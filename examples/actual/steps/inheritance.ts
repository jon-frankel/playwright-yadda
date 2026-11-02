import { expect } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";
import type { ActualContext } from "./context.js";

// Two tiny libraries that define the same step text, for overrides/inheritance.feature: which one
// a scenario gets shows whether its @Libraries replaced its feature's or were added to them.

function inheritanceLibrary(name: string) {
  const steps = createStepLibrary();
  steps.given(/^the library in use is reported$/, function (this: ActualContext) {
    this.reported = name;
  });
  steps.then(
    /^the reported library is "([^"]+)"$/,
    function (this: ActualContext, expected: string) {
      expect(this.reported).toBe(expected);
    },
  );
  return steps;
}

export const inheritanceASteps = inheritanceLibrary("A");
export const inheritanceBSteps = inheritanceLibrary("B");
