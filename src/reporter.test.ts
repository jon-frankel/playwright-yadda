import type { TestCase, TestError, TestResult, TestStep } from "@playwright/test/reporter";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import YaddaReporter from "./reporter.js";
import { FEATURE_TAGS_ANNOTATION, REPORTER_FLAG, WARNING_ANNOTATION } from "./warnings.js";

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
  delete (globalThis as Record<symbol, unknown>)[REPORTER_FLAG];
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
  const warned = (scenario: string, ...warnings: string[]) =>
    Object.assign(makeTest(scenario), {
      annotations: [
        { type: "feature-file", description: `${process.cwd()}/features/a.feature` },
        ...warnings.map((description) => ({ type: WARNING_ANNOTATION, description })),
      ],
    });

  it("prints the warnings of the scenarios in the run once each, before the first scenario", () => {
    const reporter = new YaddaReporter();
    const tests = [
      warned("One", "ignoring @mode on a scenario", "ignoring @timeout"),
      warned("Two", "ignoring @timeout"),
      makeTest("Clean"),
    ];
    // a second project declares the same scenarios again
    reporter.onBegin({}, { allTests: () => [...tests, ...tests] });

    const printed = stripAnsi(err.join(""));
    expect(printed).toBe(
      "playwright-yadda: ignoring @mode on a scenario\nplaywright-yadda: ignoring @timeout\n",
    );
    expect(stdout()).toBe("Running 6 scenarios\n\n");
  });

  it("prints nothing extra when no scenario in the run has a warning", () => {
    const reporter = new YaddaReporter();
    reporter.onBegin({}, { allTests: () => [makeTest("Clean")] });
    expect(err).toEqual([]);
  });

  it("tells the loader to leave the warnings to it", () => {
    expect((globalThis as Record<symbol, unknown>)[REPORTER_FLAG]).toBeUndefined();
    new YaddaReporter();
    expect((globalThis as Record<symbol, unknown>)[REPORTER_FLAG]).toBe(true);
  });

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

  describe("tags", () => {
    const tagged = (scenario: string, featureTags: string[], tags: string[]) =>
      Object.assign(makeTest(scenario), {
        tags,
        annotations: [
          { type: "feature-file", description: `${process.cwd()}/features/a.feature` },
          ...(featureTags.length
            ? [{ type: FEATURE_TAGS_ANNOTATION, description: featureTags.join(" ") }]
            : []),
        ],
      });

    const run = (options: ConstructorParameters<typeof YaddaReporter>[0], tests: TestCase[]) => {
      const reporter = new YaddaReporter(options);
      for (const test of tests) reporter.onTestEnd(test, makeResult("passed", 0));
      return stdout();
    };

    it("prints a feature's tags with the feature, and a scenario's other tags with the scenario", () => {
      const printed = run(undefined, [
        tagged("One", ["@storefront", "@slow"], ["@storefront", "@slow", "@smoke"]),
        tagged("Two", ["@storefront", "@slow"], ["@storefront", "@slow"]),
      ]);
      expect(printed).toContain("Feature: Alpha @storefront @slow\n");
      expect(printed).toContain("✓ Scenario: One @smoke (5ms)");
      expect(printed).toContain("✓ Scenario: Two (5ms)");
      expect(printed.match(/@storefront/g)).toHaveLength(1);
    });

    it("prints a scenario's value for a tag it overrides, and the feature's with the feature", () => {
      const printed = run(undefined, [tagged("One", ["@timeout:5000"], ["@timeout:100"])]);
      expect(printed).toContain("Feature: Alpha @timeout:5000\n");
      expect(printed).toContain("✓ Scenario: One @timeout:100 (5ms)");
    });

    it("prints no tags when there are none, or when asked not to", () => {
      expect(run(undefined, [tagged("One", [], [])])).toContain("✓ Scenario: One (5ms)");
      out = [];
      const printed = run({ tags: false }, [tagged("One", ["@a"], ["@a", "@b"])]);
      expect(printed).not.toContain("@");
    });

    it("prints a scenario's tags when Playwright gives no feature annotation", () => {
      expect(run(undefined, [tagged("One", [], ["@smoke"])])).toContain("✓ Scenario: One @smoke");
    });
  });

  describe("with --list", () => {
    // Playwright passes a reporter `_mode: "list"` in its options when it only lists the tests
    const listing = (tests: TestCase[], options: object = { _mode: "list" }) => {
      const reporter = new YaddaReporter(options as never);
      reporter.onBegin({}, { allTests: () => tests });
      reporter.onEnd({ status: "passed" } as never);
      return stdout();
    };
    const tagged = (scenario: string, feature: string, file: string, tags: string[] = []) =>
      Object.assign(makeTest(scenario), {
        titlePath: () => [
          "",
          "",
          "features.spec.ts",
          `Feature: ${feature}`,
          `Scenario: ${scenario}`,
        ],
        annotations: [{ type: "feature-file", description: `${process.cwd()}/features/${file}` }],
        tags,
      });

    it("lists the scenarios by feature file instead of printing a run that did not happen", () => {
      const text = listing([
        tagged("One", "Alpha", "a.feature", ["@smoke"]),
        tagged("Two", "Alpha", "a.feature"),
        tagged("Three", "Beta", "b.feature"),
      ]);
      expect(text).toBe(
        [
          "features/a.feature",
          "Feature: Alpha",
          "  Scenario: One @smoke",
          "  Scenario: Two",
          "",
          "features/b.feature",
          "Feature: Beta",
          "  Scenario: Three",
          "",
          "Total: 3 scenarios in 2 features",
          "",
        ].join("\n"),
      );
      expect(text).not.toMatch(/Running|passed/);
    });

    it("says which project a scenario is in when there are several", () => {
      const test = tagged("One", "Alpha", "a.feature");
      test.titlePath = () => [
        "",
        "chromium",
        "features.spec.ts",
        "Feature: Alpha",
        "Scenario: One",
      ];
      expect(listing([test])).toContain("[chromium] Feature: Alpha");
    });

    it("still prints the warnings of the scenarios", () => {
      listing([warned("One", "a problem")]);
      expect(err.join("")).toContain("a problem");
    });

    it("prints a run when Playwright does not say it is listing", () => {
      expect(listing([tagged("One", "Alpha", "a.feature")], {})).toContain("Running 1 scenario");
    });
  });
});
