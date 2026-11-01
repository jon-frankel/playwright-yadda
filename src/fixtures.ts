import { AsyncLocalStorage } from "node:async_hooks";
import type { TestInfo, TestType } from "@playwright/test";
import Yadda from "yadda";
import type { StepLibrary } from "./types.js";

/**
 * Fixtures every scenario sets up, whether or not a step names them. The default scenario context
 * (`{ page, context, testInfo }`, and what `createContext` receives) is built from them.
 */
export const BASE_FIXTURE_NAMES = ["page", "context"] as const;

/** Property of the fixtures object holding the scenario's `TestInfo` (it is not a fixture) */
export const TEST_INFO_KEY = "$testInfo";

/**
 * The fixtures (test-scoped and worker-scoped) of a Playwright `test`, as a step destructures them
 */
export type FixturesOf<T> =
  T extends TestType<infer TestArgs, infer WorkerArgs> ? TestArgs & WorkerArgs : never;

/** First argument of a step in `'fixtures'` mode: the scenario's fixtures plus its `TestInfo` */
export type StepFixtures<F> = F & { [TEST_INFO_KEY]: TestInfo };

/**
 * A step function in `'fixtures'` mode. Destructure the fixtures the step needs from the first
 * argument, as in a Playwright test: `async ({ page, myFixture }, arg) => { ... }`.
 */
export type FixtureStepFunction<F> = (fixtures: StepFixtures<F>, ...args: any[]) => unknown;

type Methods = "define" | "given" | "when" | "then";

/**
 * A Yadda step library whose steps receive Playwright fixtures as their first argument
 */
export type FixtureStepLibrary<F> = Omit<Yadda.ContextParamLibrary, Methods> & {
  [M in Methods]: (
    signatures: Yadda.Signature,
    fn?: FixtureStepFunction<F>,
    macroContext?: Yadda.Properties,
    options?: Yadda.DefineOptions,
  ) => FixtureStepLibrary<F>;
};

/** Thrown when a step's first parameter does not say, by destructuring, which fixtures it needs */
export class FixtureDeclarationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "FixtureDeclarationError";
  }
}

// -- Reading the fixture names a step function destructures -----------------------------------
//
// This is not a JavaScript parser. Playwright decides what to set up by reading the text of the
// test function's first parameter (`fixtureParameterNames`, which is not exported), and a step
// has to be read the same way, since its destructuring is what names the fixtures. The helpers
// below do just enough for that. `mask` makes a copy of the source in which comments, strings,
// templates and regex literals cannot be mistaken for structure; the parameter list and the
// `{ a, b: c, d = 1 }` pattern are then found and split on commas in the masked copy, skipping
// commas inside nested brackets, and the names are cut from the same positions of the real text.
// Nothing is evaluated. Anything that cannot be read (a rest property, a parameter that is not
// destructured) is an error rather than a guess.

const IDENTIFIER = /^[\p{ID_Start}$_][\p{ID_Continue}$‌‍]*$/u;

/** A function's source twice over, the same length each time, so a position in one is in the other */
interface Masked {
  /** Comments blanked out, everything else as written */
  text: string;
  /** As `text`, with the inside of strings, templates and regex literals filled with `_` */
  code: string;
}

/** Characters after which a `/` starts a regex literal, not a division */
const BEFORE_REGEX = "(,=:[!&|?{;+-*%<>~^";

/** End (exclusive) of the string or template starting at `start`, or the end of the source */
function quotedEnd(source: string, start: number): number {
  const quote = source[start]!;
  for (let i = start + 1; i < source.length; i++) {
    if (source[i] === "\\") i++;
    else if (source[i] === quote) return i + 1;
  }
  return source.length;
}

/** End (exclusive) of the regex literal starting at `start`, or -1 if it is not one */
function regexEnd(source: string, start: number): number {
  let inClass = false;
  for (let i = start + 1; i < source.length; i++) {
    const ch = source[i]!;
    if (ch === "\n") return -1;
    if (ch === "\\") i++;
    else if (ch === "[") inClass = true;
    else if (ch === "]") inClass = false;
    else if (ch === "/" && !inClass) return i + 1;
  }
  return -1;
}

