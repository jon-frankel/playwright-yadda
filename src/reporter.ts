import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import type {
  FullResult,
  Reporter,
  TestCase,
  TestError,
  TestResult,
} from "@playwright/test/reporter";
import type { YaddaReporterOptions } from "./types.js";
import { FEATURE_TAGS_ANNOTATION, REPORTER_FLAG, WARNING_ANNOTATION } from "./warnings.js";

// Same rules as Playwright's own reporters: FORCE_COLOR=0/false disables, any other value forces
const forceColor = process.env.FORCE_COLOR;
const colorDisabled = !!process.env.NO_COLOR || forceColor === "0" || forceColor === "false";
const useColor = !colorDisabled && (!!process.stdout.isTTY || !!forceColor);
const paint = (code: number, text: string) => (useColor ? `\x1b[${code}m${text}\x1b[0m` : text);
const green = (t: string) => paint(32, t);
const red = (t: string) => paint(31, t);
const yellow = (t: string) => paint(33, t);
const dim = (t: string) => paint(2, t);
const cyan = (t: string) => paint(36, t);
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
  // The snippet shows the code at error.location; when that is an internal frame the stack
  // above no longer mentions it, so showing it would contradict the filter
  const loc = error.location;
  const snippetIsInternal =
    !!loc && !!isInternalFrame?.(`    at ${loc.file}:${loc.line}:${loc.column}`);
  const snippet = error.snippet && !snippetIsInternal ? clean(error.snippet) : undefined;
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
  // Feature title -> file, so a scenario that fails before its annotation exists still groups
  private fileByFeature = new Map<string, string>();
  private isInternalFrame: ((line: string) => boolean) | undefined;
  private showTags: boolean;

  constructor(options?: YaddaReporterOptions | null) {
    const full = typeof options === "object" && options !== null && options.fullStackTrace === true;
    this.showTags = !(typeof options === "object" && options !== null && options.tags === false);
    this.isInternalFrame = full ? undefined : makeInternalFrameTest();
    // Playwright makes the reporters before it loads the specs. The loader stays quiet about how
    // scenarios are declared, because onBegin prints it, for the scenarios that are in the run.
    (globalThis as Record<symbol, unknown>)[REPORTER_FLAG] = true;
  }

  printsToStdio(): boolean {
    return true;
  }

  onBegin(_config: unknown, suite: { allTests(): TestCase[] }): void {
    const tests = suite.allTests();
    // Warnings about how scenarios are declared, each once however many scenarios or projects carry it
    const warnings = new Set<string>();
    for (const test of tests) {
      for (const annotation of test.annotations ?? []) {
        if (annotation.type === WARNING_ANNOTATION && annotation.description) {
          warnings.add(annotation.description);
        }
      }
    }
    for (const warning of warnings) {
      process.stderr.write(`${yellow(`playwright-yadda: ${warning}`)}\n`);
    }
    const n = tests.length;
    process.stdout.write(`Running ${n} ${n === 1 ? "scenario" : "scenarios"}\n\n`);
  }

  onTestEnd(test: TestCase, result: TestResult): void {
    const titles = test.titlePath().filter(Boolean);
    const featureIdx = titles.findIndex((t) => t.startsWith("Feature:"));
    const heading =
      featureIdx >= 0 ? titles.slice(featureIdx) : titles.slice(Math.min(2, titles.length - 1)); // drop project + file

    this.finalResults.set(test, result);

    const status = result.status;
    const skipped = status === "skipped";
    // What the test was expected to do: a `@fail` scenario is expected to fail, and passes when it does
    const ok = !skipped && status === (test.expectedStatus ?? "passed");

    const lines: string[] = [];
    const retry = result.retry > 0 ? dim(` (retry #${result.retry})`) : "";
    const duration = dim(` (${result.duration}ms)`);
    const mark = ok ? green("✓") : skipped ? yellow("-") : red("✗");
    // The feature's tags go with the feature, the scenario's others with the scenario
    const featureTags = this.showTags
      ? (test.annotations.find((a) => a.type === FEATURE_TAGS_ANNOTATION)?.description ?? "")
          .split(" ")
          .filter(Boolean)
      : [];
    const ownTags = this.showTags
      ? (test.tags ?? []).filter((tag) => !featureTags.includes(tag))
      : [];
    // Print the feature (and its file) once, before its first scenario
    const featureTitle = heading.length > 1 ? heading.slice(0, -1).join(" › ") : "";
    const annotatedFile = test.annotations.find((a) => a.type === "feature-file")?.description;
    if (annotatedFile) this.fileByFeature.set(featureTitle, annotatedFile);
    const featureFile = annotatedFile ?? this.fileByFeature.get(featureTitle);
    const featureKey = featureFile ?? featureTitle;
    if (featureKey !== this.currentFeature) {
      this.currentFeature = featureKey;
      if (featureFile) lines.push(bold(path.relative(process.cwd(), featureFile)));
      if (featureTitle) {
        const title = featureFile ? dim(featureTitle) : bold(featureTitle);
        lines.push(featureTags.length ? `${title} ${cyan(featureTags.join(" "))}` : title);
      }
    }
    const tagText = ownTags.length ? ` ${cyan(ownTags.join(" "))}` : "";
    lines.push(
      `${mark} ${bold(heading[heading.length - 1] ?? test.title)}${tagText}${retry}${duration}`,
    );

    const steps = result.steps.filter((s) => s.category === "test.step");
    // Playwright sets no error on a step still running when the test times out, is interrupted
    // or its worker dies; such a step has not ended, which it reports as a negative duration
    // How many errors per message are already shown under steps; each covers one result error
    const shown = new Map<string, number>();
    for (const step of steps) {
      const failedStep = !!step.error || step.duration < 0;
      lines.push(`    ${failedStep ? red("✗") : green("✓")} ${step.title}`);
      if (step.error) {
        const key = stripAnsi(step.error.message ?? "");
        shown.set(key, (shown.get(key) ?? 0) + 1);
        lines.push(indent(red(formatError(step.error, this.isInternalFrame)), "        "));
      }
    }

    // Playwright records no error for a `@fail` scenario that passes, which it still counts as failed
    if (!ok && !skipped && status === "passed" && test.expectedStatus === "failed") {
      lines.push(indent(red("Expected to fail, but passed."), "    "));
    }

    // Errors not already shown under a step: fixtures, hooks, timeouts, teardown
    const errors = result.errors?.length ? result.errors : result.error ? [result.error] : [];
    if (!ok && !skipped) {
      for (const err of errors) {
        const key = stripAnsi(err.message ?? "");
        const covered = shown.get(key) ?? 0;
        if (covered > 0) {
          shown.set(key, covered - 1);
          continue;
        }
        lines.push(indent(red(formatError(err, this.isInternalFrame)), "    "));
      }
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
    for (const [test, final] of this.finalResults) {
      if (final.status === "skipped") skipped++;
      else if (final.status === (test.expectedStatus ?? "passed")) {
        if (final.retry > 0) flaky++;
        else passed++;
      } else failed++;
    }

    const parts: string[] = [];
    if (failed) parts.push(red(`${failed} failed`));
    if (flaky) parts.push(yellow(`${flaky} flaky`));
    if (skipped) parts.push(yellow(`${skipped} skipped`));
    parts.push(green(`${passed} passed`));
    process.stdout.write(`${parts.join(", ")} ${dim(`(${result.status})`)}\n`);
  }
}
