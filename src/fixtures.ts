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
// below do just enough for that: cut out comments, find the parameter list, and split it and the
// `{ a, b: c, d = 1 }` pattern on commas, skipping commas inside nested brackets and strings.
// They never evaluate or interpret the code. Anything they cannot read (a rest property, a
// parameter that is not destructured) is an error rather than a guess.

const IDENTIFIER = /^[A-Za-z_$][\w$]*$/;

/** Source text with comments blanked out, string and template contents left alone */
function stripComments(source: string): string {
  let out = "";
  let quote: string | null = null;
  for (let i = 0; i < source.length; i++) {
    const ch = source[i]!;
    const next = source[i + 1];
    if (quote) {
      out += ch;
      if (ch === "\\") out += source[++i] ?? "";
      else if (ch === quote) quote = null;
    } else if (ch === '"' || ch === "'" || ch === "`") {
      quote = ch;
      out += ch;
    } else if (ch === "/" && next === "/") {
      while (i < source.length && source[i] !== "\n") i++;
      out += "\n";
    } else if (ch === "/" && next === "*") {
      i += 2;
      while (i < source.length && !(source[i] === "*" && source[i + 1] === "/")) i++;
      i++;
      out += " ";
    } else {
      out += ch;
    }
  }
  return out;
}

/** Split on commas that are not inside brackets, braces, parentheses or strings */
function splitTopLevel(text: string): string[] {
  const parts: string[] = [];
  let depth = 0;
  let quote: string | null = null;
  let current = "";
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]!;
    if (quote) {
      current += ch;
      if (ch === "\\") current += text[++i] ?? "";
      else if (ch === quote) quote = null;
      continue;
    }
    if (ch === '"' || ch === "'" || ch === "`") quote = ch;
    else if (ch === "(" || ch === "[" || ch === "{") depth++;
    else if (ch === ")" || ch === "]" || ch === "}") depth--;
    else if (ch === "," && depth === 0) {
      parts.push(current);
      current = "";
      continue;
    }
    current += ch;
  }
  parts.push(current);
  return parts.map((part) => part.trim()).filter((part) => part !== "");
}

/**
 * Text between the first `(` of a function's source and its matching `)`.
 *
 * @throws {FixtureDeclarationError} when there is no parameter list or it never closes. The last
 * happens when a default value holds something this scanner reads wrongly, such as a regex literal
 * with an unbalanced bracket; reading no fixtures there would hide the mistake.
 */
function parameterList(source: string): string {
  // `x => ...` and `async x => ...`: one bare parameter, which cannot be a destructuring pattern
  const bare = source.match(/^\s*(?:async\s+)?([A-Za-z_$][\w$]*)\s*=>/);
  if (bare) return bare[1]!;

  const open = source.indexOf("(");
  if (open !== -1) {
    let depth = 0;
    let quote: string | null = null;
    for (let i = open; i < source.length; i++) {
      const ch = source[i]!;
      if (quote) {
        if (ch === "\\") i++;
        else if (ch === quote) quote = null;
      } else if (ch === '"' || ch === "'" || ch === "`") quote = ch;
      else if (ch === "(") depth++;
      else if (ch === ")" && --depth === 0) return source.slice(open + 1, i);
    }
  }
  throw new FixtureDeclarationError(
    `Cannot find the end of the step's parameter list, so its fixtures cannot be read. ` +
      `Simplify its parameters (no regex literals in default values, for one).`,
  );
}

/**
 * The fixture names a function destructures from its first parameter, which is how Playwright
 * decides what to set up for a test. A function with no parameters needs none.
 *
 * `$testInfo` is not a fixture and is left out.
 *
 * @throws {FixtureDeclarationError} when the first parameter is not an object destructuring
 * pattern, or uses a rest property or a name that is not a plain identifier
 */
export function parseFixtureNames(fn: (...args: any[]) => unknown): string[] {
  const source = stripComments(Function.prototype.toString.call(fn));
  if (source.includes("[native code]")) {
    throw new FixtureDeclarationError(
      "Cannot read the fixtures of a bound or native function; its parameters are not in its " +
        "source. Pass the function itself, or wrap it in one that destructures them.",
    );
  }
  const params = parameterList(source);

  const first = splitTopLevel(params)[0];
  if (first === undefined) return [];

  if (!first.startsWith("{") || !first.endsWith("}")) {
    throw new FixtureDeclarationError(
      `The first parameter of a step in "fixtures" mode must destructure the fixtures it uses, ` +
        `for example "async ({ page }) => ..." or "({}) => ..." for none; found "${first}".`,
    );
  }

  const names: string[] = [];
  for (const prop of splitTopLevel(first.slice(1, -1))) {
    if (prop.startsWith("...")) {
      throw new FixtureDeclarationError(
        `Rest property "${prop}" is not supported in a step's fixtures. List each fixture it uses.`,
      );
    }
    const key = prop.split(/[:=]/, 1)[0]!.trim();
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
 * The fixtures the steps of a scenario use: those declared by the macro Yadda would run for each of
 * `steps`, in first-seen order. Libraries that do not use fixtures mode contribute nothing.
 *
 * The winner is worked out the way Yadda does when it runs the steps one after another: the
 * closest pattern wins, and a tie goes to the library of the previous step's macro. Where it
 * cannot choose (an ambiguous step), every macro that matches counts, since the scenario then
 * fails when it runs and a superset is the safe side to err on. A step nothing defines adds nothing.
 */
export function collectScenarioFixtures(libraries: StepLibrary[], steps: string[]): string[] {
  const names = new Set<string>();
  // `rank_macros` is not in Yadda's typings; it is what the interpreter itself ranks steps with
  const interpreter = new Yadda.Interpreter(libraries as any) as any;

  for (const step of steps) {
    const report = interpreter.rank_macros(step).validate();
    // Moves the interpreter on, as running the step would, so the next step's tie goes the same way
    try {
      interpreter.validate([step]);
    } catch {
      // undefined or ambiguous; reported when the scenario runs
    }

    const macros: Macro[] = report.valid
      ? [report.winner]
      : libraries.flatMap((library) => library.find_compatible_macros(step) as Macro[]);

    for (const macro of macros) {
      for (const name of declaredFixtures.get(macro.library)?.get(macro.toString()) ?? []) {
        names.add(name);
      }
    }
  }
  return [...names];
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
