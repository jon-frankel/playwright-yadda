import { describe, expect, it, vi } from "vitest";
import Yadda from "yadda";
import { defineFeature, parseFeature } from "./feature.js";
import { createHooks } from "./hooks.js";
import { createLibraryRegistry } from "./registry.js";

const text = `
Feature: Hooked
  @mobile
  Scenario: Mobile
    Given a step
    Then a step

  @mobile
  @slow
  Scenario: Slow mobile
    Given a step

  Scenario: Plain
    Given boom
`;

function setup(hooks: ReturnType<typeof createHooks>, extra: Record<string, unknown> = {}) {
  const log: string[] = [];
  const lib = new Yadda.Library();
  lib.define("a step", () => log.push("step"));
  lib.define("boom", () => {
    throw new Error("boom");
  });
  const tests: Record<string, any> = {};
  const befores: any[] = [];
  const afters: any[] = [];
  const test: any = Object.assign(
    (title: string, ...rest: any[]) => {
      tests[title] = rest.at(-1);
    },
    {
      describe: (a: any, b: any) => (typeof a === "function" ? a() : b()),
      beforeAll: (fn: any) => befores.push(fn),
      afterAll: (fn: any) => afters.push(fn),
      step: async (_t: string, body: () => Promise<void>) => body(),
      skip: vi.fn<() => void>(),
      only: vi.fn<() => void>(),
      fail: vi.fn<() => void>(),
      slow: vi.fn<() => void>(),
    },
  );
  defineFeature(parseFeature(text), {
    test,
    registry: createLibraryRegistry({ common: lib }),
    hooks,
    ...extra,
  });
  return { log, tests, befores, afters };
}

describe("hooks", () => {
  it("runs Before in registration order and After in reverse", async () => {
    const hooks = createHooks();
    const { log, tests } = setup(hooks);
    for (const n of [1, 2]) {
      hooks.BeforeScenario(() => void log.push(`before${n}`));
      hooks.AfterScenario(() => void log.push(`after${n}`));
    }
    // registered after defineFeature: hooks are looked up when the scenario runs
    await tests["Scenario: Slow mobile"]({ page: {}, context: {} });
    expect(log).toEqual(["before1", "before2", "step", "after2", "after1"]);
  });

  it("filters by tag expression", async () => {
    const hooks = createHooks();
    const { log, tests } = setup(hooks);
    hooks.BeforeScenario({ tags: "@mobile and not @slow" }, () => void log.push("fast-mobile"));
    hooks.BeforeScenario({ tags: "@slow" }, () => void log.push("slow"));
    await tests["Scenario: Mobile"]({ page: {}, context: {} });
    await tests["Scenario: Slow mobile"]({ page: {}, context: {} });
    expect(log).toEqual(["fast-mobile", "step", "step", "slow", "step"]);
  });

  it("honours order", async () => {
    const hooks = createHooks();
    const { log, tests } = setup(hooks);
    hooks.BeforeScenario({ order: 5 }, () => void log.push("late"));
    hooks.BeforeScenario({ order: -1 }, () => void log.push("early"));
    await tests["Scenario: Slow mobile"]({ page: {}, context: {} });
    expect(log.slice(0, 2)).toEqual(["early", "late"]);
  });

  it("runs step hooks around each step", async () => {
    const hooks = createHooks();
    const { log, tests } = setup(hooks);
    hooks.BeforeStep(({ step }) => void log.push(`before ${step}`));
    hooks.AfterStep(
      ({ step, error }) =>
        void log.push(`after ${step} ${error ? (error as Error).message : "ok"}`),
    );
    await tests["Scenario: Slow mobile"]({ page: {}, context: {} });
    expect(log).toEqual(["before Given a step", "step", "after Given a step ok"]);
  });

  it("still runs AfterStep and AfterScenario when a step fails", async () => {
    const hooks = createHooks();
    const { log, tests } = setup(hooks);
    hooks.AfterStep(({ error }) => void log.push(`afterStep ${(error as Error).message}`));
    hooks.AfterScenario(() => void log.push("afterScenario"));
    await expect(tests["Scenario: Plain"]({ page: {}, context: {} })).rejects.toThrow(/boom/);
    expect(log).toEqual(["afterStep boom", "afterScenario"]);
  });

  it("runs every After hook when one throws, and reports the first error", async () => {
    const hooks = createHooks();
    const { log, tests } = setup(hooks);
    hooks.AfterScenario(() => void log.push("a"));
    hooks.AfterScenario({ name: "bad" }, () => {
      throw new Error("after failed");
    });
    await expect(tests["Scenario: Slow mobile"]({ page: {}, context: {} })).rejects.toThrow(
      /bad failed: after failed/,
    );
    expect(log).toContain("a");
  });

  it("a failing BeforeScenario skips the steps but still runs AfterScenario", async () => {
    const hooks = createHooks();
    const { log, tests } = setup(hooks);
    hooks.BeforeScenario(() => {
      throw new Error("setup");
    });
    hooks.AfterScenario(() => void log.push("cleanup"));
    await expect(tests["Scenario: Slow mobile"]({ page: {}, context: {} })).rejects.toThrow(
      /setup/,
    );
    expect(log).toEqual(["cleanup"]);
  });

  it("registers worker hooks on beforeAll/afterAll, filtered by the feature's tags", async () => {
    const hooks = createHooks();
    const log: string[] = [];
    hooks.BeforeWorker(() => void log.push("bw"));
    hooks.AfterWorker(() => void log.push("aw1"));
    hooks.AfterWorker(() => void log.push("aw2"));
    hooks.BeforeWorker({ tags: "@nope" }, () => void log.push("never"));
    const { befores, afters } = setup(hooks);
    expect(befores).toHaveLength(1);
    await befores[0]({}, {});
    await afters[0]({}, {});
    expect(log).toEqual(["bw", "aw2", "aw1"]);
  });

  it("still runs the legacy onBeforeScenario/onAfterScenario", async () => {
    const hooks = createHooks();
    const log: string[] = [];
    const { tests } = setup(hooks, {
      onBeforeScenario: () => void log.push("legacy before"),
      onAfterScenario: () => void log.push("legacy after"),
    });
    await tests["Scenario: Slow mobile"]({ page: {}, context: {} });
    expect(log).toEqual(["legacy before", "legacy after"]);
  });

  it("throws at registration on a bad tag expression", () => {
    expect(() => createHooks().BeforeScenario({ tags: "@a and" }, () => {})).toThrow(
      /tag expression/,
    );
  });
});
