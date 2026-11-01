import Yadda from "yadda";
import type { StepLibrary } from "./types.js";

export type TestStepWrapper = <T>(title: string, body: () => Promise<T>) => Promise<T>;

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
 * Execute an array of scenario steps, optionally wrapping each step with a Playwright `test.step` function
 */
export async function runScenarioSteps(
  yadda: Yadda.Yadda,
  steps: string[],
  context: unknown,
  testStep?: TestStepWrapper,
  stepHooks?: StepHooks,
  /** Asked before each step: true once the test is over, so that no further step starts */
  stopped?: () => boolean,
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
  for (const stepText of steps) {
    if (stopped?.()) return;
    if (testStep) {
      await testStep(stepText, () => execute(stepText));
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
