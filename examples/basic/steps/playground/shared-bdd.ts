import { createBdd } from "playwright-yadda";
import { test } from "../test.js";

/** The `createStepLibrary` of `createBdd(test)`, shared by the libraries of several files. */
export const { createStepLibrary } = createBdd(test);
