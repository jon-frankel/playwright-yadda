import { test as base, expect } from "@playwright/test";
import {
  createBdd,
  createLibraryRegistry,
  defineFeatureSuite,
  parseFeature,
} from "../../src/index.ts";

/** Every fixture set-up is recorded on the test, where the JSON report shows it */
const recordSetUp = (
  name: string,
  info: { annotations: { type: string; description?: string }[] },
) => info.annotations.push({ type: "set-up", description: name });

const test = base.extend<{ account: string; expensive: string }, { session: string }>({
  // no browser: the suite always asks for these two, so they are replaced
  // oxlint-disable-next-line no-empty-pattern
  page: async ({}, use) => use({} as never),
  // oxlint-disable-next-line no-empty-pattern
  context: async ({}, use) => use({} as never),
  // oxlint-disable-next-line no-empty-pattern
  account: async ({}, use, info) => {
    recordSetUp("account", info);
    await use("alice");
  },
  // oxlint-disable-next-line no-empty-pattern
  expensive: async ({}, use, info) => {
    recordSetUp("expensive", info);
    await use("never used");
  },
  // oxlint-disable-next-line no-empty-pattern
  session: [async ({}, use) => use("worker-wide"), { scope: "worker" }],
});

const { createStepLibrary } = createBdd(test);
const steps = createStepLibrary();
steps.given("a signed-in user", async ({ account }) => {
  expect(account).toBe("alice");
});
steps.given("the browser is known", async ({ browserName, $testInfo }) => {
  expect(browserName).toBeTruthy();
  expect($testInfo.title).toContain("Scenario:");
});
steps.then("the worker session is shared", async ({ session }) => {
  expect(session).toBe("worker-wide");
});
steps.then("an expensive thing is checked", async ({ expensive }) => {
  expect(expensive).toBe("never used");
});
// oxlint-disable-next-line no-empty-pattern
steps.given("nothing custom is needed", async ({}) => {});

const feature = parseFeature(`@Libraries=steps
Feature: Fixtures
  Scenario: Uses a custom fixture
    Given a signed-in user
    And the browser is known

  Scenario: Uses a worker fixture
    Then the worker session is shared

  Scenario: Uses no custom fixture
    Given nothing custom is needed

  Scenario: Uses another custom fixture
    Then an expensive thing is checked
`);

defineFeatureSuite({
  test,
  registry: createLibraryRegistry({ steps }, { defaultLibraries: [] }),
  features: [feature],
});
