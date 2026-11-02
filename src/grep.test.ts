import { describe, expect, it } from "vitest";
import Yadda from "yadda";
import cases from "./grep.cases.json" with { type: "json" };
import { defineFeature, parseFeature } from "./feature.js";
import {
  featureTestTitle,
  grepForFeature,
  grepForScenario,
  grepForScenarioOutline,
  scenarioTestTitle,
} from "./grep.js";
import { createLibraryRegistry } from "./registry.js";

interface GrepCase {
  name: string;
  kind: "feature" | "scenario" | "outline";
  feature: string;
  scenario?: string;
  grep: string;
  selects: string[];
  rejects: string[];
}

/**
 * How Playwright applies `--grep` (`forceRegExp` and `createTitleMatcher`): the pattern becomes a
 * case-insensitive regular expression, unless it is written as `/pattern/flags`, and is tested
 * against the project name, the spec file, the titles and the tags, joined with spaces.
 */
const playwrightMatches = (grep: string, titles: string): boolean =>
  new RegExp(grep, "gi").test(`chromium features.spec.ts ${titles}`);

const patternFor = (c: GrepCase): string =>
  c.kind === "feature"
    ? grepForFeature(c.feature)
    : c.kind === "outline"
      ? grepForScenarioOutline(c.feature, c.scenario!)
      : grepForScenario(c.feature, c.scenario!);

// `grep.cases.json` is also read by the JetBrains plugin's PlaywrightGrepTest, so a pattern that
// changes here has to change there too
describe("grep patterns", () => {
  for (const c of cases.cases as GrepCase[]) {
    describe(`${c.name}`, () => {
      it("is the pattern both sides agree on", () => {
        expect(patternFor(c)).toBe(c.grep);
      });

      it("selects what it should and nothing else", () => {
        // A pattern written as /.../ would be read as a regular expression with flags by Playwright
        expect(c.grep.startsWith("/")).toBe(false);
        const matched = (titles: string[]) =>
          titles.map((title) => [title, playwrightMatches(c.grep, title)]);
        expect(matched(c.selects)).toEqual(c.selects.map((title) => [title, true]));
        expect(matched(c.rejects)).toEqual(c.rejects.map((title) => [title, false]));
      });
    });
  }
});

describe("the titles of the tests", () => {
  it("name the feature's describe and the scenario's test, as a pattern is built from", () => {
    expect(featureTestTitle("Shop")).toBe("Feature: Shop");
    expect(scenarioTestTitle("Buy")).toBe("Scenario: Buy");
    // Yadda keeps the space after a title, which no pattern could be told about
    expect(featureTestTitle("Shop  ")).toBe("Feature: Shop");
    expect(scenarioTestTitle("Buy ")).toBe("Scenario: Buy");
  });

  it("are the ones defineFeature declares, which the patterns select", () => {
    const feature = parseFeature(
      "Feature: Shop  \n  Scenario: Buy \n    Given a step\n",
      "gherkin",
    );
    const declared: string[] = [];
    const mockTest: any = Object.assign((title: string) => declared.push(title), {
      describe: (title: string, fn: () => void) => {
        declared.push(title);
        fn();
      },
    });
    defineFeature(feature, {
      test: mockTest,
      registry: createLibraryRegistry({ common: new Yadda.Library() }),
    });

    expect(declared).toEqual(["Feature: Shop", "Scenario: Buy"]);
    expect(playwrightMatches(grepForScenario("Shop  ", "Buy "), declared.join(" "))).toBe(true);
    expect(playwrightMatches(grepForFeature("Shop  "), declared.join(" "))).toBe(true);
  });
});
