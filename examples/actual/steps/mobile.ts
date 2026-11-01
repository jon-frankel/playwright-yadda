import { expect } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";
import type { ActualContext } from "./context.js";
import { openDemoBudget } from "./support/demo-budget.js";
import { indexOfOrdinal } from "./support/ordinal.js";

/** Steps for Actual's narrow layout. The `@mobile` hook in hooks.ts sets the phone viewport first. */
export const mobileSteps = createStepLibrary();

// Not the `budget` fixture: that one is set up before the hooks, so before the viewport is resized.
mobileSteps.given("the demo budget is open on a phone", async function (this: ActualContext) {
  await openDemoBudget(this.page);
  await this.page.locator("[data-navbar-state]").waitFor();
});

const navbar = (ctx: ActualContext) => ctx.page.locator("[data-navbar-state]");

// The navbar slides with react-spring, so a click through the DOM is steadier than a pointer click.
mobileSteps.when(
  /^the user goes to the (Accounts|Settings) page$/,
  async function (this: ActualContext, name: string) {
    await navbar(this)
      .getByRole("link", { name })
      .evaluate((el) => (el as HTMLElement).click());
    await this.page.waitForURL(name === "Accounts" ? /\/accounts$/ : /\/settings$/);
  },
);

async function openSettingsSection(ctx: ActualContext, section: string) {
  await ctx.page.getByTestId("settings").waitFor();
  const link = ctx.page
    .getByRole("navigation", { name: "Settings" })
    .getByRole("link", { name: section, exact: true });
  // On a narrow screen the settings menu is only on the landing page.
  if (!(await link.isVisible())) await ctx.page.getByRole("button", { name: "Back" }).click();
  await link.click();
}

mobileSteps.when(
  /^the user opens the (Payees|Rules) settings$/,
  async function (this: ActualContext, section: string) {
    await openSettingsSection(this, section);
  },
);

mobileSteps.when(
  /^the user opens the (\w+) account$/,
  async function (this: ActualContext, ordinal: string) {
    await this.page
      .getByLabel("Account list")
      .getByTestId("account-list-item")
      .nth(indexOfOrdinal(ordinal))
      .click();
    await this.page.getByLabel("Transaction list").waitFor();
  },
);

mobileSteps.when(
  /^the user opens the (\w+) (payee|rule)$/,
  async function (this: ActualContext, ordinal: string, kind: string) {
    const items =
      kind === "payee"
        ? this.page.getByRole("grid", { name: "Payees" }).getByRole("gridcell")
        : this.page.getByRole("grid", { name: "Rules" }).getByRole("row");
    await items.nth(indexOfOrdinal(ordinal)).click();
  },
);

mobileSteps.when(
  /^the user searches for "([^"]+)"$/,
  async function (this: ActualContext, text: string) {
    await this.page.getByPlaceholder(/^(Search|Filter)/).fill(text);
  },
);

mobileSteps.when("the user clears the search", async function (this: ActualContext) {
  await this.page.getByPlaceholder(/^(Search|Filter)/).fill("");
});

mobileSteps.when("the user exports the data", async function (this: ActualContext) {
  await openSettingsSection(this, "General");
  const download = this.page.waitForEvent("download");
  await this.page.getByTestId("settings").getByRole("button", { name: "Export data" }).click();
  this.download = await download;
});

mobileSteps.then(
  /^the (\w+) account is "([^"]+)" with a balance of ([\d,.]+)$/,
  async function (this: ActualContext, ordinal: string, name: string, balance: string) {
    const row = this.page
      .getByLabel("Account list")
      .getByTestId("account-list-item")
      .nth(indexOfOrdinal(ordinal));
    await expect(row.getByTestId("account-name")).toHaveText(name);
    await expect(row.getByTestId("account-balance")).toHaveText(balance);
  },
);

mobileSteps.then(/^the page heading is "([^"]+)"$/, async function (this: ActualContext, text) {
  await expect(this.page.getByRole("heading").first()).toHaveText(text as string);
});

mobileSteps.then("the account lists transactions", async function (this: ActualContext) {
  await expect(this.page.getByLabel("Transaction list").getByRole("button").first()).toBeVisible();
});

mobileSteps.then(/^"([^"]+)" is not shown$/, async function (this: ActualContext, text: string) {
  await expect(this.page.getByText(text)).toHaveCount(0);
});

mobileSteps.then(
  "a zip named after today's date is downloaded",
  async function (this: ActualContext) {
    const download = this.download as import("@playwright/test").Download;
    expect(download.suggestedFilename()).toMatch(/^\d{4}-\d{2}-\d{2}-.*\.zip$/);
  },
);
