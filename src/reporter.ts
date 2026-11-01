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

function formatError(error: TestError): string {
  const text = error.stack ?? error.message ?? String(error.value ?? "Unknown error");
  const plain = useColor ? text : stripAnsi(text);
  // Drop yadda/playwright-yadda internals so the trace points at the user's step code
  const cleaned = plain
    .split("\n")
    .filter(
      (l) =>
        !/^\s+at .*(node_modules[\\/](\.pnpm[\\/].*[\\/])?(yadda|playwright-yadda)[\\/]|playwright-yadda[\\/]src[\\/]|\/src\/(runner|feature)\.ts)/.test(
          l,
        ),
    )
    .join("\n");
  // Playwright's stack already begins with the message; append snippet when present
  return error.snippet && !cleaned.includes(error.snippet)
    ? `${cleaned}\n${useColor ? error.snippet : stripAnsi(error.snippet)}`
    : cleaned;
}

/**
 * Playwright reporter that prints each Gherkin scenario with a ✓ / ✗ next to
 * every step and the stack trace of the failing step below it.
 *
 * Usage: `reporter: [["playwright-yadda/reporter"]]`
 */
export default class YaddaReporter implements Reporter {
  private passed = 0;
  private failed = 0;
  private skipped = 0;
  private flaky = 0;
  private failures: string[] = [];
  private currentFeature: string | undefined;

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
        lines.push(indent(red(formatError(step.error!)), "        "));
      }
    }

    // Failure outside any step (fixtures, hooks, timeouts)
    const errors = result.errors?.length ? result.errors : result.error ? [result.error] : [];
    if (!ok && !skipped && !errorShown) {
      for (const err of errors) lines.push(indent(red(formatError(err)), "    "));
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
