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

// What @fail, @slow and @timeout do has to reach the hooks too, which Playwright runs before the test
// oxlint-disable-next-line no-empty-pattern
test.beforeEach(async ({}, info) => {
  if (info.title.includes("failing hook")) throw new Error("the hook fails");
  if (info.title.includes("slow hook")) await new Promise((resolve) => setTimeout(resolve, 900));
});

const steps = createStepLibrary();
steps.given("a step that passes", async () => {});
const registry = createLibraryRegistry({ common: steps });

// Driven by test/playwright.test.ts, which checks how each scenario ends
defineFeature(
  parseFeature(`
@timeout:500
Feature: Hooks
  @fail
  Scenario: Expected to fail, in a failing hook
    Given a step that passes

  @slow
  Scenario: Slow, in a slow hook
    Given a step that passes

  @slow
  @timeout:600
  Scenario: Slow with its own timeout, in a slow hook
    Given a step that passes

  Scenario: Over the feature's timeout, in a slow hook
    Given a step that passes
`),
  { test, registry },
);
