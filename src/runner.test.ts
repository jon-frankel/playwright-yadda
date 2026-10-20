import { describe, expect, it, vi } from "vitest";
import Yadda from "yadda";
import { createScenarioYadda, runScenarioSteps, runStep } from "./runner.js";

describe("runner", () => {
  it("should create an isolated Yadda instance", () => {
    const lib = new Yadda.Library();
    const yadda = createScenarioYadda([lib]);
    expect(yadda).toBeDefined();
    expect(typeof yadda.run).toBe("function");
  });

  it("should run synchronous steps cleanly", async () => {
    const executed: string[] = [];
    const lib = new Yadda.Library();
    lib.define("step one", () => {
      executed.push("one");
    });
    lib.define("step two", () => {
      executed.push("two");
    });

    const yadda = createScenarioYadda([lib]);
    const ctx = {};

    await runStep(yadda, "step one", ctx);
    await runStep(yadda, "step two", ctx);

    expect(executed).toEqual(["one", "two"]);
  });

  it("should run asynchronous steps and await completion", async () => {
    const events: string[] = [];
    const lib = new Yadda.Library();
    lib.define("async step", async () => {
      events.push("start");
      await new Promise((resolve) => setTimeout(resolve, 20));
      events.push("finish");
    });

    const yadda = createScenarioYadda([lib]);
    await runStep(yadda, "async step", {});

    expect(events).toEqual(["start", "finish"]);
  });

  it("should reject when a step throws an error", async () => {
    const lib = new Yadda.Library();
    lib.define("failing step", () => {
      throw new Error("Something broke");
    });

    const yadda = createScenarioYadda([lib]);
    await expect(runStep(yadda, "failing step", {})).rejects.toThrow("Something broke");
  });

  it("should wrap each step in testStep when provided", async () => {
    const stepsRun: string[] = [];
    const stepWrappersCalled: string[] = [];

    const lib = new Yadda.Library();
    lib.define("first step", () => {
      stepsRun.push("first");
    });
    lib.define("second step", () => {
      stepsRun.push("second");
    });

    const yadda = createScenarioYadda([lib]);
    const mockTestStep = vi.fn<(title: string, body: () => Promise<void>) => Promise<void>>(
      async (title: string, body: () => Promise<void>) => {
        stepWrappersCalled.push(title);
        await body();
      },
    );

    await runScenarioSteps(yadda, ["first step", "second step"], {}, mockTestStep as any);

    expect(stepsRun).toEqual(["first", "second"]);
    expect(stepWrappersCalled).toEqual(["first step", "second step"]);
    expect(mockTestStep).toHaveBeenCalledTimes(2);
  });
});
