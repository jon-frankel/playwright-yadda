import { expect } from "@playwright/test";
import { createBdd } from "playwright-yadda";
import { test } from "./test.js";

// Steps take the scenario's fixtures as their first argument, typed from `test`
const { createStepLibrary } = createBdd(test);

export const inventorySteps = createStepLibrary();

inventorySteps.given(
  /^the shop stocks (\d+) "(.*)"$/,
  async ({ stock }, qty: string, item: string) => {
    stock.set(item, Number(qty));
  },
);

inventorySteps.when(
  /^a customer buys (\d+) "(.*)"$/,
  async ({ stock }, qty: string, item: string) => {
    stock.set(item, (stock.get(item) ?? 0) - Number(qty));
  },
);

inventorySteps.then(/^"(.*)" has (\d+) left$/, async ({ stock }, item: string, left: string) => {
  expect(stock.get(item)).toBe(Number(left));
});

// Built-in fixtures and the test's `TestInfo` (as `$testInfo`) are there too
inventorySteps.then(
  "the scenario runs in a browser project",
  async ({ browserName, $testInfo }) => {
    expect(browserName).toBeTruthy();
    expect($testInfo.title).toContain("Scenario:");
  },
);
