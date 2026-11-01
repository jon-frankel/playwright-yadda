import { expect } from "@playwright/test";
import { createHooks, createStepLibrary } from "playwright-yadda";

/**
 * Hooks for features/hooks.feature. They are registered on the suite (see e2e/features.spec.ts) and
 * each is limited by a tag expression, so no other feature in this example is touched by them.
 */
export const hooks = createHooks();

/** What the hooks leave behind for the steps to check: stands in for real setup and teardown */
export const record = { workerSetUps: 0, cleanedUp: [] as string[] };

// Per scenario, only for the scenarios that ask. `ctx` is what the steps see as `this`.
hooks.BeforeScenario({ tags: "@eur", name: "currency" }, ({ ctx }) => {
  ctx.currency = "EUR";
});

// Per step. BeforeStep runs before a step and AfterStep after it, with the step's error if it failed.
hooks.BeforeScenario({ tags: "@audit" }, ({ ctx }) => {
  ctx.audit = [];
});
hooks.AfterStep({ tags: "@audit" }, ({ ctx, step, error }) => {
  ctx.audit.push(`${error ? "failed" : "passed"}: ${step}`);
});

// AfterScenario runs although the scenario fails, which makes it the place for cleanup. `order`
// sorts hooks, lowest first; After hooks run in the reverse order, so this one runs last.
hooks.AfterScenario({ tags: "@cleanup", name: "cleanup", order: -1 }, ({ scenario }) => {
  record.cleanedUp.push(scenario.title);
});

// Per feature and worker, selected by the feature's own tags
hooks.BeforeWorker({ tags: "@worker-setup" }, () => {
  record.workerSetUps += 1;
});
hooks.AfterWorker({ tags: "@worker-setup" }, () => {
  record.workerSetUps = 0;
});

export const hookSteps = createStepLibrary();

hookSteps.given(/^the shop currency is (\w+)$/, function (this: any, currency: string) {
  expect(this.currency).toBe(currency);
});

hookSteps.given("the shop currency is not set", function (this: any) {
  expect(this.currency).toBeUndefined();
});

hookSteps.given("a step that passes", function () {});

hookSteps.then(
  /^the audit trail shows (\d+) steps? before this one$/,
  function (this: any, n: string) {
    expect(this.audit).toHaveLength(Number(n));
    expect(this.audit.every((entry: string) => entry.startsWith("passed: "))).toBe(true);
  },
);

hookSteps.given("a step that fails on purpose", function () {
  throw new Error("This step fails on purpose");
});

hookSteps.then(/^the cleanup hook has run for "(.+)"$/, function (title: string) {
  expect(record.cleanedUp).toContain(title);
});

hookSteps.then("the worker hook has run once for this feature", function () {
  expect(record.workerSetUps).toBe(1);
});
