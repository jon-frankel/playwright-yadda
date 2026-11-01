import { expect } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";
import type { ActualContext } from "./context.js";
import { indexOfOrdinal } from "./support/ordinal.js";

export const commonSteps = createStepLibrary();

commonSteps.given(
  /^the (Rules|Payees) page is open$/,
  async function (this: ActualContext, section: string) {
    await this.page.getByRole("link", { name: "Settings" }).click();
    await this.page
      .getByRole("navigation", { name: "Settings" })
      .getByRole("link", { name: section, exact: true })
      .click();
  },
);

commonSteps.when(
  /^the user opens the "([^"]+)" account$/,
  async function (this: ActualContext, account: string) {
    await this.page.getByRole("link", { name: new RegExp(`^${account}`) }).click();
  },
);

// Rules and payees are both lists with the same filter box and row behaviour.
commonSteps.when(
  /^the user filters the list for "([^"]+)"$/,
  async function (this: ActualContext, text: string) {
    await this.page.getByPlaceholder(/^Filter /).fill(text);
  },
);

commonSteps.when(
  /^the user right-clicks the (\w+) row$/,
  async function (this: ActualContext, ordinal: string) {
    await this.page
      .getByTestId("table")
      .getByTestId("row")
      .nth(indexOfOrdinal(ordinal))
      .click({ button: "right" });
  },
);

commonSteps.then(/^"([^"]+)" is shown$/, async function (this: ActualContext, text: string) {
  await expect(this.page.getByText(text).first()).toBeVisible();
});

commonSteps.then(
  /^a context menu offers "([^"]+)"$/,
  async function (this: ActualContext, option: string) {
    const menu = this.page.getByRole("menu");
    await expect(menu).toBeVisible();
    await expect(menu.getByRole("button", { name: option })).toBeVisible();
  },
);
