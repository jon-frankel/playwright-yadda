import { createStepLibrary } from "playwright-yadda";

/** Empty on purpose, for `ide-playground/create-step-modes.feature`: param mode, where the context is the first argument. */
export const playgroundParamSteps = createStepLibrary(undefined, "param");
