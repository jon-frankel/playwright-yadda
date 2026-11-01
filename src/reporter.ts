import { createRequire } from "node:module";
import path from "node:path";
import type {
  FullResult,
  Reporter,
  TestCase,
  TestError,
  TestResult,
  TestStep,
} from "@playwright/test/reporter";

const useColor = !process.env.NO_COLOR && (process.stdout.isTTY || !!process.env.FORCE_COLOR);
const paint = (code: number, text: string) => (useColor ? `\x1b[${code}m${text}\x1b[0m` : text);
const green = (t: string) => paint(32, t);
const red = (t: string) => paint(31, t);
const yellow = (t: string) => paint(33, t);
const dim = (t: string) => paint(2, t);
const bold = (t: string) => paint(1, t);

// eslint-disable-next-line no-control-regex
const ANSI = /\x1b\[[0-9;]*m/g;
const stripAnsi = (s: string) => s.replace(ANSI, "");

function indent(text: string, pad: string): string {
  return text
    .split("\n")
    .map((line) => (line.length ? pad + line : line))
    .join("\n");
}

export interface YaddaReporterOptions {
  /** Keep yadda and playwright-yadda frames in stack traces. Default: false. */
  fullStackTrace?: boolean;
}

const NODE_MODULES_FRAME = /node_modules[\\/](playwright-)?yadda[\\/]/;
const STACK_FRAME = /^\s+at /;

/** Real directories of the yadda and playwright-yadda packages, as seen from the user's project. */
function packageDirs(): string[] {
  const require = createRequire(path.join(process.cwd(), "noop.js"));
  const dirs: string[] = [];
  for (const name of ["playwright-yadda", "yadda"]) {
    try {
      dirs.push(path.dirname(require.resolve(`${name}/package.json`)) + path.sep);
    } catch {
      // Not resolvable from here; the node_modules pattern still applies
    }
  }
  return dirs;
}

function makeInternalFrameTest(): (line: string) => boolean {
  const dirs = packageDirs();
  return (line) =>
    STACK_FRAME.test(line) && (NODE_MODULES_FRAME.test(line) || dirs.some((d) => line.includes(d)));
}

function formatError(
  error: TestError,
  isInternalFrame: ((line: string) => boolean) | undefined,
): string {
  const clean = (t: string) => (useColor ? t : stripAnsi(t));
  const text = clean(error.stack ?? error.message ?? String(error.value ?? "Unknown error"));
  const snippet = error.snippet ? clean(error.snippet) : undefined;
  const body = isInternalFrame
    ? text
        .split("\n")
        .filter((line) => !isInternalFrame(line))
        .join("\n")
    : text;
  // Playwright's stack already begins with the message; append the code snippet when it isn't there
  return snippet && !text.includes(snippet) ? `${body}\n${snippet}` : body;
}

/**
 * Playwright reporter that prints each Gherkin scenario with a ✓ / ✗ next to
 * every step and the stack trace of the failing step below it.
 *
 * Usage: `reporter: [["playwright-yadda/reporter"]]`
 *
 * Frames from yadda and playwright-yadda are hidden from stack traces so they start at your
 * step code; pass `{ fullStackTrace: true }` to keep them.
 */
export default class YaddaReporter implements Reporter {
  private passed = 0;
  private failed = 0;
  private skipped = 0;
  private flaky = 0;
  private failures: string[] = [];
  private currentFeature: string | undefined;
  private isInternalFrame: ((line: string) => boolean) | undefined;

  constructor(options?: YaddaReporterOptions | null) {
    const full = typeof options === "object" && options !== null && options.fullStackTrace === true;
    this.isInternalFrame = full ? undefined : makeInternalFrameTest();
  }

  printsToStdio(): boolean {
    return true;
  }

  onBegin(_config: unknown, suite: { allTests(): TestCase[] }): void {
    const n = suite.allTests().length;
    process.stdout.write(`Running ${n} ${n === 1 ? "scenario" : "scenarios"}\n\n`);
  }

  onTestEnd(test: TestCase, result: TestResult): void {
    const titles = test.titlePath().filter(Boolean);
    const featureIdx = titles.findIndex((t) => t.startsWith("Feature:"));
    const heading =
      featureIdx >= 0 ? titles.slice(featureIdx) : titles.slice(Math.min(2, titles.length - 1)); // drop project + file

    const status = result.status;
    const ok = status === "passed";
    const skipped = status === "skipped";
    if (ok) {
      this.passed++;
      if (result.retry > 0) this.flaky++;
    } else if (skipped) this.skipped++;
    else this.failed++;

    const lines: string[] = [];
    const retry = result.retry > 0 ? dim(` (retry #${result.retry})`) : "";
    const duration = dim(` (${result.duration}ms)`);
    const mark = ok ? green("✓") : skipped ? yellow("-") : red("✗");
    // Print the feature (and its file) once, before its first scenario
    const featureFile = test.annotations.find((a) => a.type === "feature-file")?.description;
    const featureTitle = heading.length > 1 ? heading.slice(0, -1).join(" › ") : "";
    const featureKey = featureFile ?? featureTitle;
    if (featureKey !== this.currentFeature) {
      this.currentFeature = featureKey;
      if (featureFile) lines.push(bold(path.relative(process.cwd(), featureFile)));
      if (featureTitle) lines.push(featureFile ? dim(featureTitle) : bold(featureTitle));
    }
    lines.push(`${mark} ${bold(heading[heading.length - 1] ?? test.title)}${retry}${duration}`);

    const steps = result.steps.filter((s) => s.category === "test.step");
    let errorShown = false;
    for (const step of steps) {
      const failedStep = !!step.error;
      lines.push(`    ${failedStep ? red("✗") : green("✓")} ${step.title}`);
      if (failedStep) {
        errorShown = true;
        lines.push(indent(red(formatError(step.error!, this.isInternalFrame)), "        "));
      }
    }

    // Failure outside any step (fixtures, hooks, timeouts)
    const errors = result.errors?.length ? result.errors : result.error ? [result.error] : [];
    if (!ok && !skipped && !errorShown) {
      for (const err of errors)
        lines.push(indent(red(formatError(err, this.isInternalFrame)), "    "));
    }

    const block = lines.join("\n") + "\n\n";
    process.stdout.write(block);
    if (!ok && !skipped && result.retry === test.retries) this.failures.push(heading.join(" › "));
  }

  onEnd(result: FullResult): void {
    const parts: string[] = [];
    if (this.failed) parts.push(red(`${this.failed} failed`));
    if (this.flaky) parts.push(yellow(`${this.flaky} flaky`));
    if (this.skipped) parts.push(yellow(`${this.skipped} skipped`));
    parts.push(green(`${this.passed} passed`));
    process.stdout.write(`${parts.join(", ")} ${dim(`(${result.status})`)}\n`);
  }
}

export type { TestStep };
