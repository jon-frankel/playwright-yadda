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
steps.given("a step that waits", async () => {
  await new Promise((resolve) => setTimeout(resolve, 300));
});
const registry = createLibraryRegistry({ common: steps });

// Driven by test/playwright.test.ts, which counts how often the warning about the scenario's
// @mode is printed. The scenarios run in parallel, so several workers load this file.
defineFeature(
  parseFeature(`
@mode:parallel
Feature: Warnings
  @mode:serial
  Scenario: Sets a mode of its own
    Given a step that waits

  Scenario: Clean one
    Given a step that waits

  Scenario: Clean two
    Given a step that waits

  Scenario: Clean three
    Given a step that waits

  Scenario: Clean four
    Given a step that waits

  Scenario: Clean five
    Given a step that waits
`),
  { test, registry, defaultLibraries: ["common"] },
);
