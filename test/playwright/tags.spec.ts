import { test as base } from "@playwright/test";
import {
  createLibraryRegistry,
  createStepLibrary,
  defineFeature,
  parseFeature,
} from "../../src/index.ts";

// no browser: the suite always asks for these two, so they are replaced
const test = base.extend({
  // oxlint-disable-next-line no-empty-pattern
  page: async ({}, use) => use({} as never),
  // oxlint-disable-next-line no-empty-pattern
  context: async ({}, use) => use({} as never),
});

const steps = createStepLibrary();
steps.given("a step that passes", async () => {});
steps.given("a step that fails", async () => {
  throw new Error("failed on purpose");
});
steps.given("a step that waits", async () => {
  await new Promise((resolve) => setTimeout(resolve, 1500));
});
steps.given("a step that fails on the first attempt", async function () {
  if (this.testInfo.retry === 0) throw new Error("the first attempt fails");
});
const registry = createLibraryRegistry({ common: steps });

// Driven by test/playwright.test.ts, which checks how each scenario ends
defineFeature(
  parseFeature(`
@smoke
@retries:1
Feature: Special tags
  @fail
  Scenario: Expected to fail
    Given a step that fails

  @fixme
  Scenario: Fixme
    Given a step that fails

  @skip
  Scenario: Skipped
    Given a step that fails

  @pending
  Scenario: Pending
    Given a step that fails

  @slow
  Scenario: Slow
    Given a step that passes

  Scenario: Plain
    Given a step that passes

  @timeout:500
  Scenario: Times out
    Given a step that waits

  @retries:0
  Scenario: Retries nothing
    Given a step that fails

  Scenario: Retried once, from the feature
    Given a step that fails on the first attempt

  @unknown-tag
  Scenario: Unknown tags change nothing
    Given a step that passes
`),
  { test, registry },
);

defineFeature(
  parseFeature(`
@timeout:500
@mode:serial
Feature: Feature settings
  @timeout:5000
  Scenario: Overrides the feature's timeout
    Given a step that waits

  Scenario: Times out from the feature's tag
    Given a step that waits

  Scenario: Is skipped, as serial mode skips what follows a failure
    Given a step that passes
`),
  { test, registry },
);
