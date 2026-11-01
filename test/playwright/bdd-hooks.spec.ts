import { test as base } from "@playwright/test";
import {
  createHooks,
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
steps.given("a step that hangs", async () => {
  await new Promise(() => {});
});
steps.given("a step that fails", async () => {
  throw new Error("step failed");
});
const registry = createLibraryRegistry({ common: steps });

// Driven by test/playwright.test.ts. Each hook adds an annotation, so the report shows what ran
const hooks = createHooks();
const note = (info: { annotations: { type: string; description?: string }[] }, text: string) =>
  info.annotations.push({ type: "hook", description: text });
hooks.BeforeWorker(({ testInfo }) => note(testInfo, "BeforeWorker"));
hooks.AfterWorker(({ testInfo }) => void testInfo);
hooks.BeforeScenario(({ fixtures }) => note(fixtures.testInfo, "BeforeScenario 1"));
hooks.BeforeScenario({ tags: "@mobile" }, ({ fixtures }) =>
  note(fixtures.testInfo, "BeforeScenario mobile"),
);
hooks.AfterScenario(({ fixtures }) => note(fixtures.testInfo, "AfterScenario 1"));
hooks.AfterScenario(({ fixtures }) => note(fixtures.testInfo, "AfterScenario 2"));
hooks.BeforeStep(({ fixtures, step }) => note(fixtures.testInfo, `BeforeStep ${step}`));
hooks.AfterStep(({ fixtures, step, error }) =>
  note(fixtures.testInfo, `AfterStep ${step} ${error ? "failed" : "ok"}`),
);

defineFeature(
  parseFeature(`
Feature: Hooks
  @mobile
  Scenario: Tagged
    Given a step that passes

  @timeout:1500
  Scenario: Times out
    Given a step that hangs

  @fail
  Scenario: Fails
    Given a step that fails
`),
  { test, registry, hooks },
);
