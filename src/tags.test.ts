import { afterEach, describe, expect, it, vi } from "vitest";
import { defineFeature, parseFeature } from "./feature.js";
import { createLibraryRegistry } from "./registry.js";
import { resolveTags } from "./tags.js";

afterEach(() => vi.restoreAllMocks());

describe("resolveTags", () => {
  it("reads @name:value and @name=value as the same tag", () => {
    expect(resolveTags({ "timeout:5000": true }).scenario.timeout).toBe(5000);
    expect(resolveTags({ timeout: "5000" }).scenario.timeout).toBe(5000);
  });

  it("treats @skip, @pending and @fixme as ways to skip", () => {
    expect(resolveTags({ skip: true }).scenario.skip).toBe("skip");
    expect(resolveTags({ pending: true }).scenario.skip).toBe("skip");
    expect(resolveTags({ fixme: true }).scenario.skip).toBe("fixme");
    expect(resolveTags({ fixme: true, skip: true }).scenario.skip).toBe("skip");
    expect(resolveTags({}).scenario.skip).toBeUndefined();
  });

  it("lets a scenario's tag override the feature's", () => {
    const { scenario, feature, own } = resolveTags(
      { "timeout:1000": true, "retries:1": true, "mode:serial": true },
      { "timeout:2000": true },
    );
    expect(scenario).toMatchObject({ timeout: 2000, retries: 1, mode: "serial" });
    expect(feature).toEqual({ timeout: 1000, retries: 1, mode: "serial" });
    expect(own).toEqual({ timeout: 2000 });
  });

  describe("@mode on a scenario", () => {
    afterEach(() => vi.restoreAllMocks());

    it("is ignored with a warning: a mode belongs to a whole feature", () => {
      const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
      const { scenario, own } = resolveTags({ "mode:serial": true }, { "mode:parallel": true });
      expect(scenario.mode).toBe("serial");
      expect(own.mode).toBeUndefined();
      expect(warn).toHaveBeenCalledOnce();
    });
  });

  it("lets @skip=false on a scenario turn off the feature's @skip", () => {
    expect(resolveTags({ skip: true }, { skip: "false" }).scenario.skip).toBeUndefined();
  });

  it("detects @only, @fail and @slow on the feature or the scenario", () => {
    expect(resolveTags({ only: true }).scenario.only).toBe(true);
    expect(resolveTags({}, { fail: true }).scenario.fail).toBe(true);
    expect(resolveTags({ slow: true }).scenario.slow).toBe(true);
    expect(resolveTags({}).scenario).toMatchObject({ only: false, fail: false, slow: false });
  });

  describe("playwright tags", () => {
    it("passes every tag, the feature's first, without duplicates", () => {
      const { scenario } = resolveTags(
        { smoke: true, "timeout:5000": true },
        { smoke: true, area: "cart", libraries: "auth, catalog" },
      );
      expect(scenario.playwrightTags).toEqual([
        "@smoke",
        "@timeout:5000",
        "@area:cart",
        "@libraries",
      ]);
    });
  });

  it("does not pass a tag switched off with =false to Playwright", () => {
    expect(resolveTags({ skip: true }, { skip: "false" }).scenario.playwrightTags).toEqual([]);
  });

  it("accepts only whole numbers for @timeout and @retries", () => {
    vi.spyOn(console, "warn").mockImplementation(() => {});
    const { scenario } = resolveTags({ "timeout:1e3": true, "retries:0x2": true });
    expect(scenario.timeout).toBeUndefined();
    expect(scenario.retries).toBeUndefined();
    expect(resolveTags({ "retries:0": true }).scenario.retries).toBe(0);
  });

  describe("invalid values", () => {
    afterEach(() => vi.restoreAllMocks());

    it("ignores them, once with a warning", () => {
      const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
      const { scenario } = resolveTags({
        "timeout:soon": true,
        "retries:-1": true,
        "mode:fast": true,
      });
      expect(scenario.timeout).toBeUndefined();
      expect(scenario.retries).toBeUndefined();
      expect(scenario.mode).toBeUndefined();
      expect(warn).toHaveBeenCalledTimes(3);
      resolveTags({ "timeout:soon": true });
      expect(warn).toHaveBeenCalledTimes(3);
    });
  });

  it("leaves unknown tags inert", () => {
    expect(resolveTags({ whatever: true }, { "else:1": true }).scenario).toEqual({
      only: false,
      fail: false,
      slow: false,
      playwrightTags: ["@whatever", "@else:1"],
    });
  });
});

