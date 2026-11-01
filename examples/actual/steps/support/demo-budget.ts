import type { Page } from "@playwright/test";
import { disableAnimations } from "./animations.js";

/** Opens Actual's demo budget in the page, at whatever viewport size the page has by now. */
export async function openDemoBudget(page: Page): Promise<void> {
  await disableAnimations(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Try the demo", exact: true }).click();
  await page.getByTestId("budget-table").or(page.locator("[data-navbar-state]")).first().waitFor();
}
