import Yadda from "yadda";
import type { StepLibrary } from "./types.js";

/** Where a step is written, for Playwright to show in its reports and trace viewer */
export interface StepLocation {
  file: string;
  line: number;
  column: number;
}

export type TestStepWrapper = <T>(
  title: string,
  body: () => Promise<T>,
  options?: { location?: StepLocation },
) => Promise<T>;

let activeExecutionContext: Record<string, unknown> | null = null;

// Listen to Yadda's EventBus to capture the active macro execution context (`this`)
Yadda.EventBus.instance().on(Yadda.EventBus.ON_EXECUTE, (event: any) => {
  if (event?.data?.ctx) {
    activeExecutionContext = event.data.ctx;
  }
});

/**
 * Creates an isolated Yadda runner instance with only the resolved libraries
 */
export function createScenarioYadda(libraries: StepLibrary[]): Yadda.Yadda {
  return Yadda.createInstance(libraries);
}

/**
 * Promisified step execution wrapper around yadda.run()
 */
export function runStep(yadda: Yadda.Yadda, stepText: string, context: unknown): Promise<void> {
  return new Promise((resolve, reject) => {
    try {
      activeExecutionContext = null;
      yadda.run(stepText, context as any, (err?: any) => {
        if (err) return reject(err);

        // Sync back any properties set on `this` during step execution to the shared scenario context
        if (activeExecutionContext && typeof context === "object" && context !== null) {
          for (const [key, value] of Object.entries(activeExecutionContext)) {
            if (key !== "step") {
              (context as Record<string, unknown>)[key] = value;
            }
          }
        }

        resolve();
      });
    } catch (err) {
      reject(err);
    }
  });
}

/**
 * Execute an array of scenario steps, optionally wrapping each step with a Playwright `test.step`
 * function. With `locations` (one per step, in order) the wrapper is told where each step is
 * written, so Playwright can show the feature file's line instead of this one's.
 */
export async function runScenarioSteps(
  yadda: Yadda.Yadda,
  steps: string[],
  context: unknown,
  testStep?: TestStepWrapper,
  stepHooks?: StepHooks,
  /** Asked before each step: true once the test is over, so that no further step starts */
  stopped?: () => boolean,
  locations?: readonly (StepLocation | undefined)[],
): Promise<void> {
  const execute = async (stepText: string) => {
    if (!stepHooks) return runStep(yadda, stepText, context);
    // A failing `before` hook fails the step; `after` runs either way and sees the error
    let error: unknown;
    let failed = false;
    try {
      await stepHooks.before(stepText);
      if (stopped?.()) return;
      await runStep(yadda, stepText, context);
    } catch (e) {
      failed = true;
      error = e;
    }
    await stepHooks.after(stepText, failed ? error : undefined);
    if (failed) throw error;
  };
  for (const [index, stepText] of steps.entries()) {
    if (stopped?.()) return;
    if (testStep) {
      const location = locations?.[index];
      // Only given when there is one, as a wrapper of someone's own may not expect a third argument
      await (location
        ? testStep(stepText, () => execute(stepText), { location })
        : testStep(stepText, () => execute(stepText)));
    } else {
      await execute(stepText);
    }
  }
}

/** Called around each step: `after` is given what the step threw, if it failed */
export interface StepHooks {
  before(step: string): Promise<void>;
  after(step: string, error?: unknown): Promise<void>;
}
