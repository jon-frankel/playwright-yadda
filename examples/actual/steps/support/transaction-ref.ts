import type { Locator, Page } from "@playwright/test";
import { indexOfOrdinal } from "./ordinal.js";

/**
 * How a step names one transaction, as one capture group to embed in a step pattern:
 * `the second transaction` or `the transaction with notes "range-two"`. Every step that acts on a
 * transaction takes it, so a scenario can point at a row by position or by what it says.
 */
export const TRANSACTION = String.raw`(the \w+ transaction|the transaction with notes "[^"]+")`;

/** The row a `TRANSACTION` capture names. */
export function transactionRow(page: Page, ref: string): Locator {
  const rows = page.getByTestId("transaction-table").getByTestId("row");
  const notes = ref.match(/^the transaction with notes "([^"]+)"$/)?.[1];
  if (notes !== undefined) return rows.filter({ hasText: notes });
  return rows.nth(indexOfOrdinal(ref.split(" ")[1]!));
}
