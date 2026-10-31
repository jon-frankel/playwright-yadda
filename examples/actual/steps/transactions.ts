import { expect, type Locator } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";
import type { ActualContext } from "./context.js";
import { parseTable, requireBlock } from "./support/blocks.js";
import { indexOfOrdinal } from "./support/ordinal.js";

export const transactionSteps = createStepLibrary();

/** Opens a cell of a transaction row and types into it, the way a user would. */
async function typeInto(ctx: ActualContext, row: Locator, cell: string, text: string) {
  const target = row.getByTestId(cell);
  await target.click();
  const input = target.getByRole("textbox");
  await input.selectText();
  await input.pressSequentially(text);
  await ctx.page.keyboard.press("Tab");
}

transactionSteps.when(
  /^the user adds a debit of "([^"]+)" paid to "([^"]+)"$/,
  async function (this: ActualContext, amount: string, payee: string) {
    await this.page.getByRole("button", { name: "Add New" }).click();
    const row = this.page.getByTestId("new-transaction").getByTestId("row");
    await typeInto(this, row, "debit", amount);
    await typeInto(this, row, "payee", payee);
    await this.page.getByTestId("add-button").click();
    await this.page.getByRole("button", { name: "Cancel" }).click();
  },
);

/**
 * `the first transaction shows payee "Ikea" and debit "100.00"`: each `column "value"` pair is
 * checked against that row of the list, top first. A column that isn't mentioned isn't checked.
 */
transactionSteps.then(
  /^the (\w+) transaction shows (.+)$/,
  async function (this: ActualContext, ordinal: string, checks: string) {
    const row = this.page
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
  async function (this: ActualContext, block = "") {
    const expected = parseTable(requireBlock(block, "the transaction list starts with:"));
    const rows = this.page.getByTestId("transaction-table").getByTestId("row");
    for (const [index, cells] of expected.entries()) {
      for (const [column, text] of Object.entries(cells)) {
        if (text) await expect(rows.nth(index).getByTestId(column)).toHaveText(text);
      }
    }
  },
);
