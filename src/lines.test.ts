import { describe, expect, it } from "vitest";
import Yadda from "yadda";
import { parseFeature } from "./feature.js";
import { trackLines } from "./lines.js";

/** The text of the line a step is said to be on, so a test reads as what it points at */
const textAt = (source: string, line: number | undefined) =>
  line === undefined ? undefined : source.split("\n")[line - 1]!.trim();

describe("line numbers in a parsed feature", () => {
  it("gives the feature, each scenario and each step the line it is written on", () => {
    const source = `# a comment
@tag
Feature: Shop

  Scenario: First
    Given one
    When two

  @slow
  Scenario: Second
    Then three
`;
    const feature = parseFeature(source, "gherkin");
    expect(textAt(source, feature.line)).toBe("Feature: Shop");
    expect(feature.scenarios.map((scenario) => textAt(source, scenario.line))).toEqual([
      "Scenario: First",
      "Scenario: Second",
    ]);
    expect(feature.scenarios.map((scenario) => scenario.stepLines)).toEqual([[6, 7], [11]]);
  });

  it("puts the steps of a Background first, on the lines they are written on there", () => {
    const source = `Feature: Shop

  Background:
    Given the shop is open
    And it has stock

  Scenario: One
    When they buy

  Scenario: Two
    When they leave
`;
    const [one, two] = parseFeature(source, "gherkin").scenarios;
    expect(one?.steps).toEqual(["Given the shop is open", "And it has stock", "When they buy"]);
    expect(one?.stepLines).toEqual([4, 5, 8]);
    expect(two?.stepLines).toEqual([4, 5, 11]);
  });

  it("counts a step with a block under it as one step, on its first line", () => {
    const source = `Feature: Shop
  Scenario: Blocks
    Given a cart of
      ---
      keyboard
      mouse
      ---
    When they pay
    Then the order is
      ---
      done
      ---
    And that is all
`;
    const [scenario] = parseFeature(source, "gherkin").scenarios;
    expect(scenario?.steps).toHaveLength(4);
    expect(scenario?.stepLines).toEqual([3, 8, 9, 13]);
    expect(scenario?.stepLines?.map((line) => textAt(source, line))).toEqual([
      "Given a cart of",
      "When they pay",
      "Then the order is",
      "And that is all",
    ]);
  });

  it("gives every example of an outline the lines of the outline's own steps", () => {
    const source = `Feature: Shop
  Scenario: Buying [n]
    Given [n] in the cart
    Then it costs [price]

    Examples:
      | n | price |
      | 1 | 10    |
      | 2 | 20    |
`;
    const { scenarios } = parseFeature(source, "gherkin");
    expect(scenarios.map((scenario) => scenario.steps[0])).toEqual([
      "Given 1 in the cart",
      "Given 2 in the cart",
    ]);
    for (const scenario of scenarios) {
      expect(scenario.line).toBe(2);
      expect(scenario.stepLines).toEqual([3, 4]);
    }
  });

  it("follows the scenarios that come after an outline's examples", () => {
    const source = `Feature: Shop
  Scenario: Outline [n]
    Given [n] of them

    Examples:
      | n |
      | 1 |

  Scenario: After
    Given a plain step
`;
    const { scenarios } = parseFeature(source, "gherkin");
    expect(scenarios.map((scenario) => [scenario.line, scenario.stepLines])).toEqual([
      [2, [3]],
      [9, [10]],
    ]);
  });

  it("reads a Markdown feature the same way, with its fences and tables", () => {
    const source = `# Feature: Shop

## Background:

- Given the shop is open

## Scenario: Paying

- Given a cart of

  \`\`\`
  keyboard
  \`\`\`

- When they pay
- Then the receipt is

  | item | qty |
  | ---- | --- |
  | pen  | 2   |

## Scenario: Leaving

- Given they leave
`;
    const feature = parseFeature(source, "markdown");
    expect(textAt(source, feature.line)).toBe("# Feature: Shop");
    const [paying, leaving] = feature.scenarios;
    expect(paying?.stepLines?.map((line) => textAt(source, line))).toEqual([
      "- Given the shop is open",
      "- Given a cart of",
      "- When they pay",
      "- Then the receipt is",
    ]);
    expect(leaving?.stepLines?.map((line) => textAt(source, line))).toEqual([
      "- Given the shop is open",
      "- Given they leave",
    ]);
  });

  it("lines up with the steps of every scenario of the example features", async () => {
    const fs = await import("node:fs");
    const path = await import("node:path");
    const dir = path.resolve(import.meta.dirname, "../examples/basic/features");
    const files = fs.readdirSync(dir, { recursive: true, encoding: "utf8" }).filter((file) => {
      return /\.(feature|md)$/.test(file);
    });
    expect(files.length).toBeGreaterThan(5);
    for (const file of files) {
      const source = fs.readFileSync(path.join(dir, file), "utf8");
      for (const scenario of parseFeature(source, "auto", path.join(dir, file)).scenarios) {
        expect(scenario.stepLines, `${file}: ${scenario.title}`).toHaveLength(
          scenario.steps.length,
        );
        for (const [index, step] of scenario.steps.entries()) {
          // The step's first line is the line's text, unless an outline replaced a placeholder in it
          const written = textAt(source, scenario.stepLines![index])!.replace(/^[-*+]\s+/, "");
          const first = step.split("\n")[0]!;
          const placeholder = /[<[][^>\]]+[>\]]/.test(written);
          expect(
            placeholder || written === first,
            `${file}: ${scenario.title}: "${first}" is not on line ${scenario.stepLines![index]} ("${written}")`,
          ).toBe(true);
        }
      }
    }
  });

  it("leaves a scenario of a Rule alone, as it is not run", () => {
    const source = `Feature: Shop
  Rule: A rule
    Scenario: In the rule
      Given a step
`;
    const feature = parseFeature(source, "gherkin");
    expect(feature.scenarios).toEqual([]);
    expect(textAt(source, feature.line)).toBe("Feature: Shop");
  });
});

describe("trackLines", () => {
  it("does nothing to a parser that does not look like Yadda 3's", () => {
    expect(trackLines({}).featureLine()).toBeUndefined();
    const emit = () => {};
    const parser = { emit, registry: {} };
    expect(trackLines(parser).featureLine()).toBeUndefined();
    expect(parser.emit).toBe(emit);
  });

  it("leaves the parser's result as it is apart from the lines", () => {
    const text = "Feature: F\n  Scenario: S\n    Given a step\n";
    const parser = new Yadda.parsers.FeatureParser();
    const plain = new Yadda.parsers.FeatureParser().parse(text) as { scenarios: object[] };
    trackLines(parser as never);
    const tracked = parser.parse(text) as { scenarios: { line?: number; stepLines?: number[] }[] };
    const { line, stepLines, ...rest } = tracked.scenarios[0]!;
    expect([line, stepLines]).toEqual([2, [3]]);
    expect(rest).toEqual(plain.scenarios[0]);
  });
});
