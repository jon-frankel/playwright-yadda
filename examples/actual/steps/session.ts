import { createBdd } from "playwright-yadda";
import { test } from "./test.js";

const { createStepLibrary } = createBdd(test);

/** Steps that take Playwright fixtures instead of the scenario context: the `fixtures` mode. */
export const sessionSteps = createStepLibrary();

// Destructuring the fixture is the step: Playwright sets it up before the scenario runs.
sessionSteps.given("the demo budget is open", async ({ budget: _budget }) => {});
