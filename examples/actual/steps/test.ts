import { test as base } from "@playwright/test";
import { disableAnimations } from "./support/animations.js";

/**
 * The suite's `test`, extended with a fixture for what every scenario starts from. Steps destructure
 * it like a Playwright test does, and a scenario only sets it up when one of its steps asks for it.
 */
export const test = base.extend<{ budget: void }>({
  // Opens Actual's demo budget in the scenario's own browser context.
  budget: async ({ page }, use) => {
    await disableAnimations(page);
    await page.goto("/");
    await page.getByRole("button", { name: "Try the demo", exact: true }).click();
    await page.getByTestId("budget-table").waitFor();
    await use();
  },
});
