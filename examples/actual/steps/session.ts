import { createBdd } from "playwright-yadda";
import { test } from "./test.js";

const { createStepLibrary } = createBdd(test);

/** Steps that take Playwright fixtures instead of the scenario context: the `fixtures` mode. */
export const sessionSteps = createStepLibrary();

sessionSteps.given("the demo budget is open", async ({ budget }) => {
  void budget; // setting the fixture up is the step
});
