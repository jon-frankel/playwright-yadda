import { expect } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";
import type { ActualContext } from "./context.js";
import { openDemoBudget } from "./support/demo-budget.js";
import { indexOfOrdinal } from "./support/ordinal.js";

/** Steps for Actual's narrow layout. The `@mobile` hook in hooks.ts sets the phone viewport first. */
// `param` mode: the scenario context is the first argument, instead of `this`.
export const mobileSteps = createStepLibrary(undefined, "param");

// Not the `budget` fixture: that one is set up before the hooks, so before the viewport is resized.
mobileSteps.given("the demo budget is open on a phone", async (ctx: ActualContext) => {
  await openDemoBudget(ctx.page);
  await ctx.page.locator("[data-navbar-state]").waitFor();
});

const navbar = (ctx: ActualContext) => ctx.page.locator("[data-navbar-state]");

// The navbar slides with react-spring, so a click through the DOM is steadier than a pointer click.
mobileSteps.when(
  /^the user goes to the (Accounts|Settings) page$/,
  async (ctx: ActualContext, name: string) => {
    await navbar(ctx)
      .getByRole("link", { name })
      .evaluate((el) => (el as HTMLElement).click());
    await ctx.page.waitForURL(name === "Accounts" ? /\/accounts$/ : /\/settings$/);
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
  async (ctx: ActualContext, section: string) => {
    await openSettingsSection(ctx, section);
  },
);

mobileSteps.when(
  /^the user opens the (\w+) account$/,
  async (ctx: ActualContext, ordinal: string) => {
    await ctx.page
      .getByLabel("Account list")
      .getByTestId("account-list-item")
      .nth(indexOfOrdinal(ordinal))
      .click();
    await ctx.page.getByLabel("Transaction list").waitFor();
  },
);

mobileSteps.when(
  /^the user opens the (\w+) (payee|rule)$/,
  async (ctx: ActualContext, ordinal: string, kind: string) => {
    const items =
      kind === "payee"
        ? ctx.page.getByRole("grid", { name: "Payees" }).getByRole("gridcell")
        : ctx.page.getByRole("grid", { name: "Rules" }).getByRole("row");
    await items.nth(indexOfOrdinal(ordinal)).click();
  },
);

mobileSteps.when(/^the user searches for "([^"]+)"$/, async (ctx: ActualContext, text: string) => {
  await ctx.page.getByPlaceholder(/^(Search|Filter)/).fill(text);
});

mobileSteps.when("the user clears the search", async (ctx: ActualContext) => {
  await ctx.page.getByPlaceholder(/^(Search|Filter)/).fill("");
});

mobileSteps.when("the user exports the data", async (ctx: ActualContext) => {
  await openSettingsSection(ctx, "General");
  const download = ctx.page.waitForEvent("download");
  await ctx.page.getByTestId("settings").getByRole("button", { name: "Export data" }).click();
  ctx.download = await download;
});

mobileSteps.then(
  /^the (\w+) account is "([^"]+)" with a balance of ([\d,.]+)$/,
  async (ctx: ActualContext, ordinal: string, name: string, balance: string) => {
    const row = ctx.page
      .getByLabel("Account list")
      .getByTestId("account-list-item")
      .nth(indexOfOrdinal(ordinal));
    await expect(row.getByTestId("account-name")).toHaveText(name);
    await expect(row.getByTestId("account-balance")).toHaveText(balance);
  },
);

mobileSteps.then(/^the page heading is "([^"]+)"$/, async (ctx: ActualContext, text) => {
  await expect(ctx.page.getByRole("heading").first()).toHaveText(text as string);
});

mobileSteps.then("the account lists transactions", async (ctx: ActualContext) => {
  await expect(ctx.page.getByLabel("Transaction list").getByRole("button").first()).toBeVisible();
});

mobileSteps.then(
  /^the account lists transactions mentioning "([^"]+)"$/,
  async (ctx: ActualContext, text: string) => {
    const list = ctx.page.getByLabel("Transaction list");
    await expect(list.getByRole("button").first()).toBeVisible();
    await expect(list.getByRole("button").filter({ hasText: text }).first()).toBeVisible();
  },
);

const payeeCells = (ctx: ActualContext) =>
  ctx.page.getByRole("grid", { name: "Payees" }).getByRole("gridcell");

// Remembers the unfiltered count, so a later "not shown" check has a rendered list to compare to.
mobileSteps.given("the payees list has loaded", async (ctx: ActualContext) => {
  await payeeCells(ctx).first().waitFor();
  ctx.payeeCount = await payeeCells(ctx).count();
});

mobileSteps.then("only some of the payees are listed", async (ctx: ActualContext) => {
  const total = ctx.payeeCount as number;
  await expect(payeeCells(ctx).first()).toBeVisible();
  await expect.poll(() => payeeCells(ctx).count()).toBeLessThan(total);
});

mobileSteps.then(/^"([^"]+)" is not shown$/, async (ctx: ActualContext, text: string) => {
  await expect(ctx.page.getByText(text)).toHaveCount(0);
});

mobileSteps.then("a zip named after today's date is downloaded", async (ctx: ActualContext) => {
  const download = ctx.download as import("@playwright/test").Download;
  // Actual names it `yyyy-MM-dd-<budget>.zip`, in local time.
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  const today = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  expect(download.suggestedFilename()).toMatch(new RegExp(`^${today}-.*\\.zip$`));
});
