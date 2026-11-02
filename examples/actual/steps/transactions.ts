import { expect, type Locator } from "@playwright/test";
import type { Page } from "@playwright/test";
import { createBdd } from "playwright-yadda";
import { test } from "./test.js";
import { parseTable, requireBlock } from "./support/blocks.js";
import { indexOfOrdinal } from "./support/ordinal.js";
import {
  fillTransactionRow,
  finishEntry,
  openEntryForm,
  type TransactionEntry,
} from "./support/transaction-form.js";

const { createStepLibrary } = createBdd(test);

export const transactionSteps = createStepLibrary();

/** Opens a cell of a transaction row and types into it, the way a user would. */
async function typeInto(page: Page, row: Locator, cell: string, text: string) {
  const target = row.getByTestId(cell);
  await target.click();
  const input = target.getByRole("textbox");
  await input.selectText();
  await input.pressSequentially(text);
  await page.keyboard.press("Tab");
}

transactionSteps.when(
  /^the user adds a debit of "([^"]+)" paid to "([^"]+)"$/,
  async ({ page }, amount: string, payee: string) => {
    await page.getByRole("button", { name: "Add New" }).click();
    const row = page.getByTestId("new-transaction").getByTestId("row");
    await typeInto(page, row, "debit", amount);
    await typeInto(page, row, "payee", payee);
    await page.getByTestId("add-button").click();
    await page.getByRole("button", { name: "Cancel" }).click();
  },
);

/**
 * `the first transaction shows payee "Ikea" and debit "100.00"`: each `column "value"` pair is
 * checked against that row of the list, top first. A column that isn't mentioned isn't checked.
 */
transactionSteps.then(
  /^the (\w+) transaction shows (.+)$/,
  async ({ page }, ordinal: string, checks: string) => {
    const row = page
      .getByTestId("transaction-table")
      .getByTestId("row")
      .nth(indexOfOrdinal(ordinal));
    for (const [, column, expected] of checks.matchAll(/(\w+) "([^"]*)"/g)) {
      await expect(row.getByTestId(column!)).toHaveText(expected!);
    }
  },
);

/**
 * The same check for several rows at once, as a table under the step. Each row of the table is checked against that
 * row of the list, top first, and a blank cell isn't checked:
 *
 *     Then the transaction list starts with:
 *       ---
 *       | payee | category |
 *       | Ikea  | Split    |
 *       ---
 */
transactionSteps.then(
  /^the transaction list starts with:(?:\n([\s\S]+))?$/,
  async ({ page }, block = "") => {
    const expected = parseTable(requireBlock(block, "the transaction list starts with:"));
    const rows = page.getByTestId("transaction-table").getByTestId("row");
    for (const [index, cells] of expected.entries()) {
      for (const [column, text] of Object.entries(cells)) {
        if (text) await expect(rows.nth(index).getByTestId(column)).toHaveText(text);
      }
    }
  },
);

const rowsOf = (page: Page) => page.getByTestId("transaction-table").getByTestId("row");

const entryOf = (cells: Record<string, string>): TransactionEntry => cells;

/**
 * Adds one transaction from a one-row table, with any of the columns account, payee, notes,
 * category, debit and credit.
 */
transactionSteps.when(
  /^the user adds a transaction:(?:\n([\s\S]+))?$/,
  async ({ page }, block = "") => {
    const [cells] = parseTable(requireBlock(block, "the user adds a transaction:"));
    await openEntryForm(page);
    await fillTransactionRow(
      page,
      page.getByTestId("new-transaction").getByTestId("row"),
      entryOf(cells!),
    );
    await finishEntry(page);
  },
);

/** The same, but stops before pressing Add, so the form can be looked at. */
transactionSteps.when(
  /^the user fills in a transaction without adding it:(?:\n([\s\S]+))?$/,
  async ({ page }, block = "") => {
    const [cells] = parseTable(
      requireBlock(block, "the user fills in a transaction without adding it:"),
    );
    await openEntryForm(page);
    await fillTransactionRow(
      page,
      page.getByTestId("new-transaction").getByTestId("row"),
      entryOf(cells!),
    );
  },
);

transactionSteps.when("the user adds the filled-in transaction", async ({ page }) => {
  await finishEntry(page);
});

transactionSteps.then(
  /^the filled-in transaction's category is "([^"]+)"$/,
  async ({ page }, category: string) => {
    const input = page.getByTestId("new-transaction").getByTestId("category").locator("input");
    await expect(input).toHaveValue(category);
  },
);

/**
 * Adds a split transaction. The first row of the table is the whole transaction, the rest are its
 * parts, and the editor starts with two empty parts.
 */
