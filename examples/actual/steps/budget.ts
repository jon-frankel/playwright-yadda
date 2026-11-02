import { expect } from "@playwright/test";
import type { Locator, Page } from "@playwright/test";
import { createBdd } from "playwright-yadda";
import { test } from "./test.js";
import { indexOfOrdinal } from "./support/ordinal.js";

const { createStepLibrary } = createBdd(test);

export const budgetingSteps = createStepLibrary();

const table = (page: Page) => page.getByTestId("budget-table");
const categoryRow = (page: Page, ordinal: string): Locator =>
  table(page).getByTestId("row").nth(indexOfOrdinal(ordinal));

/** Money as whole cents, from the text of a balance cell. */
async function balanceOf(row: Locator): Promise<number> {
  const text = await row.getByTestId("balance").textContent();
  const cents = Math.round(parseFloat((text ?? "").replace(/,/g, "")) * 100);
  // An empty cell is NaN, and NaN equals NaN: it must not read as a balance.
  if (Number.isNaN(cents)) throw new Error(`No balance in the cell: "${text}"`);
  return cents;
}

budgetingSteps.then(/^the budget summary shows "([^"]+)"$/, async ({ page }, label: string) => {
  await expect(page.getByTestId("budget-summary").first().getByText(label)).toBeVisible({
    timeout: 10_000,
  });
});

budgetingSteps.then("the budget table totals are numbers", async ({ page }) => {
  const totals = table(page).getByTestId("budget-totals");
  for (const total of ["total-budgeted", "total-spent", "total-leftover"]) {
    const text = await totals.getByTestId(new RegExp(`${total}$`)).textContent();
    expect(Number.isNaN(parseInt(text ?? "", 10)), `${total} is "${text}"`).toBe(false);
  }
});

budgetingSteps.when(
  /^the user transfers the whole balance of the (\w+) category to the (\w+)$/,
  async function (this: { balances?: Record<string, number> }, { page }, from: string, to: string) {
    const target = categoryRow(page, to);
    const name = (await target.getByTestId("category-name").textContent()) ?? "";
    this.balances = {
      from: await balanceOf(categoryRow(page, from)),
      to: await balanceOf(target),
    };
    await categoryRow(page, from)
      .getByTestId("balance")
      .getByTestId(/^budget/)
      .click();
    await page.getByRole("button", { name: "Transfer to another category" }).click();
    await page.getByPlaceholder("(none)").click();
    await page.keyboard.type(name);
    await page.keyboard.press("Enter");
    await page.getByRole("button", { name: "Transfer" }).click();
  },
);

budgetingSteps.then(
  /^the (\w+) category's balance is what both categories held$/,
  async function (this: { balances?: Record<string, number> }, { page }, ordinal: string) {
    const { from, to } = this.balances ?? {};
    if (from === undefined || to === undefined)
      throw new Error("The balances were not noted first");
    await expect.poll(() => balanceOf(categoryRow(page, ordinal))).toEqual(from + to);
  },
);

budgetingSteps.when(
  /^the user clicks the spent amount of the (\w+) category$/,
  async ({ page }, ordinal: string) => {
    await categoryRow(page, ordinal).getByTestId("category-month-spent").click();
  },
);

budgetingSteps.then(/^the transactions of "([^"]+)" are shown$/, async ({ page }, name: string) => {
  await expect(page).toHaveURL(/\/accounts/);
  await expect(page.getByTestId("account-name")).toContainText(name);
});

budgetingSteps.when("the user goes back to the budget", async ({ page }) => {
  await page.getByRole("button", { name: "Back" }).click();
  await table(page).waitFor();
});

// Right-clicking: three places in the budget, one step.
budgetingSteps.when(
  /^the user opens the context menu of (the \w+ category|the "[^"]+" category group|the budget name)$/,
  async ({ page }, target: string) => {
    const [, ordinal] = target.match(/^the (\w+) category$/) ?? [];
    const [, group] = target.match(/^the "([^"]+)" category group$/) ?? [];
    const place = ordinal
      ? categoryRow(page, ordinal).getByTestId("category-name")
      : group
        ? table(page).getByText(group, { exact: true })
        : page.getByTestId("budget-name");
    await place.click({ button: "right" });
  },
);

// Scroll position

budgetingSteps.given(/^the budget has (\d+) extra categories$/, async ({ page }, count: string) => {
  await page.evaluate(async (n) => {
    type Send = (type: string, args?: unknown) => Promise<string>;
    const w = window as unknown as {
      $send: Send;
      __TANSTACK_QUERY_CLIENT__: { invalidateQueries(f: unknown): Promise<void> };
    };
    const groupId = await w.$send("category-group-create", { name: "Extra Categories" });
    for (let i = 1; i <= n; i++)
      await w.$send("category-create", { name: `Category ${i}`, groupId });
    await w.__TANSTACK_QUERY_CLIENT__.invalidateQueries({ queryKey: ["categories", "lists"] });
  }, Number(count));
  await page.getByText(`Category ${count}`, { exact: true }).waitFor({ state: "visible" });
});

const scroller = (page: Page) => page.getByTestId("budget-table-scroll-container");

budgetingSteps.when(
  "the user scrolls to the bottom of the budget",
  async function (this: { scrollTop?: number }, { page }) {
    await scroller(page).evaluate((el) => {
      el.scrollTop = el.scrollHeight;
    });
    this.scrollTop = await scroller(page).evaluate((el) => el.scrollTop);
    expect(this.scrollTop).toBeGreaterThan(0);
  },
);

// Clicks without Playwright's auto-scroll, which would move the list before the app remembers where it was.
budgetingSteps.when("the user clicks the last spent amount in view", async ({ page }) => {
  const clicked = await page.evaluate(() => {
    const container = document.querySelector('[data-testid="budget-table-scroll-container"]');
    if (!container) return false;
    const box = container.getBoundingClientRect();
    const cells = [
      ...container.querySelectorAll<HTMLElement>('[data-testid="category-month-spent"]'),
    ].reverse();
    const cell = cells.find((c) => {
      const r = c.getBoundingClientRect();
      return r.top >= box.top && r.bottom <= box.bottom;
    });
    cell?.click();
    return Boolean(cell);
  });
  expect(clicked, "a spent amount in view").toBe(true);
});

budgetingSteps.then(
  "the budget is scrolled to where it was",
  async function (this: { scrollTop?: number }, { page }) {
    expect(await scroller(page).evaluate((el) => el.scrollTop)).toBe(this.scrollTop);
  },
);
