// Regenerates parser-cases.json and markdown-cases.json: what the installed yadda's FeatureParser and
// MarkdownFeatureParser make of each input below. YaddaGrammarTest holds the Kotlin port of those
// parsers to these answers, so rerun this after upgrading yadda, or adding a case:
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

const markdownCases = [
  ["a feature", "# Feature: F\n\n## Scenario: S\n\n- Given a\n- Then b\n"],
  ["heading levels are cosmetic", "### Feature: F\n#### Scenario: S\n- Given a\n"],
  ["no space after the hashes", "#Feature: F\n#Scenario: S\n- Given a\n"],
  ["list markers", "# Feature: F\n## Scenario: S\n- Given a\n* When b\n+ Then c\n"],
  ["indented list items", "# Feature: F\n## Scenario: S\n  - Given a\n    - When b\n"],
  ["plain lines are steps", "# Feature: F\n## Scenario: S\nGiven a\nWhen b\n"],
  ["an ordered list item is a plain line", "# Feature: F\n## Scenario: S\n1. Given a\n"],
  [
    "a heading that is no keyword is a step",
    "# Feature: F\n## Scenario: S\n### then something\n- Given a\n",
  ],
  [
    "a description under the feature",
    "# Feature: F\n\nSome words about it.\n\n- A bullet\n\n## Scenario: S\n- Given a\n",
  ],
  ["background", "# Feature: F\n## Background:\n- Given a\n## Scenario: S\n- Given b\n"],
  ["empty background", "# Feature: F\n## Background:\n## Scenario: S\n- Given b\n"],
  ["a rule", "# Feature: F\n## Rule: R\n### Scenario: S\n- Given a\n"],
  [
    "a rule and a scenario",
    "# Feature: F\n## Scenario: A\n- Given a\n## Rule: R\n### Scenario: S\n- Given b\n## Scenario: B\n- Given c\n",
  ],
  [
    "scenario outline",
    "# Feature: F\n## Scenario Outline: S [n]\n- Given [n]\n## Examples:\n\n| n |\n|---|\n| 1 |\n",
  ],
  ["no feature heading", "## Scenario: S\n- Given a\n"],
  ["empty file", ""],
  ["a readme", "# Project\n\nSome words.\n\n- one\n- two\n"],
  ["feature without scenarios", "# Feature: F\n\nwords\n"],
  ["empty scenario", "# Feature: F\n## Scenario: A\n## Scenario: B\n- Given x\n"],
  ["empty scenario at the end", "# Feature: F\n## Scenario: A\n- Given x\n## Scenario: B\n"],
  [
    "a step table",
    "# Feature: F\n## Scenario: S\n- Given the rows:\n\n  | a | b |\n  |---|---|\n  | 1 | 2 |\n- Then done\n",
  ],
  [
    "a step table with no blank line before",
    "# Feature: F\n## Scenario: S\n- Given the rows:\n  | a | b |\n  |---|---|\n  | 1 | 2 |\n- Then done\n",
  ],
  [
    "a step table with padding kept",
    "# Feature: F\n## Scenario: S\n- Given the rows:\n\n  | product   | price |\n  |-----------|-------|\n  | Doohickey | 4.99  |\n",
  ],
  [
    "a step table at the end of the file",
    "# Feature: F\n## Scenario: S\n- Given the rows:\n\n  | a |\n  |---|\n  | 1 |",
  ],
  [
    "two tables",
    "# Feature: F\n## Scenario: S\n- Given one:\n\n  | a |\n  | 1 |\n\n- And two:\n\n  | b |\n  | 2 |\n",
  ],
  [
    "a separator with alignment colons",
    "# Feature: F\n## Scenario: S\n- Given the rows:\n\n  | a | b |\n  |:--|--:|\n  | 1 | 2 |\n",
  ],
  [
    "a table without outer pipes in its separator",
    "# Feature: F\n## Scenario: S\n- Given the rows:\n\n  | a | b |\n  --|--\n  | 1 | 2 |\n",
  ],
  [
    "a table in the feature description",
    "# Feature: F\n\n| a |\n|---|\n| 1 |\n\n## Scenario: S\n- Given a\n",
  ],
  [
    "a fenced step",
    '# Feature: F\n## Scenario: S\n- Given a doc:\n\n  ```json\n  {"a": 1}\n  ```\n- Then done\n',
  ],
  ["a fence with no language", "# Feature: F\n## Scenario: S\n- Given a doc:\n```\nhello\n```\n"],
  ["a tilde fence", "# Feature: F\n## Scenario: S\n- Given a doc:\n~~~\nhello\n~~~\n"],
  ["a longer closing fence", "# Feature: F\n## Scenario: S\n- Given a doc:\n```\nhello\n`````\n"],
  [
    "a different fence inside",
    "# Feature: F\n## Scenario: S\n- Given a doc:\n```\n~~~\nhello\n~~~\n```\n",
  ],
  ["an unclosed fence", "# Feature: F\n## Scenario: S\n- Given a doc:\n```\nhello\n"],
  [
    "keywords inside a fence are text",
    "# Feature: F\n## Scenario: S\n- Given a doc:\n```\n## Scenario: T\n@oops\n# Feature: G\n```\n- Then done\n",
  ],
  [
    "a blank line inside a fence",
    "# Feature: F\n## Scenario: S\n- Given a doc:\n```\nhello\n\nworld\n```\n",
  ],
  [
    "a table inside a fence is text",
    "# Feature: F\n## Scenario: S\n- Given a doc:\n```\n| a |\n|---|\n```\n",
  ],
  [
    "content less indented than the fence",
    "# Feature: F\n## Scenario: S\n- Given a doc:\n\n    ```\n  hello\n    ```\n",
  ],
  ["an empty fence", "# Feature: F\n## Scenario: S\n- Given a doc:\n```\n```\n- Then done\n"],
  [
    "a fence in a background",
    "# Feature: F\n## Background:\n- Given a doc:\n```\nx\n```\n## Scenario: S\n- Given b\n",
  ],
  ["a fence in the feature description", "# Feature: F\n```\nx\n```\n## Scenario: S\n- Given a\n"],
  ["a blockquote is a comment", "# Feature: F\n## Scenario: S\n> Note to self\n- Given a\n"],
  [
    "a blockquote between a step and its table",
    "# Feature: F\n## Scenario: S\n- Given the rows:\n> hmm\n  | a |\n  | 1 |\n",
  ],
  ["an html comment", "# Feature: F\n<!-- hidden -->\n## Scenario: S\n- Given a\n"],
  [
    "a multi-line html comment",
    "# Feature: F\n<!--\n## Scenario: ignored\n- Given nothing\n-->\n## Scenario: S\n- Given a\n",
  ],
  [
    "text after a comment closes is dropped",
    "# Feature: F\n<!--\nhidden --> and this\n## Scenario: S\n- Given a\n",
  ],
  [
    "a comment inside a scenario",
    "# Feature: F\n## Scenario: S\n- Given a\n<!-- note -->\n- Then b\n",
  ],
  [
    "entities in prose",
    "# Feature: F\n## Scenario: S &amp; T\n- Given &lt;a&gt; &quot;b&quot; &apos;c&apos;\n",
  ],
  ["numeric entities", "# Feature: F\n## Scenario: S\n- Given &#65; and &#x42; and &#X43;\n"],
  ["unknown entities stay", "# Feature: F\n## Scenario: S\n- Given &nbsp; and &foo; and &amp\n"],
  [
    "an entity in a fence is verbatim",
    "# Feature: F\n## Scenario: S\n- Given a doc:\n```\n&lt;x&gt;\n```\n",
  ],
  [
    "entities in table cells",
    "# Feature: F\n## Scenario: S\n- Given the rows:\n\n  | &lt;a&gt; |\n  | &amp; |\n",
  ],
  [
    "annotations",
    "@Libraries=x, y\n@only\n\n# Feature: F\n\n@pending\n## Scenario: S\n- Given a\n",
  ],
  ["two tags on one line", "@a @b\n# Feature: F\n## Scenario: S\n- Given a\n"],
  [
    "an annotation inside a fence is text",
    "# Feature: F\n## Scenario: S\n- Given a doc:\n```\n@oops\n```\n",
  ],
  [
    "tag then text then scenario",
    "# Feature: F\n## Scenario: S\n- Given a\n@t\noops\n## Scenario: T\n- Given b\n",
  ],
  [
    "an annotation after a feature description",
    "# Feature: F\nabout\n@t\nmore\n## Scenario: S\n- Given a\n",
  ],
  [
    "background after a tag",
    "# Feature: F\n@t\n## Background:\n- Given b\n## Scenario: S\n- Given c\n",
  ],
  [
    "examples",
    "# Feature: F\n## Scenario: S [n]\n- Given [n] things\n\n## Examples:\n\n| n | m |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |\n",
  ],
  [
    "where",
    "# Feature: F\n## Scenario Outline: S [n]\n- Given [n]\n## Where:\n| n |\n|---|\n| 1 |\n| 2 |\n",
  ],
  [
    "examples without a separator row",
    "# Feature: F\n## Scenario: S [n]\n- Given [n]\n## Examples:\n| n |\n| 1 |\n",
  ],
  [
    "examples with a wrong number of fields",
    "# Feature: F\n## Scenario: S [n]\n- Given [n]\n## Examples:\n| n | m |\n|---|---|\n| 1 |\n",
  ],
  [
    "examples without rows",
    "# Feature: F\n## Scenario: S\n- Given a\n## Examples:\n| n |\n|---|\n",
  ],
  ["examples without anything", "# Feature: F\n## Scenario: S\n- Given a\n## Examples:\n"],
  [
    "examples then a scenario",
    "# Feature: F\n## Scenario: S [n]\n- Given [n]\n## Examples:\n| n |\n| 1 |\n## Scenario: T\n- Given b\n",
  ],
  ["examples as a plain line", "# Feature: F\n## Scenario: S\n- Given a\nExamples:\n"],
  [
    "meta placeholders",
    "# Feature: F\n## Scenario: S [n.index]\n- Given [n.start.line] [n]\n## Examples:\n| n |\n|---|\n| a |\n| b |\n",
  ],
  [
    "an example row with an annotation",
    "# Feature: F\n## Scenario: S [n]\n- Given [n]\n## Examples:\n| n |\n|---|\n@pending\n| 1 |\n| 2 |\n",
  ],
  [
    "a fenced step in an outline",
    "# Feature: F\n## Scenario: S [n]\n- Given the rows:\n\n  | a |\n  |---|\n  | [n] |\n\n## Examples:\n| n |\n|---|\n| 1 |\n",
  ],
  ["a thematic break is a plain line", "# Feature: F\n## Scenario: S\n- Given a\n---\n- Then b\n"],
  ["a hash line is a heading step", "# Feature: F\n## Scenario: S\n# a comment?\n- Given a\n"],
  ["a heading with closing hashes", "# Feature: F #\n## Scenario: S ##\n- Given a\n"],
  ["keywords are case-insensitive", "# FEATURE: F\n## SCENARIO: S\n- Given a\n"],
  ["a keyword without a colon is a step", "# Feature: F\n## Scenario: S\n## Examples\n"],
  ["a keyword as a plain line is a step", "# Feature: F\n## Scenario: S\nScenario: T\n"],
  [
    "two features",
    "# Feature: A\n## Scenario: S\n- Given x\n# Feature: B\n## Scenario: T\n- Given y\n",
  ],
  ["windows line endings", "# Feature: F\r\n## Scenario: S\r\n- Given a\r\n"],
  ["blank lines everywhere", "\n\n# Feature: F\n\n## Scenario: S\n\n- Given a\n\n- Then b\n\n"],
  ["a step with trailing spaces", "# Feature: F\n## Scenario: S   \n- Given a   \n"],
  [
    "a pipe line outside a table, then text",
    "# Feature: F\n## Scenario: S\n- Given a\n| x |\ntext\n",
  ],
];

function expected(Parser, cases) {
  return cases.map(([name, input]) => {
    let result;
    try {
      const feature = new Parser().parse(input);
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
}

function write(file, Parser, cases) {
  const out = expected(Parser, cases);
  writeFileSync(resolve(here, file), JSON.stringify(out, null, 2) + "\n");
  console.log(`${file}: ${out.length} cases, ${out.filter((c) => c.ok).length} parse`);
}

write("parser-cases.json", Yadda.parsers.FeatureParser, cases);
write("markdown-cases.json", Yadda.parsers.MarkdownFeatureParser, markdownCases);
