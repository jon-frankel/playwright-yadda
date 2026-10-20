import { createStepLibrary } from "playwright-yadda";

export const commonSteps = createStepLibrary();

commonSteps.given("the test session is active", function (this: any) {
  this.sessionActive = true;
});
