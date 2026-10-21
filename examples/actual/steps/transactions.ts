import { expect, type Locator } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";
import type { ActualContext } from "./context.js";
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
