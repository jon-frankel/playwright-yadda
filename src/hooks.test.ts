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

  Scenario: Late
    Given a late step
    And a step
`;

function setup(hooks: ReturnType<typeof createHooks>, extra: Record<string, unknown> = {}) {
  const log: string[] = [];
  const lib = new Yadda.Library();
  lib.define("a step", () => log.push("step"));
  let release!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  lib.define("a late step", () => gate);
  lib.define("boom", () => {
    throw new Error("boom");
  });
  const tests: Record<string, any> = {};
  const befores: any[] = [];
  const afters: any[] = [];
  const afterEaches: any[] = [];
  const test: any = Object.assign(
    (title: string, ...rest: any[]) => {
      tests[title] = rest.at(-1);
    },
    {
      describe: (a: any, b: any) => (typeof a === "function" ? a() : b()),
      beforeAll: (fn: any) => befores.push(fn),
      afterAll: (fn: any) => afters.push(fn),
      afterEach: (fn: any) => afterEaches.push(fn),
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
  return { log, tests, befores, afters, afterEaches, release };
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

  it("a failing BeforeStep skips the step and reaches AfterStep as its error", async () => {
    const hooks = createHooks();
    const { log, tests } = setup(hooks);
    hooks.BeforeStep(() => {
      throw new Error("before step failed");
    });
    hooks.AfterStep(({ error }) => void log.push(`after: ${(error as Error).message}`));
    await expect(tests["Scenario: Slow mobile"]({ page: {}, context: {} })).rejects.toThrow(
      /before step failed/,
    );
    expect(log).toHaveLength(1);
    expect(log[0]).toMatch(/^after: .*before step failed$/);
  });

  it("reports the step's error, not a failing AfterStep/AfterScenario hook's", async () => {
    const hooks = createHooks();
    const { tests } = setup(hooks);
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    hooks.AfterStep(() => {
      throw new Error("after step broke");
    });
    hooks.AfterScenario(() => {
      throw new Error("after scenario broke");
    });
    await expect(tests["Scenario: Plain"]({ page: {}, context: {} })).rejects.toThrow(/boom/);
    expect(warn).toHaveBeenCalledTimes(2);
    warn.mockRestore();
  });

  it("warns about After hook errors after the first, and does not blame a hook for a rethrown step error", async () => {
    const hooks = createHooks();
    const { tests } = setup(hooks);
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    hooks.AfterScenario(() => {
      throw new Error("first");
    });
    hooks.AfterScenario(() => {
      throw new Error("second");
    });
    await expect(tests["Scenario: Slow mobile"]({ page: {}, context: {} })).rejects.toThrow(
      /second/,
    );
    // After hooks run in reverse, so "second" ran first and is the one reported
    expect(warn).toHaveBeenCalledTimes(1);
    expect(String(warn.mock.calls[0]?.[0])).toContain("first");

    const rethrow = createHooks();
    const other = setup(rethrow);
    rethrow.AfterStep(({ error }) => {
      if (error) throw error;
    });
    await expect(other.tests["Scenario: Plain"]({ page: {}, context: {} })).rejects.toThrow(
      /^boom$/,
    );
    // no new warning: the rethrown error is the step's own, and is the one reported
    expect(warn).toHaveBeenCalledTimes(1);
    warn.mockRestore();
  });

  it("names default hooks by their position within their kind", () => {
    const hooks = createHooks();
    hooks.BeforeScenario(() => {});
    hooks.AfterScenario(() => {
      throw new Error("x");
    });
    expect(hooks.select("AfterScenario", [])[0]?.name).toBe("AfterScenario #1");
  });

  describe("when the test times out", () => {
    const info = { testId: "t1", annotations: [], error: new Error("Test timeout") };
    const sut = () => {
      const hooks = createHooks();
      const made = setup(hooks);
      hooks.BeforeStep(({ step }) => void made.log.push(`beforeStep ${step}`));
      hooks.AfterStep(
        ({ step, error }) => void made.log.push(`afterStep ${step} ${String(error)}`),
      );
      hooks.AfterScenario(() => void made.log.push("afterScenario"));
      return made;
    };

    it("runs AfterStep and AfterScenario once, from afterEach, even if the body resumes", async () => {
      const { log, tests, afterEaches, release } = sut();
      const body = tests["Scenario: Late"]({ page: {}, context: {} }, info);
      const settled = body.catch((error: Error) => error);
      await new Promise((resolve) => setTimeout(resolve, 10));
      await afterEaches[0]({}, info); // Playwright's timeout: the body is abandoned, not stopped
      release(); // ... and later it resumes
      await settled;
      expect(log).toEqual([
        "beforeStep Given a late step",
        "afterStep Given a late step Error: Test timeout",
        "afterScenario",
      ]);
    });

    it("leaves the hooks to a body that reached them first", async () => {
      const { log, tests, afterEaches, release } = sut();
      release();
      await tests["Scenario: Late"]({ page: {}, context: {} }, info);
      await afterEaches[0]({}, info);
      expect(log.filter((entry) => entry === "afterScenario")).toHaveLength(1);
    });

    it("a resumed body stays quiet: no error, no onAfterScenario, no later steps", async () => {
      const hooks = createHooks();
      const log: string[] = [];
      const made = setup(hooks, {
        onAfterScenario: () => void log.push("legacy after"),
      });
      hooks.AfterScenario(() => void log.push("afterScenario"));
      const body = made.tests["Scenario: Late"]({ page: {}, context: {} }, info);
      await new Promise((resolve) => setTimeout(resolve, 10));
      await made.afterEaches[0]({}, info);
      made.release();
      await expect(body).resolves.toBeUndefined();
      expect(log).toEqual(["afterScenario"]);
      expect(made.log).not.toContain("step");
    });

    it("does not start the next Before hook once the test is over", async () => {
      const hooks = createHooks();
      const log: string[] = [];
      let release!: () => void;
      const gate = new Promise<void>((resolve) => (release = resolve));
      hooks.BeforeScenario(async () => {
        log.push("before 1");
        await gate;
      });
      hooks.BeforeScenario(() => void log.push("before 2"));
      hooks.AfterScenario(() => void log.push("after"));
      const made = setup(hooks);
      const body = made.tests["Scenario: Late"]({ page: {}, context: {} }, info);
      await new Promise((resolve) => setTimeout(resolve, 10));
      await made.afterEaches[0]({}, info);
      release();
      await body;
      expect(log).toEqual(["before 1", "after"]);
    });

    it("only warns when a hook fails there", async () => {
      const hooks = createHooks();
      const { tests, afterEaches } = setup(hooks);
      const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
      hooks.AfterScenario(() => {
        throw new Error("cleanup broke");
      });
      const settled = tests["Scenario: Late"]({ page: {}, context: {} }, info).catch(() => {});
      await new Promise((resolve) => setTimeout(resolve, 10));
      await expect(afterEaches[0]({}, info)).resolves.toBeUndefined();
      expect(warn).toHaveBeenCalled();
      warn.mockRestore();
      void settled;
    });
  });

  it("copes with an error whose message is read-only", async () => {
    const hooks = createHooks();
    const { log, tests } = setup(hooks);
    hooks.AfterScenario(() => void log.push("ran"));
    hooks.AfterScenario(() => {
      throw new DOMException("aborted", "AbortError");
    });
    await expect(tests["Scenario: Slow mobile"]({ page: {}, context: {} })).rejects.toThrow(
      /aborted/,
    );
    expect(log).toContain("ran");
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
