import { createRequire } from "node:module";

/**
 * Test annotation carrying a warning about how a scenario is declared. The leading `_` keeps
 * Playwright's HTML report from listing it. The yadda reporter prints each distinct one once,
 * for the scenarios that are in the run.
 */
export const WARNING_ANNOTATION = "_playwright-yadda-warning";

/** Set on `globalThis` by the yadda reporter, which then prints the warnings in place of the loader */
export const REPORTER_FLAG = Symbol.for("playwright-yadda.reporter");

/**
 * Whether the installed Playwright accepts a details object (`{ annotation, tag }`) in
 * `test(title, details, fn)`, which it has done since 1.42.
 */
export function supportsTestDetails(): boolean {
  try {
    const { version } = createRequire(import.meta.url)("@playwright/test/package.json");
    const [major = 0, minor = 0] = String(version).split(".").map(Number);
    return major > 1 || (major === 1 && minor >= 42);
  } catch {
    return false;
  }
}

/** Problems already warned about by this process, so each is reported once */
const warned = new Set<string>();

/**
 * Print a warning about how a scenario is declared, once. Playwright loads the spec in its main
 * process and again in every worker, so only the main process prints: a worker has
 * `TEST_WORKER_INDEX` set before it loads anything. When the yadda reporter is in use (and the
 * warning can reach it as an annotation) it prints instead, for the scenarios that run. Pass
 * `annotated: false` for a warning that no scenario carries, which only the loader can print.
 */
export function warnOnce(message: string, annotated = true): void {
  if (process.env.TEST_WORKER_INDEX !== undefined) return;
  if (annotated && (globalThis as Record<symbol, unknown>)[REPORTER_FLAG] && supportsTestDetails())
    return;
  if (warned.has(message)) return;
  warned.add(message);
  console.warn(`playwright-yadda: ${message}`);
}

/** For tests: forget the problems already warned about, so each test sees its own warnings */
export function resetWarningsForTests(): void {
  warned.clear();
}
