// Regenerates parser-cases.json: what the installed yadda's FeatureParser makes of each input below.
// YaddaGrammarTest holds the Kotlin port of that parser to these answers, so rerun this after
// upgrading yadda, or adding a case:
//
//   node ide/jetbrains/src/test/resources/yadda/generate-parser-cases.mjs
import { createRequire } from "node:module";
import { writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(resolve(here, "../../../../../../package.json"));
const Yadda = require("yadda");

const cases = [
  ["a feature", "Feature: F\n  Scenario: S\n    Given a\n    Then b\n"],
  ["no feature heading", "Scenario: S\n  Given a\n"],
  ["text before the feature", "some preamble\nFeature: F\n  Scenario: S\n    Given a\n"],
  ["empty scenario in the middle", "Feature: F\n  Scenario: A\n  Scenario: B\n    Given x\n"],
  ["empty scenario at the end", "Feature: F\n  Scenario: A\n    Given x\n  Scenario: B\n"],
  ["feature without scenarios", "Feature: F\n  some description\n"],
  ["empty file", ""],
  ["comments only", "# nothing here\n"],
  [
    "two features",
    "Feature: A\n  Scenario: S\n    Given x\nFeature: B\n  Scenario: T\n    Given y\n",
  ],
  ["background", "Feature: F\n  Background:\n    Given a\n  Scenario: S\n    Given b\n"],
  ["empty background", "Feature: F\n  Background:\n  Scenario: S\n    Given b\n"],
  [
    "background after a scenario",
    "Feature: F\n  Scenario: S\n    Given a\n  Background:\n    Given b\n  Scenario: T\n    Given c\n",
  ],
  [
    "background after a tag",
    "Feature: F\n  @t\n  Background:\n    Given b\n  Scenario: S\n    Given c\n",
  ],
  [
    "description then tag then text",
    "Feature: F\n  about it\n  @t\n  more about it\n  Scenario: S\n    Given a\n",
  ],
  ["a rule", "Feature: F\n  Rule: R\n    Scenario: S\n      Given a\n"],
  [
    "a rule and a scenario",
    "Feature: F\n  Scenario: A\n    Given a\n  Rule: R\n    Scenario: S\n      Given b\n  Scenario: B\n    Given c\n",
  ],
  ["an empty rule", "Feature: F\n  Rule: R\n  Scenario: S\n    Given a\n"],
  [
    "two rules",
    "Feature: F\n  Rule: R\n    Scenario: S\n      Given a\n  Rule: Q\n    Scenario: T\n      Given b\n",
  ],
  [
    "a rule with a background",
    "Feature: F\n  Rule: R\n    Background:\n      Given z\n    Scenario: S\n      Given a\n",
  ],
  ["rule only", "Rule: R\n  Scenario: S\n    Given a\n"],
  ["two tags on one line", "@a @b\nFeature: F\n  Scenario: S\n    Given a\n"],
  [
    "name-value tags",
    "@Libraries=x, y\n@only\nFeature: F\n  @pending\n  Scenario: S\n    Given a\n",
  ],
  ["tag with spaces in the name", "Feature: F\n  @my tag=1\n  Scenario: S\n    Given a\n"],
  [
    "tag in a scenario starts the next",
    "Feature: F\n  Scenario: S\n    Given a\n  @t\n  Scenario: T\n    Given b\n",
  ],
  [
    "tag then text then scenario",
    "Feature: F\n  Scenario: S\n    Given a\n  @t\n  oops\n  Scenario: T\n    Given b\n",
  ],
  [
    "outline with pipeless examples",
    "Feature: F\n  Scenario: S [n]\n    Given [n] things\n  Examples:\n    n | m\n    1 | 2\n    3 | 4\n",
  ],
  [
    "outline with outer pipes",
    "Feature: F\n  Scenario: S [n]\n    Given [n] things\n  Examples:\n    | n | m |\n    | 1 | 2 |\n",
  ],
  [
    "where and scenario outline",
    "Feature: F\n  Scenario Outline: S [n]\n    Given [n]\n  Where:\n    n\n    1\n    2\n",
  ],
  [
    "wrong number of fields",
    "Feature: F\n  Scenario: S [n]\n    Given [n]\n  Examples:\n    n | m\n    1\n",
  ],
  ["examples without rows", "Feature: F\n  Scenario: S\n    Given a\n  Examples:\n    n | m\n"],
  ["examples without anything", "Feature: F\n  Scenario: S\n    Given a\n  Examples:\n"],
  [
    "examples then a scenario",
    "Feature: F\n  Scenario: S [n]\n    Given [n]\n  Examples:\n    n\n    1\n  Scenario: T\n    Given b\n",
  ],
  [
    "blank line ends the examples",
    "Feature: F\n  Scenario: S [n]\n    Given [n]\n  Examples:\n    n\n    1\n\n    2\n",
  ],
  [
    "examples with a leading dash line",
    "Feature: F\n  Scenario: S [n]\n    Given [n]\n  Examples:\n  ---\n    n\n    1\n",
  ],
  [
    "multi-line example rows",
    "Feature: F\n  Scenario: S [a]\n    Given [a]\n  Examples:\n    a | b\n    ---\n    1 | 2\n    3 | 4\n    ---\n    5 | 6\n",
  ],
  [
    "example row annotation",
    "Feature: F\n  Scenario: S [n]\n    Given [n]\n  Examples:\n    n\n    @pending\n    1\n    2\n",
  ],
  [
    "unicode separators",
    "Feature: F\n  Scenario: S [n]\n    Given [n]\n  Examples:\n    n ┆ m\n    1 ┆ 2\n",
  ],
  [
    "example indentation",
    "Feature: F\n  Scenario: S [n]\n    Given [n]\n  Examples:\n      n | m\n    1 | 2\n",
  ],
  [
    "meta placeholders",
    "Feature: F\n  Scenario: S [n.index]\n    Given [n.start.line] [n]\n  Examples:\n    n\n    a\n    b\n",
  ],
  [
    "a fenced step",
    "Feature: F\n  Scenario: S\n    Given rows:\n    ---\n    | a | b |\n    | 1 | 2 |\n    ---\n    Then done\n",
  ],
  [
    "a fenced step with a remark",
    'Feature: F\n  Scenario: S\n    Given a doc:\n    --- json\n    {"a": 1}\n    ---\n    Then done\n',
  ],
  [
    "an unclosed fence",
    "Feature: F\n  Scenario: S\n    Given a doc:\n    ---\n    hello\n    world\n",
  ],
  [
    "a fence with a blank line inside",
    "Feature: F\n  Scenario: S\n    Given a doc:\n    ---\n    hello\n\n    world\n    ---\n    Then done\n",
  ],
  [
    "a fence with a trailing blank line",
    "Feature: F\n  Scenario: S\n    Given a doc:\n    ---\n    hello\n\n    ---\n    Then done\n",
  ],
  [
    "content indented less than its fence",
    "Feature: F\n  Scenario: S\n    Given a doc:\n      ---\n    hello\n      ---\n",
  ],
  [
    "a keyword inside a fence",
    "Feature: F\n  Scenario: S\n    Given a doc:\n    ---\n    hello\n    Scenario: T\n    Given b\n",
  ],
  [
    "a tag inside a fence",
    "Feature: F\n  Scenario: S\n    Given a doc:\n    ---\n    @oops\n    ---\n",
  ],
  [
    "a comment inside a fence",
    "Feature: F\n  Scenario: S\n    Given a doc:\n    ---\n    # kept? no\n    text\n    ---\n",
  ],
  [
    "block comment inside a fence",
    "Feature: F\n  Scenario: S\n    Given a doc:\n    ---\n    ###\n    text\n    ###\n    more\n    ---\n",
  ],
  [
    "a keyword straight after the opening fence",
    "Feature: F\n  Scenario: S\n    Given a doc:\n    ---\n    Scenario: T\n    Given b\n",
  ],
  [
    "two fences in a row",
    "Feature: F\n  Scenario: S\n    Given a doc:\n    ---\n    x\n    ---\n    ---\n    y\n    ---\n",
  ],
  [
    "an empty fence",
    "Feature: F\n  Scenario: S\n    Given a doc:\n    ---\n    ---\n    Then done\n",
  ],
  [
    "a blank line before the fence",
    "Feature: F\n  Scenario: S\n    Given a doc:\n\n    ---\n    x\n    ---\n",
  ],
  ["a pipe fence", "Feature: F\n  Scenario: S\n    Given a doc:\n    |---\n    | a |\n    |---\n"],
  ["a fence at feature level", "Feature: F\n  ---\n  x\n  ---\n  Scenario: S\n    Given a\n"],
  ["a fence before the feature", "---\nFeature: F\n  Scenario: S\n    Given a\n"],
  [
    "a fence in a background",
    "Feature: F\n  Background:\n    Given a doc:\n    ---\n    x\n    ---\n  Scenario: S\n    Given b\n",
  ],
  [
    "examples inside a fence",
    "Feature: F\n  Scenario: S\n    Given a doc:\n    ---\n    Examples:\n    ---\n",
  ],
  ["examples at feature level", "Feature: F\n  Examples:\n  Scenario: S\n    Given a\n"],
  ["block comments", "Feature: F\n###\nScenario: ignored\n###\n  Scenario: S\n    Given a\n"],
  ["line comments", "# top\nFeature: F\n  # c\n  Scenario: S\n    # c\n    Given a\n"],
  ["keywords are case-insensitive", "FEATURE: F\n  SCENARIO: S\n    Given a\n"],
  ["keyword without a colon is a step", "Feature: F\n  Scenario: S\n    Examples\n"],
  ["feature title is optional", "Feature:\n  Scenario:\n    Given a\n"],
  ["windows line endings", "Feature: F\r\n  Scenario: S\r\n    Given a\r\n"],
  [
    "background in a rule after a scenario",
    "Feature: F\n  Rule: R\n    Scenario: S\n      Given a\n    Background:\n      Given b\n    Scenario: T\n      Given c\n",
  ],
  ["blank lines everywhere", "\n\nFeature: F\n\n  Scenario: S\n\n    Given a\n\n    Then b\n\n"],
  [
    "second examples replaces the first",
    "Feature: F\n  Scenario: S [n]\n    Given [n]\n  Examples:\n    n\n    1\n  Examples:\n    n\n    2\n",
  ],
];

const out = cases.map(([name, input]) => {
  let result;
  try {
    const feature = new Yadda.parsers.FeatureParser().parse(input);
    result = {
      ok: true,
      steps: feature.scenarios.map((s) => s.steps),
      titles: feature.scenarios.map((s) => s.title),
      rules: feature.rules.length,
    };
  } catch (e) {
    const m = /^Error parsing line (\d+), "[\s\S]*"\.\nOriginal error was: ([\s\S]*)$/.exec(
      e.message,
    );
    result = m
      ? { ok: false, line: Number(m[1]), message: m[2] }
      : { ok: false, message: e.message };
  }
  return { name, input, ...result };
});

writeFileSync(resolve(here, "parser-cases.json"), JSON.stringify(out, null, 2) + "\n");
console.log(`${out.length} cases, ${out.filter((c) => c.ok).length} parse`);