function mask(source: string): Masked {
  let text = "";
  let code = "";
  let previous = ""; // last character of `code` that is not whitespace
  for (let i = 0; i < source.length;) {
    const ch = source[i]!;
    const next = source[i + 1];
    let end = -1;
    if (ch === "/" && next === "/") {
      end = source.indexOf("\n", i);
      end = end === -1 ? source.length : end;
      text += " ".repeat(end - i);
      code += " ".repeat(end - i);
      i = end;
      continue;
    }
    if (ch === "/" && next === "*") {
      end = source.indexOf("*/", i + 2);
      end = end === -1 ? source.length : end + 2;
      text += " ".repeat(end - i);
      code += " ".repeat(end - i);
      i = end;
      continue;
    }
    if (ch === '"' || ch === "'" || ch === "`") end = quotedEnd(source, i);
    else if (ch === "/" && (previous === "" || BEFORE_REGEX.includes(previous))) {
      end = regexEnd(source, i);
    }
    if (end !== -1) {
      text += source.slice(i, end);
      code += ch + "_".repeat(Math.max(0, end - i - 2)) + (end - i > 1 ? source[end - 1] : "");
      previous = ch === "/" ? ")" : ch; // a literal is an operand: a `/` after it divides
      i = end;
      continue;
    }
    text += ch;
    code += ch;
    if (!/\s/.test(ch)) previous = ch;
    i++;
  }
  return { text, code };
}

const slice = (masked: Masked, start: number, end?: number): Masked => ({
  text: masked.text.slice(start, end),
  code: masked.code.slice(start, end),
});

/** Split on commas that are not inside brackets, braces or parentheses */
function splitTopLevel(masked: Masked): Masked[] {
  const parts: Masked[] = [];
  let depth = 0;
  let start = 0;
  for (let i = 0; i < masked.code.length; i++) {
    const ch = masked.code[i]!;
    if (ch === "(" || ch === "[" || ch === "{") depth++;
    else if (ch === ")" || ch === "]" || ch === "}") depth--;
    else if (ch === "," && depth === 0) {
      parts.push(slice(masked, start, i));
      start = i + 1;
    }
  }
  parts.push(slice(masked, start));
  return parts
    .map((part) => {
      const lead = part.code.length - part.code.trimStart().length;
      return slice(part, lead, lead + part.code.trim().length);
    })
    .filter((part) => part.code !== "");
}

/**
 * The function's parameter list: between the first `(` and its matching `)`.
 *
 * @throws {FixtureDeclarationError} when there is no parameter list or it never closes
 */
function parameterList(masked: Masked): Masked {
  // `x => ...` and `async x => ...`: one bare parameter, which cannot be a destructuring pattern
  const bare = masked.code.match(/^\s*(?:async\s+)?([\p{ID_Start}$_][\p{ID_Continue}$]*)\s*=>/u);
  if (bare)
    return slice(
      masked,
      masked.code.indexOf(bare[1]!),
      masked.code.indexOf(bare[1]!) + bare[1]!.length,
    );

  const open = masked.code.indexOf("(");
  if (open !== -1) {
    let depth = 0;
    for (let i = open; i < masked.code.length; i++) {
      if (masked.code[i] === "(") depth++;
      else if (masked.code[i] === ")" && --depth === 0) return slice(masked, open + 1, i);
    }
  }
  throw new FixtureDeclarationError(
    "Cannot find the end of the step's parameter list, so its fixtures cannot be read.",
  );
}

/**
 * The fixture names a function destructures from its first parameter, which is how Playwright
 * decides what to set up for a test. A function with no parameters needs none.
 *
 * `$testInfo` is not a fixture and is left out.
 *
 * @throws {FixtureDeclarationError} when the first parameter is not an object destructuring
 * pattern, or uses a rest property or a name that is not a plain identifier, or when the function
 * is bound or native, so has no parameters to read
 */
export function parseFixtureNames(fn: (...args: any[]) => unknown): string[] {
  const masked = mask(Function.prototype.toString.call(fn));
  if (/\{\s*\[native code\]\s*\}\s*$/.test(masked.code)) {
    throw new FixtureDeclarationError(
      "Cannot read the fixtures of a bound or native function; its parameters are not in its " +
        "source. Pass the function itself, or wrap it in one that destructures them.",
    );
  }

  const first = splitTopLevel(parameterList(masked))[0];
  if (first === undefined) return [];

  if (!first.text.startsWith("{") || !first.text.endsWith("}")) {
    throw new FixtureDeclarationError(
      `The first parameter of a step in "fixtures" mode must destructure the fixtures it uses, ` +
        `for example "async ({ page }) => ..." or "({}) => ..." for none; found "${first.text}".`,
    );
  }

  const names: string[] = [];
  for (const prop of splitTopLevel(slice(first, 1, first.code.length - 1))) {
    if (prop.text.startsWith("...")) {
      throw new FixtureDeclarationError(
        `Rest property "${prop.text}" is not supported in a step's fixtures. List each fixture it uses.`,
      );
    }
    // the name is what comes before a `:` (alias) or `=` (default)
    const end = prop.code.search(/[:=]/);
    const key = (end === -1 ? prop.text : prop.text.slice(0, end)).trim();
    if (!IDENTIFIER.test(key)) {
      throw new FixtureDeclarationError(
        `Cannot read the fixture name "${key}" from the step's parameters; fixture names must be ` +
          `plain identifiers.`,
      );
    }
    if (key === "testInfo") {
      throw new FixtureDeclarationError(
        `"testInfo" is not a fixture; a step gets the scenario's TestInfo as "${TEST_INFO_KEY}".`,
      );
    }
    if (key !== TEST_INFO_KEY) names.push(key);
  }
  return names;
}