transactionSteps.when(
  /^the user adds a split transaction:(?:\n([\s\S]+))?$/,
  async ({ page }, block = "") => {
    const [root, ...parts] = parseTable(requireBlock(block, "the user adds a split transaction:"));
    await openEntryForm(page);
    const entryRows = page.getByTestId("new-transaction").getByTestId("row");
    await fillTransactionRow(page, entryRows.first(), { ...entryOf(root!), category: "split" });
    for (const [index, part] of parts.entries()) {
      if (index >= 2) await page.getByRole("button", { name: "Add Split" }).click();
      await fillTransactionRow(page, entryRows.nth(index + 1), entryOf(part));
    }
    await finishEntry(page);
  },
);

transactionSteps.when(
  /^the user changes the (\w+) transaction's (debit|credit) to "([^"]+)"$/,
  async ({ page }, ordinal: string, column: string, amount: string) => {
    const cell = rowsOf(page).nth(indexOfOrdinal(ordinal)).getByTestId(column);
    await cell.click();
    const input = cell.getByRole("textbox");
    await input.selectText();
    await input.pressSequentially(amount);
    await page.keyboard.press("Tab");
  },
);

// What the balances were before something happened, to compare with afterwards.
const balancesBefore = new WeakMap<Page, { account: string; all: string; onBudget: string }>();
const balanceOf = async (page: Page, id: string) =>
  (await page.getByTestId(id).textContent()) ?? "";

transactionSteps.given("the user notes the balances", async ({ page }) => {
  balancesBefore.set(page, {
    account: await balanceOf(page, "account-balance"),
    all: await balanceOf(page, "sidebar-all-accounts-balance"),
    onBudget: await balanceOf(page, "sidebar-on-budget-balance"),
  });
});

transactionSteps.then("the account balance has changed", async ({ page }) => {
  const before = balancesBefore.get(page);
  if (!before) throw new Error('"the user notes the balances" comes first');
  await expect.poll(() => balanceOf(page, "account-balance")).not.toBe(before.account);
});

transactionSteps.then("the total and on-budget balances are unchanged", async ({ page }) => {
  const before = balancesBefore.get(page);
  if (!before) throw new Error('"the user notes the balances" comes first');
  await expect.poll(() => balanceOf(page, "sidebar-all-accounts-balance")).toBe(before.all);
  await expect.poll(() => balanceOf(page, "sidebar-on-budget-balance")).toBe(before.onBudget);
});

// Filters

async function openFilter(page: Page, field: string) {
  await page.getByRole("button", { name: "Filter" }).click();
  await page.getByTestId("filters-select-tooltip").getByRole("button", { name: field }).click();
  return page.getByTestId("filters-menu-tooltip");
}

/** Picks an item from an autocomplete list, and waits for the list to close so the value has registered. */
async function pickItem(page: Page, testId: string) {
  const item = page.getByTestId(testId);
  await item.click();
  await expect(item).toBeHidden();
}

/** Applies the filter and waits until the list shows it, so a check right after reads the filtered rows. */
async function applyFilter(menu: Locator) {
  await menu.getByRole("button", { name: "Apply" }).click();
  await expect(menu).toBeHidden();
  await expect(menu.page().getByRole("button", { name: "Delete filter" }).first()).toBeVisible();
}

transactionSteps.when(
  /^the user filters the transactions to the category "([^"]+)"$/,
  async ({ page }, category: string) => {
    const menu = await openFilter(page, "Category");
    await page.keyboard.type(category.slice(0, 1));
    await pickItem(page, `${category}-category-item`);
    await applyFilter(menu);
  },
);

transactionSteps.when(
  /^the user filters the transactions to the category group "([^"]+)"$/,
  async ({ page }, group: string) => {
    const menu = await openFilter(page, "Category");
    await menu.getByRole("button", { name: "Category", exact: true }).click();
    await page.getByRole("button", { name: "Category group", exact: true }).click();
    await page.keyboard.type(group.slice(0, 1));
    await pickItem(page, `${group}-category-group-item`);
    await applyFilter(menu);
  },
);

transactionSteps.when(
  /^the user filters the transactions to the payee "([^"]+)"$/,
  async ({ page }, payee: string) => {
    const menu = await openFilter(page, "Payee");
    // Auto-open is broken for anything but "is not", so open the list by clicking.
    await menu.getByLabel("Payee").click();
    await pickItem(page, `${payee}-payee-item`);
    await applyFilter(menu);
  },
);

transactionSteps.when(
  /^the user filters the transactions to payees that (contain|do not contain) "([^"]+)"$/,
  async ({ page }, op: string, text: string) => {
    const menu = await openFilter(page, "Payee");
    await menu
      .getByRole("button", { name: op === "contain" ? "contains" : "does not contain" })
      .click();
    const input = menu.getByPlaceholder("nothing");
    await input.fill(text);
    await expect(input).toHaveValue(text);
    await applyFilter(menu);
  },
);

transactionSteps.when(
  /^the user filters the transactions to the note "([^"]+)"$/,
  async ({ page }, note: string) => {
    const menu = await openFilter(page, "Note");
    await page.keyboard.type(note);
    await applyFilter(menu);
  },
);