describe("defineFeature with special tags", () => {
  const registry = createLibraryRegistry({});

  /** A recording stand-in for Playwright's `test` */
  function mockTest() {
    const calls: unknown[][] = [];
    const record =
      (kind: string) =>
      (...args: unknown[]) => {
        calls.push([kind, ...args]);
      };
    const declared =
      (kind: string) =>
      (title: string, ...rest: unknown[]) => {
        calls.push([kind, title, ...rest.slice(0, -1)]);
      };
    const describeFn = Object.assign(
      (...args: unknown[]) => {
        const fn = args.pop() as () => void;
        calls.push(["describe", ...args]);
        fn();
      },
      { configure: record("configure") },
    );
    const test: any = Object.assign(declared("test"), {
      describe: describeFn,
      skip: declared("skip"),
      fixme: declared("fixme"),
      only: declared("only"),
      fail: record("fail"),
      slow: record("slow"),
      setTimeout: record("setTimeout"),
      step: async (_title: string, body: () => Promise<void>) => body(),
    });
    return { test, calls };
  }

  const run = (gherkin: string) => {
    const { test, calls } = mockTest();
    defineFeature(parseFeature(gherkin), { test, registry });
    return { test, calls };
  };

  const feature = (featureTags: string, scenarioTags: string) => `
${featureTags}
Feature: F
${scenarioTags}
Scenario: S
  Given a step
`;

  it("declares @fixme scenarios with test.fixme, and @skip and @pending with test.skip", () => {
    const kinds = (tags: string) =>
      run(feature("", tags)).calls.find((c) => c[0] !== "describe")?.[0];
    expect(kinds("@fixme")).toBe("fixme");
    expect(kinds("@skip")).toBe("skip");
    expect(kinds("@pending")).toBe("skip");
    expect(kinds("@only")).toBe("only");
    expect(kinds("")).toBe("test");
  });

  it("passes every tag to Playwright as `tag`, when it takes test details", () => {
    const { calls } = run(feature("@smoke", "@area=cart"));
    const declaration = calls.find((c) => c[0] === "test");
    expect(declaration?.[2]).toEqual({ tag: ["@smoke", "@area:cart"] });
  });

  it("configures the feature's @timeout, @retries and @mode on its describe", () => {
    const { calls } = run(feature("@timeout:5000\n@retries:2\n@mode:serial", ""));
    expect(calls).toContainEqual(["configure", { timeout: 5000, retries: 2, mode: "serial" }]);
    // nothing of the feature's needs a describe of its own around the scenario
    expect(calls.filter((c) => c[0] === "describe")).toHaveLength(1);
  });

  it("gives a scenario with its own @retries or @timeout an untitled describe", () => {
    const { calls } = run(feature("", "@retries:3\n@timeout:9000"));
    expect(calls.filter((c) => c[0] === "describe")).toEqual([
      ["describe", "Feature: F"],
      ["describe"],
    ]);
    expect(calls).toContainEqual(["configure", { timeout: 9000, retries: 3 }]);
    expect(calls.findIndex((c) => c[0] === "configure")).toBeLessThan(
      calls.findIndex((c) => c[0] === "test"),
    );
  });

  it("declares @fail and @slow in the scenario's untitled describe, before the test", () => {
    const { calls } = run(feature("", "@fail\n@slow"));
    expect(calls.map((c) => c[0])).toEqual(["describe", "describe", "fail", "slow", "test"]);
  });

  it("applies a feature's @fail to each of its scenarios", () => {
    const { calls } = run(feature("@fail", ""));
    expect(calls.map((c) => c[0])).toEqual(["describe", "describe", "fail", "test"]);
  });

  it("ignores a scenario's @mode, and does not give it a describe", () => {
    vi.spyOn(console, "warn").mockImplementation(() => {});
    const { calls } = run(feature("@mode:serial", "@mode:parallel"));
    expect(calls).toContainEqual(["configure", { mode: "serial" }]);
    expect(calls.map((c) => c[0])).toEqual(["describe", "configure", "test"]);
  });

  it("leaves unknown tags inert", () => {
    const { calls } = run(feature("@whatever", "@else"));
    expect(calls.map((c) => c[0])).toEqual(["describe", "test"]);
  });
});
