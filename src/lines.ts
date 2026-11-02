/**
 * Line numbers for what Yadda's feature parser returns.
 *
 * Yadda hands every line's number to the parser's handlers and then drops it: the result it exports
 * is titles and step texts only. This puts a thin layer in front of one parser instance to keep
 * them, by watching the events its `Scenario` and `Background` objects are given. It relies on two
 * things Yadda 3 exposes on the instance, `emit` and `registry`; with a Yadda that has neither, or
 * when what was counted does not add up, nothing is added and the feature is as it always was.
 */

/** What of Yadda's own parser objects is touched here. Both are internal to Yadda. */
interface Section {
  on(event: string, data: unknown, line?: number): unknown;
  export(): unknown;
  stepLines?: number[];
}
type SectionClass = new (...args: unknown[]) => Section;
interface YaddaParser {
  emit?: (event: string, data: unknown, line?: number) => unknown;
  registry?: { Scenario?: SectionClass; Background?: SectionClass };
}

export interface LineTracker {
  /** Line of the `Feature:` heading, once the text has been parsed */
  featureLine(): number | undefined;
}

/**
 * Make `parser` record the lines of the scenarios and steps it parses. The scenarios it returns then
 * have `line` and `stepLines`. Call before `parse`, once per parser.
 */
export function trackLines(parser: YaddaParser): LineTracker {
  const registry = parser?.registry;
  const emit = parser?.emit;
  const Scenario = registry?.Scenario;
  const Background = registry?.Background;
  if (
    !registry ||
    typeof emit !== "function" ||
    typeof Scenario !== "function" ||
    typeof Background !== "function"
  ) {
    return { featureLine: () => undefined };
  }

  // The line of the event being handled: a scenario is constructed while its `Scenario` event is
  let current: number | undefined;
  let featureLine: number | undefined;
  parser.emit = (event, data, line) => {
    current = line;
    if (event === "Feature" && featureLine === undefined) featureLine = line;
    return emit.call(parser, event, data, line);
  };

  registry.Background = function (...args: unknown[]) {
    const section = new Background(...args);
    section.stepLines = trackSteps(section);
    return section;
  } as unknown as SectionClass;

  registry.Scenario = function (...args: unknown[]) {
    const line = current;
    // The second argument is the background the scenario's steps follow
    const background = args[1] as Section | undefined;
    const section = new Scenario(...args);
    const own = trackSteps(section);

    const exported = section.export.bind(section);
    section.export = () => {
      const result = exported();
      const backgroundLines = linesOf(background);
      for (const scenario of [result].flat() as {
        line?: number;
        steps: string[];
        stepLines?: number[];
      }[]) {
        if (line !== undefined) scenario.line = line;
        // An outline's examples all keep the outline's own lines, as their steps share its text
        const stepLines = backgroundLines && [...backgroundLines, ...own];
        if (stepLines && stepLines.length === scenario.steps.length) scenario.stepLines = stepLines;
      }
      return result;
    };
    return section;
  } as unknown as SectionClass;

  return { featureLine: () => featureLine };
}

/**
 * Lines of the steps a background has, or none when it has none. Anything else (the two merged for a
 * rule) is not known, so the scenario that follows it gets no lines.
 */
function linesOf(background: Section | undefined): number[] | undefined {
  if (!background) return [];
  if (background.stepLines) return background.stepLines;
  const exported = background.export() as { steps?: unknown[] } | undefined;
  return exported?.steps?.length === 0 ? [] : undefined;
}

/**
 * Watch the events a scenario or background is given and note the line each of its steps starts on.
 * Yadda reads a text line after a step as a new step, except between a pair of `---` lines (or a
 * Markdown fence or table), where the lines belong to the step before it; this follows the same
 * rule. Examples are given to another object, so their rows are never seen here.
 */
function trackSteps(section: Section): number[] {
  const lines: number[] = [];
  let afterStep = false;
  let block: "none" | "opened" | "inside" = "none";

  const on = section.on.bind(section);
  section.on = (event, data, line) => {
    const kind = event.toLowerCase();
    if (block === "none") {
      if (kind === "text" && line !== undefined) {
        lines.push(line);
        afterStep = true;
      } else if (kind === "dash" && afterStep) {
        block = "opened";
      }
    } else if (block === "opened") {
      if (kind === "text") block = "inside";
    } else if (kind === "dash") {
      block = "none";
      afterStep = false;
    }
    return on(event, data, line);
  };
  return lines;
}