// -- Which fixtures a library's steps declare ---------------------------------------------------

const declaredFixtures = new WeakMap<object, Map<string, string[]>>();

/** The key Yadda identifies a macro by: its signature as a normalised RegExp string */
function macroKey(signature: unknown): string {
  return new RegExp(signature as string).toString();
}

/**
 * Remember the fixtures `fn` declares for the macros `signatures` define in `library`
 */
export function recordStepFixtures(
  library: object,
  signatures: unknown,
  names: readonly string[],
): void {
  let byMacro = declaredFixtures.get(library);
  if (!byMacro) declaredFixtures.set(library, (byMacro = new Map()));
  for (const signature of Array.isArray(signatures) ? signatures : [signatures]) {
    byMacro.set(macroKey(signature), [...names]);
  }
}

/**
 * A function that returns the fixtures the steps of a scenario use: those declared by the macro
 * Yadda would run for each of `steps`, in first-seen order. Libraries that do not use fixtures
 * mode contribute nothing, and when none of the libraries given does, nothing is ranked at all.
 *
 * The winner is worked out the way Yadda does when it runs the steps one after another: the
 * closest pattern wins, and a tie goes to the library of the previous step's macro. A step nothing
 * defines, or that Yadda cannot choose a definition for (an ambiguous one), adds nothing: the
 * scenario fails when it runs, before any definition could use its fixtures.
 *
 * Matching a step line against a library's patterns is the costly part, and suites repeat step
 * lines across scenarios (and whole step sequences, in generated ones), so each (library, step
 * line) is matched once per collector, and each sequence over the same libraries is ranked once.
 * Make one collector per suite, once its libraries are defined: it does not see steps defined
 * afterwards.
 */
export function createFixtureCollector(): FixtureCollector {
  const matches = new WeakMap<object, Map<string, unknown[]>>();
  // A whole scenario's answer, for scenarios that repeat another's steps over the same libraries
  const ids = new WeakMap<object, number>();
  let nextId = 1;
  const sequences = new Map<string, string[]>();
  const sequenceKey = (libraries: StepLibrary[], steps: string[]) =>
    libraries
      .map((library) => {
        if (!ids.has(library)) ids.set(library, nextId++);
        return ids.get(library);
      })
      .join(",") +
    "\n" +
    steps.join("\n");
  const cached = (library: StepLibrary, step: string): unknown[] => {
    let byStep = matches.get(library);
    if (!byStep) matches.set(library, (byStep = new Map()));
    let macros = byStep.get(step);
    if (!macros) byStep.set(step, (macros = library.find_compatible_macros(step) as unknown[]));
    return macros;
  };

  return (libraries, steps) => {
    if (!libraries.some((library) => declaredFixtures.has(library))) return [];

    const key = sequenceKey(libraries, steps);
    const known = sequences.get(key);
    if (known) return [...known];

    const names = new Set<string>();
    // Yadda only asks a library for the macros compatible with a step, so the cache can stand in
    const lookups = libraries.map((library) => ({
      find_compatible_macros: (step: string) => cached(library, step),
    }));
    // `rank_macros` is not in Yadda's typings; it is what the interpreter itself ranks steps with
    const interpreter = new Yadda.Interpreter(lookups as any) as any;

    // Validating a step is what moves the interpreter on to the next one, so the ranking it does
    // is also the answer; keep it rather than ranking the step a second time
    let report: { valid?: boolean; winner?: Macro } = {};
    const rank = interpreter.rank_macros.bind(interpreter);
    interpreter.rank_macros = (step: string) => {
      const competition = rank(step);
      const validate = competition.validate.bind(competition);
      competition.validate = () => (report = validate());
      return competition;
    };

    for (const step of steps) {
      report = {};
      try {
        interpreter.validate([step]);
      } catch {
        // undefined or ambiguous; reported when the scenario runs
      }
      const macro = report.valid ? report.winner : undefined;
      if (!macro) continue;
      for (const name of declaredFixtures.get(macro.library)?.get(macro.toString()) ?? []) {
        names.add(name);
      }
    }
    sequences.set(key, [...names]);
    return [...names];
  };
}

