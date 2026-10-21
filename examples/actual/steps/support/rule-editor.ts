import type { Locator, Page } from "@playwright/test";

/** Where in the edit-rule modal a row lives: a list of conditions, actions, or one split's actions. */
export interface EditorRow {
  page: Page;
  /** The element holding the rows. */
  root: Locator;
  index: number;
}

/** The modal's selects open a popover outside the modal, so options are found on the page. */
function popoverOption(page: Page, name: string): Locator {
  return page.locator("[data-popover]").getByRole("button", { name, exact: true });
}

async function choose(row: Locator, select: string, option: string, page: Page): Promise<void> {
  await row.getByTestId(select).getByRole("button").click();
  const choice = popoverOption(page, option);
  await choice.waitFor({ state: "visible" });
  await choice.click();
}

/**
 * Fills one row of the rule editor. Conditions pick the field before the operator, actions the other
 * way round, because that is the order the editor lays them out in. A new row is added if the
 * editor doesn't have one yet.
 */
export async function fillEditorRow(
  { page, root, index }: EditorRow,
  entry: { field: string; op?: string; value?: string },
  { fieldFirst }: { fieldFirst: boolean },
): Promise<void> {
  const row = root.getByTestId("editor-row").nth(index);
  if (!(await row.isVisible())) {
    await root.getByRole("button", { name: "Add entry" }).click();
  }

  if (entry.op && !fieldFirst) await choose(row, "op-select", entry.op, page);
  await choose(row, "field-select", entry.field, page);
  if (entry.op && fieldFirst) await choose(row, "op-select", entry.op, page);

  if (entry.value) {
    const input = row.getByRole("textbox");
    if (await input.inputValue()) await input.selectText();
    // Typed key by key: with fill() the autocomplete never highlights its "Create payee" option.
    await input.pressSequentially(entry.value);
    await page.keyboard.press("Enter");
  }
}
