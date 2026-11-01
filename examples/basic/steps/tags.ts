import { test } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";

/** Steps for features/tags-*.feature, which exercise the special tags */
export const tagsSteps = createStepLibrary();

tagsSteps.given("a step that passes", function () {});

tagsSteps.given("a step that fails", function () {
  throw new Error("This step fails on purpose");
});

tagsSteps.given(/^a step that takes (\d+) ms$/, async function (this: any, ms: string) {
  await new Promise((resolve) => setTimeout(resolve, Number(ms)));
});

// Fails on the first attempt and passes on the retry, so it needs `@retries:1` or more
tagsSteps.given("a step that passes on the second attempt", function () {
  if (test.info().retry === 0) throw new Error("First attempt fails on purpose");
});
