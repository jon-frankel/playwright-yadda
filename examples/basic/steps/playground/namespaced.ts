import * as yadda from "playwright-yadda";

/** Empty on purpose, for `ide-playground/create-step-modes.feature`: fixtures mode, set by the argument, through a namespace import. */
export const playgroundNamespacedSteps = yadda.createStepLibrary(undefined, "fixtures");
