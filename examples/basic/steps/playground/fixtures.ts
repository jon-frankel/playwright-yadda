import { createBdd } from "playwright-yadda";
import { test } from "../test.js";

/**
 * Empty on purpose: "Create Yadda step definition" in `ide-playground/create-step.feature` writes into it.
 * A `createBdd` library is in fixtures mode, so a step's first parameter is its fixtures: `async ({}, qty) => …`.
 */
const { createStepLibrary } = createBdd(test);
export const playgroundFixtureSteps = createStepLibrary();