transactionSteps.when("the user removes the first filter", async ({ page }) => {
  await page.getByRole("button", { name: "Delete filter" }).first().click();
});

/** `the first 5 transactions all show category "Clothing"`, or `... category matching "^(A|B)$"`. */
transactionSteps.then(
  /^the first (\d+) transactions all show (\w+) (matching )?"([^"]*)"$/,
  async (
    { page },
    count: string,
    column: string,
    matching: string | undefined,
    expected: string,
  ) => {
    for (let i = 0; i < Number(count); i++) {
      await expect(rowsOf(page).nth(i).getByTestId(column)).toHaveText(
        matching ? new RegExp(expected) : expected,
      );
    }
  },
);

transactionSteps.then(
  /^none of the first (\d+) transactions show (\w+) matching "([^"]*)"$/,
  async ({ page }, count: string, column: string, pattern: string) => {
    for (let i = 0; i < Number(count); i++) {
      await expect(rowsOf(page).nth(i).getByTestId(column)).not.toHaveText(new RegExp(pattern));
    }
  },
);

// Notes tooltip

transactionSteps.when(
  /^the user hovers over the notes of the (\w+) transaction$/,
  async ({ page }, ordinal: string) => {
    await rowsOf(page).nth(indexOfOrdinal(ordinal)).getByTestId("notes").hover();
  },
);

transactionSteps.then(
  /^a tooltip shows "([^"]+)" with the tag "([^"]+)"$/,
  async ({ page }, note: string, tag: string) => {
    const tooltip = page.getByRole("tooltip");
    await expect(tooltip).toBeVisible();
    await expect(tooltip).toContainText(note);
    await expect(tooltip.getByRole("button", { name: tag })).toBeVisible();
  },
);

transactionSteps.then("no tooltip appears", async ({ page }) => {
  await page.waitForTimeout(700); // past the tooltip's hover delay
  await expect(page.getByRole("tooltip")).not.toBeVisible();
});

// Column manager

const columnsDialog = (page: Page) => page.getByTestId("transaction-table-columns-modal");
const toggleOf = (page: Page, column: string) =>
  columnsDialog(page).locator(`#toggle-column-${column.toLowerCase()}`);

transactionSteps.when("the user opens the table columns dialog", async ({ page }) => {
  await page.getByRole("button", { name: "Account menu" }).click();
  await page.getByRole("button", { name: "Manage table columns" }).click();
  await columnsDialog(page).waitFor();
});

transactionSteps.when(
  /^the user (hides|shows) the "([^"]+)" column$/,
  async ({ page }, action: string, column: string) => {
    const toggle = toggleOf(page, column);
    if ((await toggle.isChecked()) !== (action === "shows")) {
      await columnsDialog(page)
        .locator(`label[for="toggle-column-${column.toLowerCase()}"]`)
        .click();
    }
  },
);

transactionSteps.when(
  /^the user moves the "([^"]+)" column up (\d+) places$/,
  async ({ page }, column: string, places: string) => {
    // The drag handle is keyboard-operable: Enter picks the column up, arrows move it.
    await columnsDialog(page)
      .getByRole("button", { name: `Reorder ${column} column` })
      .focus();
    await page.keyboard.press("Enter");
    for (let i = 0; i < Number(places); i++) await page.keyboard.press("ArrowUp");
    await page.keyboard.press("Enter");
  },
);

transactionSteps.when(
  "the user applies the columns to all transaction tables",
  async ({ page }) => {
    await columnsDialog(page).getByText("Apply to all transaction tables").click();
  },
);

transactionSteps.when("the user resets the columns to default", async ({ page }) => {
  await columnsDialog(page).getByRole("button", { name: "Reset to default", exact: true }).click();
});

transactionSteps.when("the user saves the table columns", async ({ page }) => {
  await columnsDialog(page).getByRole("button", { name: "Save", exact: true }).click();
  await columnsDialog(page).waitFor({ state: "hidden" });
});

transactionSteps.then(/^the table header reads "([^"]+)"$/, async ({ page }, text: string) => {
  await expect(page.getByTestId("transaction-table-header")).toHaveText(text);
});

transactionSteps.then(
  /^the table header (does not show|shows) "([^"]+)"$/,
  async ({ page }, mode: string, text: string) => {
    const header = page.getByTestId("transaction-table-header");
    if (mode === "shows") await expect(header).toContainText(text);
    else await expect(header).not.toContainText(text);
  },
);

transactionSteps.then(
  /^the "([^"]+)" column is (hidden|shown) in the table columns dialog$/,
  async ({ page }, column: string, state: string) => {
    const toggle = toggleOf(page, column);
    if (state === "hidden") await expect(toggle).not.toBeChecked();
    else await expect(toggle).toBeChecked();
  },
);
