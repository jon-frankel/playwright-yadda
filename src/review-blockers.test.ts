import type { TestCase, TestError, TestResult, TestStep } from "@playwright/test/reporter";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import YaddaReporter from "./reporter.js";

// eslint-disable-next-line no-control-regex
const stripAnsi = (s: string) => s.replace(/\x1b\[[0-9;]*m/g, "");

let out: string[];

beforeEach(() => {
  out = [];
  vi.spyOn(process.stdout, "write").mockImplementation((chunk: any) => {
    out.push(String(chunk));
    return true;
  });
  vi.spyOn(process.stderr, "write").mockImplementation(() => true);
});

afterEach(() => {
  vi.restoreAllMocks();
});

const stdout = () => stripAnsi(out.join(""));
const featureFile = `${process.cwd()}/features/a.feature`;

function makeTest(
  titlePath: string[],
  extra: Partial<Record<string, unknown>> = {},
  withFeatureFile = true,
): TestCase {
  return {
    title: titlePath[titlePath.length - 1],
    titlePath: () => titlePath,
    annotations: withFeatureFile ? [{ type: "feature-file", description: featureFile }] : [],
    retries: 0,
    expectedStatus: "passed",
    ...extra,
  } as unknown as TestCase;
}

function makeStep(title: string, error?: TestError): TestStep {
  return { title, category: "test.step", error, duration: 4, steps: [] } as unknown as TestStep;
}

function makeResult(
  status: TestResult["status"],
  steps: TestStep[] = [],
  errors: TestError[] = [],
): TestResult {
  return {
    status,
    retry: 0,
    duration: 5,
    steps,
    errors,
    error: errors[0],
  } as unknown as TestResult;
}

const scenarioPath = (name: string) => ["", "", "a.spec.ts", "Feature: Alpha", `Scenario: ${name}`];

describe("review blockers", () => {
  it("prints an afterEach hook error even when a step also failed", () => {
    const reporter = new YaddaReporter();
    const stepErr: TestError = { message: "Error: STEP-FAILED", stack: "Error: STEP-FAILED" };
    const hookErr: TestError = {
      message: "Error: AFTER-EACH-HOOK-FAILED",
      stack: "Error: AFTER-EACH-HOOK-FAILED",
    };
    // Shape observed from a real Playwright 1.64 run: hook errors live only in result.errors
    reporter.onTestEnd(
      makeTest(scenarioPath("S")),
      makeResult("failed", [makeStep("Given a bad step", stepErr)], [stepErr, hookErr]),
    );
    expect(stdout()).toContain("AFTER-EACH-HOOK-FAILED");
  });

  it("prints the feature file once when a scenario's body never ran (fixture/hook failure)", () => {
    const reporter = new YaddaReporter();
    const fixtureErr: TestError = { message: "Error: browserType.launch failed" };
    reporter.onTestEnd(makeTest(scenarioPath("One")), makeResult("passed"));
    // The annotation is only pushed from the test body, so this one arrives without it
    reporter.onTestEnd(
      makeTest(scenarioPath("Two"), {}, false),
      makeResult("failed", [], [fixtureErr]),
    );
    reporter.onTestEnd(makeTest(scenarioPath("Three")), makeResult("passed"));

    const lines = stdout().split("\n");
    expect(lines.filter((l) => l === "features/a.feature")).toHaveLength(1);
    expect(lines.filter((l) => l === "Feature: Alpha")).toHaveLength(1);
  });
});
