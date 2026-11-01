import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Yadda from "yadda";
import { defineFeature, defineFeatureSuite, loadFeatureFile } from "./feature.js";
import { pathTagAnnotations, tagsFromPath } from "./path-tags.js";
import { createLibraryRegistry } from "./registry.js";

describe("tagsFromPath", () => {
  const root = path.resolve("features");
  const at = (...segments: string[]) => path.join(root, ...segments);

  it("tags nothing for a plain path", () => {
    expect(tagsFromPath(at("game", "game.feature"), root)).toEqual([]);
  });

  it("takes @ directories, nested ones in path order", () => {
    expect(tagsFromPath(at("@game", "@slow", "level.feature"), root)).toEqual(["game", "slow"]);
    expect(tagsFromPath(at("@game", "plain", "@ui", "level.feature"), root)).toEqual([
      "game",
      "ui",
    ]);
  });

  it("takes an @ file name, without its extensions", () => {
    expect(tagsFromPath(at("@storefront.feature"), root)).toEqual(["storefront"]);
    expect(tagsFromPath(at("@storefront.yadda.feature"), root)).toEqual(["storefront"]);
    expect(tagsFromPath(at("@admin", "@orders.md"), root)).toEqual(["admin", "orders"]);
  });

  it("keeps special tags such as @mode:serial and the case", () => {
    expect(tagsFromPath(at("@mode:serial", "@Slow", "a.feature"), root)).toEqual([
      "mode:serial",
      "Slow",
    ]);
  });

  it("lists a repeated tag once", () => {
    expect(tagsFromPath(at("@game", "@game.feature"), root)).toEqual(["game"]);
  });

  it("ignores a bare @ and the root's own name", () => {
    expect(tagsFromPath(at("@", "a.feature"), root)).toEqual([]);
    expect(
      tagsFromPath(
        path.join("/tmp", "@outer", "features", "a.feature"),
        path.join("/tmp", "@outer", "features"),
      ),
    ).toEqual([]);
  });

  it("ignores a file outside the root", () => {
    expect(tagsFromPath(path.resolve("other", "@game", "a.feature"), root)).toEqual([]);
  });

  it("turns tags into lower-case flag annotations", () => {
    expect(pathTagAnnotations(["Game", "slow"])).toEqual({ game: true, slow: true });
  });
});

