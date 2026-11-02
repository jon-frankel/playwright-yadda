/**
 * Titles of the tests playwright-yadda declares, and the `--grep` patterns that pick them out.
 *
 * A scenario is a Playwright test named `Scenario: <title>` in a describe named `Feature: <title>`.
 * Playwright matches `--grep` against the project name, spec file, those titles and the test's tags
 * (Playwright 1.42 and later), joined with spaces, as a case-insensitive regular expression. So
 * running one scenario, from an editor or a script, means building the pattern from its titles.
 *
 * The JetBrains plugin does the same in Kotlin (`PlaywrightGrep.kt`). The two are held together by
 * `grep.cases.json`, which both test suites read: change a pattern and both must be changed to agree.
 */

/** The title of the describe declared for a feature, which is also what a pattern is built from */
export function featureTestTitle(featureTitle: string): string {
  return `Feature: ${featureTitle.trim()}`;
}

/** The title of the test declared for a scenario */
export function scenarioTestTitle(scenarioTitle: string): string {
  return `Scenario: ${scenarioTitle.trim()}`;
}

const SPECIAL = /[.*+?^${}()|[\]\\/]/g;

/** The text of a title as a regular expression matching just that text */
const escape = (text: string): string => text.replace(SPECIAL, "\\$&");

const featurePattern = (featureTitle: string) => escape(featureTestTitle(featureTitle));
const scenarioPattern = (scenarioTitle: string) => escape(scenarioTestTitle(scenarioTitle));

/**
 * Every scenario of a feature. Ending at `Scenario: ` stops "Cart" from also matching the feature
 * "Cart Admin".
 */
export function grepForFeature(featureTitle: string): string {
  return `${featurePattern(featureTitle)} Scenario: `;
}

/**
 * One scenario. It ends at the scenario's title, so "Clears cart" does not also run "Clears cart
 * twice", but any number of the tags Playwright appends to a title (`@smoke @timeout:5000`) may
 * follow it: playwright-yadda gives Playwright every tag of the feature and the scenario.
 */
export function grepForScenario(featureTitle: string, scenarioTitle: string): string {
  return `${featurePattern(featureTitle)} ${scenarioPattern(scenarioTitle)}(?: @\\S+)*$`;
}

/** A placeholder of an outline's title, which each example fills in with its own value */
const PLACEHOLDER = /\[[^\]]*\]/;

/**
 * A scenario outline. Its examples are named after it with each placeholder (`[n]`) replaced by
 * the example's value, so a placeholder matches anything and the rest of the title is literal.
 */
export function grepForScenarioOutline(featureTitle: string, scenarioTitle: string): string {
  const title = scenarioTestTitle(scenarioTitle).split(PLACEHOLDER).map(escape).join(".*");
  return `${featurePattern(featureTitle)} ${title}(?: @\\S+)*$`;
}
