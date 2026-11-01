import type { TestCase, TestError, TestResult, TestStep } from "@playwright/test/reporter";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import YaddaReporter from "./reporter.js";

// eslint-disable-next-line no-control-regex
const stripAnsi = (s: string) => s.replace(/\x1b\[[0-9;]*m/g, "");

let out: string[];
let err: string[];

beforeEach(() => {
  out = [];
  err = [];
  vi.spyOn(process.stdout, "write").mockImplementation((chunk: any) => {
    out.push(String(chunk));
    return true;
  });
  vi.spyOn(process.stderr, "write").mockImplementation((chunk: any) => {
    err.push(String(chunk));
    return true;
  });
});

afterEach(() => {
  vi.restoreAllMocks();
});

const stdout = () => stripAnsi(out.join(""));
const allOutput = () => stripAnsi(out.join("") + err.join(""));

function makeTest(scenario: string, retries = 0): TestCase {
  return {
    title: `Scenario: ${scenario}`,
    titlePath: () => [
      "",
      "chromium",
      "features.spec.ts",
      "Feature: Alpha",
      `Scenario: ${scenario}`,
    ],
    annotations: [{ type: "feature-file", description: `${process.cwd()}/features/a.feature` }],
    retries,
  } as unknown as TestCase;
}

function makeStep(title: string, error?: TestError, duration = 4): TestStep {
  return { title, category: "test.step", error, duration, steps: [] } as unknown as TestStep;
}

function makeResult(
  status: TestResult["status"],
  retry: number,
  steps: TestStep[] = [],
  errors: TestError[] = [],
): TestResult {
  return {
    status,
    retry,
    duration: 5,
    steps,
    errors,
    error: errors[0],
  } as unknown as TestResult;
}

const boom: TestError = {
  message: "Error: boom",
  stack: "Error: boom\n    at step (steps.ts:1:1)",
};

describe("YaddaReporter", () => {
  it("prints errors raised outside tests (onError)", () => {
    const reporter = new YaddaReporter();
    expect(typeof (reporter as any).onError).toBe("function");

    (reporter as any).onError({
      message: "Error: LOAD-FAIL",
      stack: "Error: LOAD-FAIL\n    at broken.spec.ts:1:7",
    } satisfies TestError);

    expect(allOutput()).toContain("LOAD-FAIL");
  });

  it("prints test stdout and stderr (onStdOut / onStdErr)", () => {
    const reporter = new YaddaReporter();
    // printsToStdio() is true, so Playwright leaves printing test output to the reporter
    expect(reporter.printsToStdio()).toBe(true);
    expect(typeof (reporter as any).onStdOut).toBe("function");
    expect(typeof (reporter as any).onStdErr).toBe("function");

    const test = makeTest("Talks");
    const result = makeResult("passed", 0);
    (reporter as any).onStdOut("HELLO-STDOUT\n", test, result);
    (reporter as any).onStdErr("HELLO-STDERR\n", test, result);

    expect(allOutput()).toContain("HELLO-STDOUT");
    expect(allOutput()).toContain("HELLO-STDERR");
  });

  it("counts each test once in the summary, not each retry attempt", () => {
    const reporter = new YaddaReporter();
    const passes = makeTest("Passes", 1);
    const fails = makeTest("Fails", 1);
    const undefinedStep = makeTest("Undefined", 1);
    const flaky = makeTest("Flaky", 1);

    reporter.onTestEnd(passes, makeResult("passed", 0, [makeStep("Given a good step")]));
    for (const test of [fails, undefinedStep]) {
      for (const retry of [0, 1]) {
        reporter.onTestEnd(
          test,
          makeResult("failed", retry, [makeStep("When a bad step", boom)], [boom]),
        );
      }
    }
    reporter.onTestEnd(
      flaky,
      makeResult("failed", 0, [makeStep("Given a flaky step", boom)], [boom]),
    );
    reporter.onTestEnd(flaky, makeResult("passed", 1, [makeStep("Given a flaky step")]));

    out = [];
    reporter.onEnd({ status: "failed" } as any);

    expect(stdout()).toContain("2 failed, 1 flaky, 1 passed");
  });

  it("treats a scenario expected to fail as ok when it fails, and as failed when it passes", () => {
    const reporter = new YaddaReporter();
    const expectedFail = Object.assign(makeTest("Fails as expected"), { expectedStatus: "failed" });
    const unexpectedPass = Object.assign(makeTest("Passes unexpectedly"), {
      expectedStatus: "failed",
    });

    reporter.onTestEnd(
      expectedFail,
      makeResult("failed", 0, [makeStep("Given a bad step", boom)], [boom]),
    );
    reporter.onTestEnd(unexpectedPass, makeResult("passed", 0, [makeStep("Given a good step")]));
    const printed = stdout();
    expect(printed).toMatch(/✓.*Fails as expected/);
    expect(printed).not.toContain("Error: boom\n    at step");
    expect(printed).toMatch(/✗.*Passes unexpectedly/);
    expect(printed).toContain("Expected to fail, but passed.");

    out = [];
    reporter.onEnd({ status: "failed" } as any);
    expect(stdout()).toContain("1 failed, 1 passed");
  });

  it("marks the step that was running when the test timed out with ✗", () => {
    const reporter = new YaddaReporter();
    const timeout: TestError = { message: "Test timeout of 1000ms exceeded." };

    // Playwright sets no error on the step that was still running at the timeout
    reporter.onTestEnd(
      makeTest("Hangs"),
      makeResult(
        "timedOut",
        0,
        [makeStep("Given a good step"), makeStep("When a step never resolves", undefined, -1)],
        [timeout],
      ),
    );

    const lines = stdout().split("\n");
    expect(lines).toContain("    ✓ Given a good step");
    expect(lines).toContain("    ✗ When a step never resolves");
  });

  it("leaves every step ✓ when the test times out after the steps finished", () => {
    const reporter = new YaddaReporter();
    const timeout: TestError = { message: "Test timeout of 1000ms exceeded." };

    // e.g. a hook after the steps hangs: all steps ended, so none reports a negative duration
    reporter.onTestEnd(
      makeTest("Hangs after"),
      makeResult("timedOut", 0, [makeStep("Given a good step")], [timeout]),
    );

    const lines = stdout().split("\n");
    expect(lines).toContain("    ✓ Given a good step");
    expect(lines.some((l) => l.includes("✗ Given"))).toBe(false);
    expect(stdout()).toContain("Test timeout of 1000ms exceeded.");
  });

  it("omits the code snippet when it points inside playwright-yadda, unless fullStackTrace", () => {
    const internal: TestError = {
      message: "Error: undefined step",
      stack: "Error: undefined step",
      location: { file: `${process.cwd()}/src/runner.ts`, line: 29, column: 13 },
      snippet: "SNIPPET-INSIDE-RUNNER",
    };
    const run = (reporter: YaddaReporter) => {
      out = [];
      reporter.onTestEnd(
        makeTest("Undefined"),
        makeResult("failed", 0, [makeStep("Given nothing", internal)], [internal]),
      );
      return stdout();
    };

    expect(run(new YaddaReporter())).not.toContain("SNIPPET-INSIDE-RUNNER");
    expect(run(new YaddaReporter({ fullStackTrace: true }))).toContain("SNIPPET-INSIDE-RUNNER");
  });

  it("keeps the code snippet when it points at user code", () => {
    const user: TestError = {
      message: "Error: boom",
      stack: "Error: boom\n    at step (/proj/steps.ts:1:1)",
      location: { file: "/proj/steps.ts", line: 1, column: 1 },
      snippet: "SNIPPET-IN-USER-CODE",
    };
    new YaddaReporter().onTestEnd(
      makeTest("Boom"),
      makeResult("failed", 0, [makeStep("When it breaks", user)], [user]),
    );
    expect(stdout()).toContain("SNIPPET-IN-USER-CODE");
  });
});