describe("path tags in a loaded feature", () => {
  let dir: string;
  const write = (file: string, body: string) => {
    const full = path.join(dir, file);
    fs.mkdirSync(path.dirname(full), { recursive: true });
    fs.writeFileSync(full, body);
    return full;
  };
  const feature = (extra = "") => `${extra}Feature: F\n  Scenario: S\n    Given a step\n`;

  beforeEach(() => {
    dir = fs.mkdtempSync(path.join(os.tmpdir(), "yadda-path-tags-"));
  });
  afterEach(() => fs.rmSync(dir, { recursive: true, force: true }));

  const mockTest = () =>
    Object.assign(vi.fn<() => void>(), {
      describe: (_: string, fn: any) => fn(),
      skip: vi.fn<() => void>(),
      only: vi.fn<() => void>(),
      step: async (_title: string, body: () => Promise<void>) => body(),
    }) as any;

  it("adds path tags to the annotations and pathTags", () => {
    const file = write("@game/@slow/a.feature", feature());
    const loaded = loadFeatureFile(file, "auto", undefined, dir);
    expect(loaded.pathTags).toEqual(["game", "slow"]);
    expect(loaded.annotations).toEqual({ game: true, slow: true });
  });

  it("adds nothing without a root, or without path tags", () => {
    const tagged = write("@game/a.feature", feature());
    expect(loadFeatureFile(tagged).pathTags).toBeUndefined();
    const plain = write("b.feature", feature());
    const loaded = loadFeatureFile(plain, "auto", undefined, dir);
    expect(loaded.pathTags).toBeUndefined();
    expect(loaded.annotations).toEqual({});
  });

  it("lets annotations written in the feature win", () => {
    const file = write("@game/a.feature", feature("@game=custom\n@Libraries=auth\n"));
    const loaded = loadFeatureFile(file, "auto", undefined, dir);
    expect(loaded.annotations).toEqual({ game: "custom", libraries: "auth" });
    expect(loaded.pathTags).toEqual(["game"]);
  });

  it("finds features through defineFeatureSuite, relative to the directory given", () => {
    // The directory given has an @ in its own name, which must not count
    const outer = path.join(dir, "@outer");
    write("@outer/@storefront/a.feature", feature());
    write("@outer/plain/b.feature", feature());
    const test = mockTest();
    const registry = createLibraryRegistry({
      common: new Yadda.Library().define("a step", () => {}),
      storefront: new Yadda.Library(),
    });
    defineFeatureSuite({ test, registry, features: outer });
    const tags = test.mock.calls.map((call: any[]) => call[1].tag);
    expect(tags).toEqual([["@storefront"], undefined]);
  });

  describe("defineFeature", () => {
    const steps = (library: Yadda.Library, name: string, ran: string[]) =>
      library.define(`${name} step`, () => {
        ran.push(name);
      });

    const run = async (opts: {
      file: string;
      text: string;
      libraryInheritance?: "merge" | "override";
    }) => {
      const ran: string[] = [];
      const common = new Yadda.Library();
      const storefront = new Yadda.Library();
      const admin = new Yadda.Library();
      steps(common, "common", ran);
      steps(storefront, "storefront", ran);
      steps(admin, "admin", ran);
      const file = write(opts.file, opts.text);
      const loaded = loadFeatureFile(file, "auto", undefined, dir);
      const body: any[] = [];
      const test = mockTest();
      test.mockImplementation((_title: string, ...rest: any[]) => body.push(rest));
      defineFeature(loaded, {
        test,
        registry: createLibraryRegistry({ common, storefront, admin }),
        libraryInheritance: opts.libraryInheritance,
      });
      return { ran, test, body, loaded };
    };
    const text = (scenarioTags = "", featureTags = "") =>
      `${featureTags}Feature: F\n  ${scenarioTags}\n  Scenario: S\n    Given common step\n    And storefront step\n`;

    it("loads a library named by a directory", async () => {
      const { ran, body } = await run({ file: "@storefront/a.feature", text: text() });
      await body[0][1]({ page: {}, context: {} });
      expect(ran).toEqual(["common", "storefront"]);
    });

    it("loads a library named by an @name.feature file", async () => {
      const { ran, body } = await run({ file: "nested/@storefront.feature", text: text() });
      await body[0][1]({ page: {}, context: {} });
      expect(ran).toEqual(["common", "storefront"]);
    });

    it("loads libraries from nested directories", async () => {
      const text2 = `Feature: F\n  Scenario: S\n    Given storefront step\n    And admin step\n`;
      const { ran, body } = await run({ file: "@storefront/@admin/a.feature", text: text2 });
      await body[0][1]({ page: {}, context: {} });
      expect(ran).toEqual(["storefront", "admin"]);
    });

    it("adds to the feature's own @Libraries rather than replacing them", () => {
      const registryHas = vi.fn<(name: string) => boolean>();
      registryHas.mockImplementation((name) => name === "storefront");
      const resolved: any[] = [];
      const registry: any = {
        has: registryHas,
        get: () => undefined,
        getAvailableKeys: () => ["storefront"],
        resolve: (featureAnnotations: any) => {
          resolved.push(featureAnnotations);
          return [];
        },
      };
      const file = write("@storefront/a.feature", feature("@Libraries=admin\n"));
      const loaded = loadFeatureFile(file, "auto", undefined, dir);
      defineFeature(loaded, { test: mockTest(), registry });
      expect(resolved[0]).toEqual({ storefront: true, libraries: "admin, storefront" });
    });

    it("ignores path tags that are not libraries", async () => {
      const text2 = `Feature: F\n  Scenario: S\n    Given common step\n`;
      const { ran, body, loaded } = await run({ file: "@slow/a.feature", text: text2 });
      await body[0][1]({ page: {}, context: {} });
      expect(ran).toEqual(["common"]);
      expect(loaded.annotations).toEqual({ slow: true });
    });

    it("lets a scenario's @Libraries replace path libraries under override", async () => {
      const text2 = `Feature: F\n  @Libraries=admin\n  Scenario: S\n    Given admin step\n`;
      const { ran, body } = await run({
        file: "@storefront/a.feature",
        text: text2,
        libraryInheritance: "override",
      });
      await body[0][1]({ page: {}, context: {} });
      expect(ran).toEqual(["admin"]);
    });

    it("merges a scenario's @Libraries with path libraries by default", async () => {
      const text2 = `Feature: F\n  @Libraries=admin\n  Scenario: S\n    Given admin step\n    And storefront step\n`;
      const { ran, body } = await run({ file: "@storefront/a.feature", text: text2 });
      await body[0][1]({ page: {}, context: {} });
      expect(ran).toEqual(["admin", "storefront"]);
    });

    it("passes path tags to Playwright as tags", async () => {
      const { test } = await run({ file: "@storefront/@slow/a.feature", text: text() });
      expect(test).toHaveBeenCalledWith(
        "Scenario: S",
        expect.objectContaining({ tag: ["@storefront", "@slow"] }),
        expect.any(Function),
      );
    });

    it("sends no tag list for a feature without path tags", async () => {
      const { test } = await run({ file: "a.feature", text: text() });
      const details = test.mock.calls[0][1];
      expect(details).not.toHaveProperty("tag");
    });

    it("treats a @skip directory like an explicit @skip", async () => {
      const { test } = await run({ file: "@skip/a.feature", text: text() });
      expect(test.skip).toHaveBeenCalledWith(
        "Scenario: S",
        expect.anything(),
        expect.any(Function),
      );
    });
  });
});
