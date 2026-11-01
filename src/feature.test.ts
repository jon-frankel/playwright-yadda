import { describe, expect, it, vi } from "vitest";
import Yadda from "yadda";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import {
  defineFeature,
  defineFeatureSuite,
  loadFeatureFile,
  parseFeature,
  pathTagsOf,
} from "./feature.js";
import { resolveLibraryNames } from "./annotations.js";
import { createLibraryRegistry } from "./registry.js";
import { REPORTER_FLAG, resetWarningsForTests, WARNING_ANNOTATION } from "./warnings.js";

describe("feature", () => {
  const gherkinText = `
@Libraries=auth, catalog
Feature: Storefront Shopping
  Scenario: Add item to cart
    Given the store is open
    When they add "Keyboard" to cart
    Then the cart has 1 item
`;

  const markdownText = `
@Libraries=auth
# Feature: Markdown Feature
  ## Scenario: Markdown Scenario
  - Given step one
  - Then step two
`;

  it("should parse standard Gherkin features", () => {
    const feature = parseFeature(gherkinText, "gherkin");
    expect(feature.title).toBe("Storefront Shopping");
    expect(feature.annotations).toEqual({ libraries: "auth, catalog" });
    expect(feature.scenarios).toHaveLength(1);
    expect(feature.scenarios[0]?.title).toBe("Add item to cart");
    expect(feature.scenarios[0]?.steps).toHaveLength(3);
  });

  it("should parse Markdown features", () => {
    const feature = parseFeature(markdownText, "markdown");
    expect(feature.title).toBe("Markdown Feature");
    expect(feature.annotations).toEqual({ libraries: "auth" });
    expect(feature.scenarios[0]?.title).toBe("Markdown Scenario");
    expect(feature.scenarios[0]?.steps).toHaveLength(2);
  });

  it("should auto-detect markdown vs gherkin", () => {
    const md = parseFeature(markdownText, "auto");
    expect(md.title).toBe("Markdown Feature");

    const gh = parseFeature(gherkinText, "auto");
    expect(gh.title).toBe("Storefront Shopping");
  });

  it("should define Playwright tests with dynamic library resolution", async () => {
    const commonLib = new Yadda.Library();
    const authLib = new Yadda.Library();
    const catalogLib = new Yadda.Library();

    const executedSteps: string[] = [];
    authLib.define("the store is open", () => {
      executedSteps.push("store open");
    });
    catalogLib.define(/they add "(.*)" to cart/, (item: string) => {
      executedSteps.push(`added ${item}`);
    });
    commonLib.define("the cart has 1 item", () => {
      executedSteps.push("cart 1");
    });

    const registry = createLibraryRegistry({
      common: commonLib,
      auth: authLib,
      catalog: catalogLib,
    });

    const feature = parseFeature(gherkinText);

    // Mock Playwright test API
    let describeTitle = "";
    let scenarioTitle = "";
    let scenarioFn: any = null;

    const mockTest: any = Object.assign(
      (title: string, ...rest: any[]) => {
        scenarioTitle = title;
        // after the details object, which carries the scenario's tags, when there is one
        scenarioFn = rest.at(-1);
      },
      {
        describe: (title: string, fn: any) => {
          describeTitle = title;
          fn();
        },
        skip: vi.fn<() => void>(),
        only: vi.fn<() => void>(),
        step: vi.fn<(_stepTitle: string, stepBody: () => Promise<void>) => Promise<void>>(
          async (_stepTitle: string, stepBody: () => Promise<void>) => {
            await stepBody();
          },
        ),
      },
    );

    defineFeature(feature, {
      test: mockTest,
      registry,
    });

    expect(describeTitle).toBe("Feature: Storefront Shopping");
    expect(scenarioTitle).toBe("Scenario: Add item to cart");

    // Execute scenario function
    await scenarioFn({ page: {}, context: {} });

    expect(executedSteps).toEqual(["store open", "added Keyboard", "cart 1"]);
    expect(mockTest.step).toHaveBeenCalledTimes(3);
  });

  it("should use test.skip when scenario is marked pending", () => {
    const pendingGherkin = `
Feature: Pending Feature
  @Pending
  Scenario: Pending Scenario
    Given step one
`;
    const feature = parseFeature(pendingGherkin);
    const mockTest: any = Object.assign(vi.fn<() => void>(), {
      describe: (_: string, fn: any) => fn(),
      skip: vi.fn<() => void>(),
      only: vi.fn<() => void>(),
      step: vi.fn<() => void>(),
    });

    const registry = createLibraryRegistry({ common: new Yadda.Library() });

    defineFeature(feature, {
      test: mockTest,
      registry,
    });

    expect(mockTest.skip).toHaveBeenCalledWith(
      "Scenario: Pending Scenario",
      { tag: ["@pending"] },
      expect.any(Function),
    );
  });

  it("should attach the feature file to a pending scenario, whose body never runs", () => {
    const feature = parseFeature(
      "Feature: Pending Feature\n  @Pending\n  Scenario: Pending Scenario\n    Given step one\n",
      "gherkin",
      "features/pending.feature",
    );
    const mockTest: any = Object.assign(vi.fn<() => void>(), {
      describe: (_: string, fn: any) => fn(),
      skip: vi.fn<() => void>(),
      only: vi.fn<() => void>(),
      step: vi.fn<() => void>(),
    });

    defineFeature(feature, {
      test: mockTest,
      registry: createLibraryRegistry({ common: new Yadda.Library() }),
    });

    expect(mockTest.skip).toHaveBeenCalledWith(
      "Scenario: Pending Scenario",
      {
        annotation: { type: "feature-file", description: feature.filepath },
        tag: ["@pending"],
      },
      expect.any(Function),
    );
  });

  it("should attach the feature file when a normal scenario is declared, not from its body", () => {
    const feature = parseFeature(
      "Feature: Normal Feature\n  Scenario: Normal Scenario\n    Given step one\n",
      "gherkin",
      "features/normal.feature",
    );
    const mockTest: any = Object.assign(vi.fn<() => void>(), {
      describe: (_: string, fn: any) => fn(),
      skip: vi.fn<() => void>(),
      only: vi.fn<() => void>(),
      step: vi.fn<() => void>(),
    });

    defineFeature(feature, {
      test: mockTest,
      registry: createLibraryRegistry({ common: new Yadda.Library() }),
    });

    expect(mockTest).toHaveBeenCalledWith(
      "Scenario: Normal Scenario",
      { annotation: { type: "feature-file", description: feature.filepath } },
      expect.any(Function),
    );
  });

  it("puts the warnings about a scenario's tags on it as annotations, a feature's on all of them", () => {
    vi.spyOn(console, "warn").mockImplementation(() => {});
    const feature = parseFeature(
      "@timeout:soon\nFeature: Warned\n  @mode:serial\n  Scenario: With a warning\n    Given step one\n\n  Scenario: Without\n    Given step one\n",
      "gherkin",
      "features/warned.feature",
    );
    const mockTest: any = Object.assign(vi.fn<() => void>(), {
      describe: (_: string, fn: any) => fn(),
      skip: vi.fn<() => void>(),
      only: vi.fn<() => void>(),
      step: vi.fn<() => void>(),
    });

    defineFeature(feature, {
      test: mockTest,
      registry: createLibraryRegistry({ common: new Yadda.Library() }),
    });

    const warningsOf = (title: string) => {
      const details = mockTest.mock.calls.find((call: unknown[]) => call[0] === title)?.[1];
      return [details.annotation].flat().filter((a: any) => a.type === WARNING_ANNOTATION);
    };
    expect(warningsOf("Scenario: With a warning").map((a: any) => a.description)).toEqual([
      expect.stringContaining("ignoring @timeout"),
      expect.stringContaining("ignoring @mode on a scenario"),
    ]);
    expect(warningsOf("Scenario: Without").map((a: any) => a.description)).toEqual([
      expect.stringContaining("ignoring @timeout"),
    ]);
  });

  it("still warns about a feature's tags when it has no scenario to carry the warning, with the reporter in use", () => {
    resetWarningsForTests();
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    (globalThis as Record<symbol, unknown>)[REPORTER_FLAG] = true;
    try {
      const mockTest: any = Object.assign(vi.fn<() => void>(), {
        describe: (_: string, fn: any) => fn(),
        skip: vi.fn<() => void>(),
        only: vi.fn<() => void>(),
        step: vi.fn<() => void>(),
      });
      const registry = createLibraryRegistry({ common: new Yadda.Library() });
      // Yadda refuses to parse a feature without scenarios, so only one built by hand has none
      const empty = { title: "Empty", annotations: { "timeout:soon": true }, scenarios: [] };
      defineFeature(empty as any, { test: mockTest, registry });
      expect(warn).toHaveBeenCalledOnce();
      expect(warn.mock.calls[0]?.[0]).toContain("ignoring @timeout, which needs a whole number");

      // with a scenario to carry it, the reporter has it and the loader stays quiet
      warn.mockClear();
      resetWarningsForTests();
      defineFeature(
        parseFeature("@timeout:soon\nFeature: Full\n  Scenario: S\n    Given step one\n"),
        { test: mockTest, registry },
      );
      expect(warn).not.toHaveBeenCalled();
    } finally {
      delete (globalThis as Record<symbol, unknown>)[REPORTER_FLAG];
    }
  });

  it("should run through defineFeatureSuite with pre-parsed features", async () => {
    const feature = parseFeature(gherkinText);
    const mockTest: any = Object.assign(vi.fn<() => void>(), {
      describe: vi.fn<(_: string, fn: any) => void>((_: string, fn: any) => fn()),
      skip: vi.fn<() => void>(),
      only: vi.fn<() => void>(),
      step: vi.fn<() => void>(),
    });

    const registry = createLibraryRegistry({
      common: new Yadda.Library(),
      auth: new Yadda.Library(),
      catalog: new Yadda.Library(),
    });

    defineFeatureSuite({
      test: mockTest,
      registry,
      features: [feature],
    });

    expect(mockTest.describe).toHaveBeenCalledWith(
      "Feature: Storefront Shopping",
      expect.any(Function),
    );
  });

  describe("tags from path", () => {
    const root = path.join(path.sep, "proj", "features");
    const at = (...parts: string[]) => path.join(root, ...parts);

    it("takes @ directories below the root, outermost first", () => {
      expect(pathTagsOf(at("@game", "@Slow", "plain", "x.feature"), root)).toEqual([
        "game",
        "slow",
      ]);
    });

    it("ignores @ in directories above the root and in plain names", () => {
      const nested = path.join(path.sep, "@outer", "features");
      expect(pathTagsOf(path.join(nested, "a", "x.feature"), nested)).toEqual([]);
    });

    it("takes special tags written as directory names", () => {
      expect(pathTagsOf(at("@mode:serial", "@timeout:5000", "x.feature"), root)).toEqual([
        "mode:serial",
        "timeout:5000",
      ]);
    });

    it("counts only the file name for a file outside the root", () => {
      const other = path.join(path.sep, "elsewhere");
      expect(pathTagsOf(path.join(path.sep, "a", "@x", "y.feature"), other)).toEqual([]);
      expect(pathTagsOf(path.join(path.sep, "a", "@x", "@y.feature"), other)).toEqual(["y"]);
    });

    it("keeps dots in the name of an @ file", () => {
      expect(pathTagsOf(at("@v1.2.feature"), root)).toEqual(["v1.2"]);
    });

    it("takes an @name.feature file name, without its extensions", () => {
      expect(pathTagsOf(at("@storefront.feature"), root)).toEqual(["storefront"]);
      expect(pathTagsOf(at("a", "@smoke.yadda.feature"), root)).toEqual(["smoke"]);
      expect(pathTagsOf(at("a", "@smoke.md"), undefined)).toEqual(["smoke"]);
    });

    it("counts only the file name without a root", () => {
      expect(pathTagsOf(at("@game", "x.feature"))).toEqual([]);
    });

    describe("loaded features", () => {
      const dir = fs.mkdtempSync(path.join(os.tmpdir(), "yadda-path-tags-"));
      const body = (head = "") => `${head}Feature: F\n  Scenario: S\n    Given a step\n`;
      const write = (rel: string, head?: string) => {
        const file = path.join(dir, rel);
        fs.mkdirSync(path.dirname(file), { recursive: true });
        fs.writeFileSync(file, body(head));
        return file;
      };

      it("adds path tags to the annotations", () => {
        const feature = loadFeatureFile(
          write("@game/@slow/x.feature", "@Libraries=auth\n"),
          "auto",
          undefined,
          dir,
        );
        expect(feature.pathTags).toEqual(["game", "slow"]);
        expect(feature.annotations).toEqual({ libraries: "auth", game: true, slow: true });
      });

      it("lets a tag written in the file win over the same path tag", () => {
        const feature = loadFeatureFile(
          write("@mode:serial/y.feature", "@mode:parallel\n"),
          "auto",
          undefined,
          dir,
        );
        expect(feature.annotations).toEqual({ "mode:parallel": true });
      });

      it("reads name=value path tags, for systems without ':' in file names", () => {
        const feature = loadFeatureFile(
          write("@timeout=5000/@mode=serial/v.feature", "@mode=parallel\n"),
          "auto",
          undefined,
          dir,
        );
        expect(feature.annotations).toEqual({ timeout: "5000", mode: "parallel" });
      });

      it("does not load a library the file switches off", () => {
        const feature = loadFeatureFile(
          write("@storefront/off.feature", "@storefront=false\n"),
          "auto",
          undefined,
          dir,
        );
        expect(feature.pathTags).toBeUndefined();
        expect(feature.annotations).toEqual({ storefront: "false" });
      });

      it("keeps the case of a value and ignores names with whitespace", () => {
        resetWarningsForTests();
        const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
        const feature = loadFeatureFile(
          write("@Env=Staging/@two words/c.feature"),
          "auto",
          undefined,
          dir,
        );
        expect(feature.annotations).toEqual({ env: "Staging" });
        expect(warn).toHaveBeenCalledWith(expect.stringContaining("whitespace"));
        warn.mockRestore();
      });

      it("adds nothing without a root or an @ name", () => {
        const feature = loadFeatureFile(write("@game/z.feature"));
        expect(feature.pathTags).toBeUndefined();
        expect(feature.annotations).toEqual({});
      });

      it("finds the root of a suite from its directory", () => {
        write("@storefront/s.feature");
        const resolve = vi.fn<(...args: any[]) => any[]>(() => []);
        const registry: any = {
          resolve,
          getAvailableKeys: () => ["common", "storefront"],
          has: (n: string) => ["common", "storefront"].includes(n),
          get: () => undefined,
        };
        const mockTest: any = Object.assign(vi.fn<() => void>(), {
          describe: (_: string, fn: any) => fn(),
          step: vi.fn<() => void>(),
        });
        defineFeatureSuite({ test: mockTest, registry, features: path.join(dir, "@storefront") });
        // the target directory is the root, so its own name is not a tag
        expect(mockTest).toHaveBeenCalledWith(
          "Scenario: S",
          expect.not.objectContaining({ tag: expect.anything() }),
          expect.any(Function),
        );
      });
    });

    describe("library resolution", () => {
      const run = (
        path_: string,
        extra?: { head?: string; inheritance?: "merge" | "override"; scenario?: string },
      ) => {
        const feature = parseFeature(
          `${extra?.head ?? ""}Feature: F\n  ${extra?.scenario ?? ""}Scenario: S\n    Given a step\n`,
          "gherkin",
        );
        feature.pathTags = pathTagsOf(path_, root);
        const registry = createLibraryRegistry({
          common: new Yadda.Library(),
          storefront: new Yadda.Library(),
          admin: new Yadda.Library(),
        });
        const resolve = vi.spyOn(registry, "resolve");
        const mockTest: any = Object.assign(vi.fn<() => void>(), {
          describe: (_: string, fn: any) => fn(),
          step: vi.fn<() => void>(),
        });
        defineFeature(feature, {
          test: mockTest,
          registry,
          libraryInheritance: extra?.inheritance,
        });
        const [featureAnnotations, scenarioAnnotations, options] = resolve.mock.calls[0]!;
        return resolveLibraryNames(featureAnnotations, scenarioAnnotations, options);
      };

      it("loads a library named by a nested directory", () => {
        expect(run(at("@storefront", "deep", "x.feature"))).toEqual(["common", "storefront"]);
      });

      it("loads a library named by an @name.feature file", () => {
        expect(run(at("@storefront.feature"))).toEqual(["common", "storefront"]);
      });

      it("honours a custom annotationKey of the registry", () => {
        const feature = parseFeature("Feature: F\n  Scenario: S\n    Given a step\n", "gherkin");
        feature.pathTags = ["storefront"];
        const registry = createLibraryRegistry(
          { common: new Yadda.Library(), storefront: new Yadda.Library() },
          { annotationKey: "Steps" },
        );
        const resolve = vi.spyOn(registry, "resolve");
        const mockTest: any = Object.assign(vi.fn<() => void>(), {
          describe: (_: string, fn: any) => fn(),
          step: vi.fn<() => void>(),
        });
        defineFeature(feature, { test: mockTest, registry });
        const [featureAnnotations, scenarioAnnotations, options] = resolve.mock.calls[0]!;
        expect(
          resolveLibraryNames(featureAnnotations, scenarioAnnotations, {
            ...registry.options,
            ...options,
          }),
        ).toEqual(["common", "storefront"]);
      });

      it("ignores path tags that name no library", () => {
        expect(run(at("@smoke", "x.feature"))).toEqual(["common"]);
      });

      it("merges with explicit @Libraries", () => {
        expect(run(at("@storefront", "x.feature"), { head: "@Libraries=admin\n" })).toEqual([
          "common",
          "admin",
          "storefront",
        ]);
      });

      it("lets a scenario's @Libraries replace them with override inheritance", () => {
        expect(
          run(at("@storefront", "x.feature"), {
            inheritance: "override",
            scenario: "@Libraries=admin\n  ",
          }),
        ).toEqual(["common", "admin"]);
      });
    });
  });
});