/** What {@link createFixtureCollector} returns */
export type FixtureCollector = (libraries: StepLibrary[], steps: string[]) => string[];

/** The fixtures a single scenario's steps use; see {@link createFixtureCollector} */
export function collectScenarioFixtures(libraries: StepLibrary[], steps: string[]): string[] {
  return createFixtureCollector()(libraries, steps);
}

/** The parts of a Yadda macro used here */
interface Macro {
  library: object;
  toString(): string;
}

// -- Running steps with the scenario's fixtures -----------------------------------------------

const activeFixtures = new AsyncLocalStorage<Record<string, unknown>>();

/** Run `body` with `fixtures` as what steps in `'fixtures'` mode receive */
export function withScenarioFixtures<T>(fixtures: Record<string, unknown>, body: () => T): T {
  return activeFixtures.run(fixtures, body);
}

/**
 * Wrap a user step so it receives the scenario's fixtures instead of Yadda's copy of the
 * scenario context. Outside a feature suite (`runStep` called directly) it falls back to that
 * context, so a context holding `page` and the like still works.
 *
 * `names` are the fixtures the step destructures. One missing from what it is given fails the step
 * naming it, rather than reaching it as `undefined`. That happens when steps are defined after the
 * suite was declared, or when two copies of playwright-yadda are in play.
 *
 * Yadda tells a step that takes a `next` callback from one that does not by comparing the
 * function's declared `length` with the number of arguments it would pass, and tells a promise from
 * a sync step by the return value. So the wrapper keeps the step's `length` and returns its result
 * untouched; the step then behaves as it would in `'param'` mode: sync, promise-returning or
 * callback style.
 */
export function bindFixtureStep(fn: (...args: any[]) => unknown, names: readonly string[]) {
  const bound = function (this: unknown, context: unknown, ...args: unknown[]) {
    const fixtures = activeFixtures.getStore() ?? context;
    const missing = names.find(
      (name) => typeof fixtures !== "object" || fixtures === null || !Object.hasOwn(fixtures, name),
    );
    if (missing !== undefined) {
      const step = (context as { step?: unknown } | null)?.step;
      throw new Error(
        `The step${typeof step === "string" ? ` "${step}"` : ""} uses the fixture "${missing}", ` +
          `which was not set up for this scenario. Define every step library before ` +
          `defineFeatureSuite() runs, and use one copy of playwright-yadda.`,
      );
    }
    return fn.call(this, fixtures, ...args);
  };
  Object.defineProperty(bound, "length", { value: fn.length });
  return bound;
}

// -- Building the Playwright test callback ----------------------------------------------------

/**
 * Build a Playwright test callback that destructures exactly `names` from its fixtures, which is
 * what makes Playwright set them up, and hands them to `run` along with the `TestInfo`.
 *
 * Playwright reads the destructuring pattern from the function's source text, so the callback
 * is created with `new Function`.
 */
export function createFixtureTestBody(
  names: readonly string[],
  run: (fixtures: Record<string, unknown>, testInfo: TestInfo) => Promise<void>,
): (fixtures: Record<string, unknown>, testInfo: TestInfo) => Promise<void> {
  for (const name of names) {
    if (!IDENTIFIER.test(name)) {
      throw new FixtureDeclarationError(`"${name}" is not a valid fixture name.`);
    }
  }
  // Each name is bound to a local first so a fixture called `run` or `testInfo` cannot shadow them
  const pattern = names.map((name) => `${name}: __f_${name}`).join(", ");
  const object = names.map((name) => `${JSON.stringify(name)}: __f_${name}`).join(", ");
  // oxlint-disable-next-line no-implied-eval -- Playwright reads the fixtures from the source text
  const factory = new Function(
    "__run",
    `return async function ({ ${pattern} }, __testInfo) { return __run({ ${object} }, __testInfo); };`,
  ) as (run: unknown) => (fixtures: Record<string, unknown>, testInfo: TestInfo) => Promise<void>;
  return factory(run);
}
