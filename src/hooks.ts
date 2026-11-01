import { parseTagExpression, type TagExpression } from "./tag-expression.js";
import type { ParsedFeature, ParsedScenario } from "./types.js";

/** Which scenarios a hook applies to, and where it falls among the others */
export interface HookOptions {
  /** Tag expression, e.g. `'@mobile and not @slow'`. The hook applies to every scenario when absent. */
  tags?: string;
  /** Shown in the error when the hook fails */
  name?: string;
  /** Lower runs first, default 0; hooks with the same order run in registration order. After hooks run in reverse. */
  order?: number;
}

/** What a scenario hook is given */
export interface ScenarioHookArgs {
  feature: ParsedFeature;
  scenario: ParsedScenario;
  /** The scenario context the steps share */
  ctx: any;
  /** Playwright's fixtures for the scenario, with its `testInfo` */
  fixtures: Record<string, any>;
  /** The scenario's tags (its feature's too), as Playwright gets them: `@smoke`, `@timeout:5000` */
  tags: string[];
}

/** What a step hook is given */
export interface StepHookArgs extends ScenarioHookArgs {
  step: string;
  /** In `AfterStep` only: what the step threw, when it failed */
  error?: unknown;
}

/** What a worker hook is given */
export interface WorkerHookArgs {
  feature: ParsedFeature;
  /** The feature's tags */
  tags: string[];
  testInfo: any;
}

export type ScenarioHook = (args: ScenarioHookArgs) => Promise<void> | void;
export type StepHook = (args: StepHookArgs) => Promise<void> | void;
export type WorkerHook = (args: WorkerHookArgs) => Promise<void> | void;

type Register<F> = {
  (fn: F): void;
  (options: HookOptions, fn: F): void;
};

export type HookKind =
  | "BeforeScenario"
  | "AfterScenario"
  | "BeforeStep"
  | "AfterStep"
  | "BeforeWorker"
  | "AfterWorker";

interface Entry {
  kind: HookKind;
  fn: (args: any) => Promise<void> | void;
  name: string;
  order: number;
  seq: number;
  matches: TagExpression;
}

/**
 * Hooks for scenarios, steps and workers, each optionally limited to the scenarios a tag
 * expression selects. Pass it to `defineFeatureSuite` as `hooks`.
 */
export interface Hooks {
  BeforeScenario: Register<ScenarioHook>;
  AfterScenario: Register<ScenarioHook>;
  BeforeStep: Register<StepHook>;
  AfterStep: Register<StepHook>;
  /**
   * Runs in `test.beforeAll` of each feature that the feature's own tags select, so once per
   * worker per feature, not once per worker.
   */
  BeforeWorker: Register<WorkerHook>;
  /** As `BeforeWorker`, in `test.afterAll` */
  AfterWorker: Register<WorkerHook>;
  /** @internal The hooks of a kind that apply to `tags`, in the order they run. Used by `defineFeature`. */
  select(kind: HookKind, tags: readonly string[]): HookRunner[];
}

export interface HookRunner {
  name: string;
  run(args: any): Promise<void> | void;
}

/** Make a hook registry */
export function createHooks(): Hooks {
  const entries: Entry[] = [];
  let seq = 0;
  const perKind = new Map<HookKind, number>();

  const register =
    <F extends (args: any) => any>(kind: HookKind): Register<F> =>
    (a: HookOptions | F, b?: F) => {
      const options = typeof a === "function" ? {} : a;
      const fn = (typeof a === "function" ? a : b) as F | undefined;
      if (typeof fn !== "function") throw new TypeError(`${kind}: a hook function is required`);
      const matches = options.tags === undefined ? () => true : parseTagExpression(options.tags);
      entries.push({
        kind,
        fn,
        name:
          options.name ?? `${kind} #${perKind.set(kind, (perKind.get(kind) ?? 0) + 1).get(kind)}`,
        order: options.order ?? 0,
        seq: seq++,
        matches,
      });
    };

  return {
    BeforeScenario: register("BeforeScenario"),
    AfterScenario: register("AfterScenario"),
    BeforeStep: register("BeforeStep"),
    AfterStep: register("AfterStep"),
    BeforeWorker: register("BeforeWorker"),
    AfterWorker: register("AfterWorker"),
    select(kind, tags) {
      const chosen = entries
        .filter((e) => e.kind === kind && e.matches(tags))
        .sort((x, y) => x.order - y.order || x.seq - y.seq);
      if (kind.startsWith("After")) chosen.reverse();
      return chosen.map((e) => ({ name: e.name, run: e.fn }));
    },
  };
}

/** A hook error that is not the one reported, because another had already failed */
export function warnSuppressed(where: string, error: unknown): void {
  const reason = error instanceof Error ? error.message : String(error);
  console.warn(`playwright-yadda: ${where} failed too, and is not reported: ${reason}`);
}

/** Run every hook, even after one throws; throws the first error afterwards */
export async function runAll(
  hooks: HookRunner[],
  args: unknown,
  { stopOnError = false }: { stopOnError?: boolean } = {},
): Promise<void> {
  let failure: { error: unknown } | undefined;
  for (const hook of hooks) {
    try {
      await hook.run(args);
    } catch (error) {
      const given = (args as { error?: unknown } | undefined)?.error;
      // Not an error the hook was handed (AfterStep's `error`) and rethrew: that one is not its own
      if (error instanceof Error && error !== given && !error.message.includes(hook.name)) {
        try {
          error.message = `${hook.name} failed: ${error.message}`;
        } catch {
          // a read-only message (a DOMException, a frozen error): report it as it is
        }
      }
      if (failure && error !== given) warnSuppressed(hook.name, error);
      failure ??= { error };
      if (stopOnError) break;
    }
  }
  if (failure) throw failure.error;
}
