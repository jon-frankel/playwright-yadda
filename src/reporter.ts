import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import type {
  FullResult,
  Reporter,
  TestCase,
  TestError,
  TestResult,
  TestStep,
} from "@playwright/test/reporter";
import type { YaddaReporterOptions } from "./types.js";

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

const NODE_MODULES_FRAME = /node_modules[\\/](playwright-)?yadda[\\/]/;
const STACK_FRAME = /^\s+at /;

/**
 * Directories whose frames are internal: this package's own `dist/` (or `src/` in a checkout),
 * located from this file rather than the cwd, and yadda's `lib/`, resolved from here too.
 * Each is listed as a path and as a file:// URL, since ESM frames can use either.
 */
function internalDirs(): string[] {
  // Built files live in dist/, but source-mapped frames point at the sibling src/
  const own = path.dirname(fileURLToPath(import.meta.url));
  const dirs = [own, path.join(own, "..", "src")].map((d) => path.normalize(d));
  try {
    const yaddaRoot = path.dirname(createRequire(import.meta.url).resolve("yadda/package.json"));
    dirs.push(path.join(yaddaRoot, "lib"));
  } catch {
    // yadda not resolvable from here; the node_modules pattern still applies
  }
  return dirs.flatMap((d) => [d + path.sep, pathToFileURL(d).href + "/"]);
}

function makeInternalFrameTest(): (line: string) => boolean {
  const dirs = internalDirs();
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
  // Final attempt of each test (Playwright reuses the TestCase across retries)
  private finalResults = new Map<TestCase, TestResult>();
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

    this.finalResults.set(test, result);

    const status = result.status;
    const ok = status === "passed";
    const skipped = status === "skipped";

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
    // Playwright sets no error on the step still running when the test times out or is interrupted
    const cutShort = status === "timedOut" || status === "interrupted";
    const cutShortStep =
      cutShort && !steps.some((s) => s.error) ? steps[steps.length - 1] : undefined;
    let errorShown = false;
    for (const step of steps) {
      const failedStep = !!step.error || step === cutShortStep;
      lines.push(`    ${failedStep ? red("✗") : green("✓")} ${step.title}`);
      if (failedStep) {
        if (step.error) {
          errorShown = true;
          lines.push(indent(red(formatError(step.error, this.isInternalFrame)), "        "));
        }
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
  }

  onStdOut(chunk: string | Buffer): void {
    process.stdout.write(chunk);
  }

  onStdErr(chunk: string | Buffer): void {
    process.stderr.write(chunk);
  }

  /** Errors outside any test: file loading, config, worker crashes */
  onError(error: TestError): void {
    process.stdout.write(`${red(formatError(error, this.isInternalFrame))}\n\n`);
  }

  onEnd(result: FullResult): void {
    let passed = 0;
    let failed = 0;
    let skipped = 0;
    let flaky = 0;
    for (const final of this.finalResults.values()) {
      if (final.status === "passed") {
        if (final.retry > 0) flaky++;
        else passed++;
      } else if (final.status === "skipped") skipped++;
      else failed++;
    }

    const parts: string[] = [];
    if (failed) parts.push(red(`${failed} failed`));
    if (flaky) parts.push(yellow(`${flaky} flaky`));
    if (skipped) parts.push(yellow(`${skipped} skipped`));
    parts.push(green(`${passed} passed`));
    process.stdout.write(`${parts.join(", ")} ${dim(`(${result.status})`)}\n`);
  }
}

export type { TestStep };
