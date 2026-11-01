import type { Locator, Page } from "@playwright/test";

/** The cells of the entry form, by the column they belong to. A blank one is left alone. */
export interface TransactionEntry {
  account?: string;
  payee?: string;
  notes?: string;
  category?: string;
  debit?: string;
  credit?: string;
}

const TYPED_CELLS = ["debit", "credit", "account", "payee"] as const;

async function selectInputText(input: Locator) {
  if (await input.inputValue()) await input.selectText();
}

/** Fills the cells of one row of the entry form, in the order a user would tab through them. */
export async function fillTransactionRow(page: Page, row: Locator, entry: TransactionEntry) {
  for (const cell of TYPED_CELLS) {
    const text = entry[cell];
    if (!text) continue;
    const target = row.getByTestId(cell);
    await target.click();
    const input = target.getByRole("textbox");
    await selectInputText(input);
    await input.pressSequentially(text);
    await page.keyboard.press("Tab");
  }

  if (entry.notes) {
    const target = row.getByTestId("notes");
    await target.click();
    await target.getByRole("combobox").fill(entry.notes);
    await page.keyboard.press("Tab");
  }

  if (entry.category) {
    const target = row.getByTestId("category");
    await target.click();
    if (entry.category.toLowerCase() === "split") {
      await page.getByTestId("split-transaction-button").click();
    } else {
      const input = target.getByRole("textbox");
      await selectInputText(input);
      await input.pressSequentially(entry.category);
      await page.keyboard.press("Tab");
    }
  }
}

/** Opens the entry form for a new transaction. */
export async function openEntryForm(page: Page) {
  await page.getByRole("button", { name: "Add New" }).click();
}

/** Presses Add, then closes the form. */
export async function finishEntry(page: Page) {
  await page.getByTestId("add-button").click();
  await page.getByRole("button", { name: "Cancel" }).click();
}
