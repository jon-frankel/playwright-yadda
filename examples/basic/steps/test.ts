import { test as base } from "@playwright/test";

/** Stock levels by product, standing in for something a real suite would set up and tear down */
export type Stock = Map<string, number>;

/**
 * The suite's `test`, extended with a custom fixture. Steps destructure it like a Playwright test
 * does; a scenario only sets it up when one of its steps asks for it.
 */
export const test = base.extend<{ stock: Stock }>({
  // Playwright needs the destructuring pattern, even when empty
  // oxlint-disable-next-line no-empty-pattern
  stock: async ({}, use) => {
    await use(new Map([["Mechanical Keyboard", 3]]));
  },
});
