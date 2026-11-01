import type { TestCase, TestResult, TestStep } from "@playwright/test/reporter";
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
  vi.unstubAllEnvs();
  vi.resetModules();
});

const featureFile = `${process.cwd()}/features/a.feature`;
const titlePath = ["", "", "a.spec.ts", "Feature: Alpha", "Scenario: crash"];

const test = {
  title: "Scenario: crash",
  titlePath: () => titlePath,
  annotations: [{ type: "feature-file", description: featureFile }],
  retries: 0,
  expectedStatus: "passed",
} as unknown as TestCase;

const step = (title: string, duration: number): TestStep =>
  ({ title, category: "test.step", duration, steps: [] }) as unknown as TestStep;

describe("review 3 blockers", () => {
  // Shape taken from a real Playwright 1.64 run where a step calls process.exit():
  // status "failed", the running step has duration -1 and no error.
  it("marks the step that was running when the worker crashed as failed, not passed", () => {
    const message = "Error: worker process exited unexpectedly (code=3, signal=null)";
    const result = {
      status: "failed",
      retry: 0,
      duration: 0,
      steps: [step("Given ok", 3), step("Given crash", -1)],
      errors: [{ message }],
      error: { message },
    } as unknown as TestResult;

    new YaddaReporter().onTestEnd(test, result);

    const text = stripAnsi(out.join(""));
    expect(text).toContain("✓ Given ok");
    expect(text).toContain("✗ Given crash");
  });

  it("emits no ANSI colour codes when FORCE_COLOR=0 (as Playwright's own reporters do)", async () => {
    vi.stubEnv("FORCE_COLOR", "0");
    vi.stubEnv("NO_COLOR", undefined as unknown as string);
    vi.resetModules();
    const { default: FreshReporter } = await import("./reporter.js");

    const result = {
      status: "passed",
      retry: 0,
      duration: 1,
      steps: [step("Given ok", 1)],
      errors: [],
    } as unknown as TestResult;
    new FreshReporter().onTestEnd(test, result);

    expect(out.join("")).not.toContain("\x1b[");
  });
});
