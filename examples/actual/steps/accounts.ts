import { expect } from "@playwright/test";
import type { Page } from "@playwright/test";
import { createBdd } from "playwright-yadda";
import { test } from "./test.js";
import { indexOfOrdinal } from "./support/ordinal.js";

const { createStepLibrary } = createBdd(test);

export const accountSteps = createStepLibrary();

const sidebarLink = (page: Page, account: string) =>
  page.getByRole("link", { name: new RegExp(`^${account}`) });
const rowsOf = (page: Page) => page.getByTestId("transaction-table").getByTestId("row");

/**
 * Fills the "Add account" form. React Aria re-renders its buttons and inputs as focus moves, which
 * makes Playwright's click-stability check loop under load, so, as in Actual's own suite, the
 * values are set and the button is clicked in one browser-side task.
 */
accountSteps.when(
  /^the user creates an? (on-budget|off-budget) account "([^"]+)" with a balance of "([^"]+)"$/,
  async ({ page }, kind: string, name: string, balance: string) => {
    await page.getByRole("button", { name: "Add account" }).click();
    await page.getByRole("heading", { name: "Add account" }).waitFor();
    for (const [label, value] of [
      ["Name", name],
      ["Balance", balance],
    ] as const) {
      await page.getByLabel(label).evaluate((el, text) => {
        const input = el as HTMLInputElement;
        // oxlint-disable-next-line typescript/unbound-method -- the React-controlled-input pattern
        const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
        setter?.call(input, text);
        input.dispatchEvent(new Event("input", { bubbles: true }));
      }, value);
    }
    if (kind === "off-budget") await page.getByLabel("Off budget").click();
    await page
      .getByRole("button", { name: "Create", exact: true })
      .evaluate((el: HTMLElement) => el.click());
    await page.getByTestId("transaction-table").waitFor();
    if (Number(balance) !== 0) await rowsOf(page).first().waitFor();
  },
);

accountSteps.then(/^the account is named "([^"]+)"$/, async ({ page }, name: string) => {
  await expect(page.getByTestId("account-name")).toHaveText(name);
});

accountSteps.when(
  /^the user closes the account and moves its balance to "([^"]+)"$/,
  async ({ page }, target: string) => {
    await page.getByRole("button", { name: "Account menu" }).click();
    await page.getByRole("button", { name: "Close Account" }).click();
    const modal = page.getByTestId("close-account-modal");
    await modal.getByPlaceholder("Select account...").fill(target);
    await page.keyboard.press("Enter");
    await modal.getByRole("button", { name: "Close account" }).click();
  },
);

accountSteps.when(
  /^the user right-clicks the "([^"]+)" account in the sidebar$/,
  async ({ page }, account: string) => {
    await sidebarLink(page, account).click({ button: "right" });
  },
);

accountSteps.when(
  /^the user right-clicks the (\w+) transaction$/,
  async ({ page }, ordinal: string) => {
    await rowsOf(page).nth(indexOfOrdinal(ordinal)).click({ button: "right" });
  },
);

accountSteps.then(
  /^the (\w+) transaction's (credit|debit) column is wider than (\d+) pixels$/,
  async ({ page }, ordinal: string, column: string, pixels: string) => {
    const box = await rowsOf(page).nth(indexOfOrdinal(ordinal)).getByTestId(column).boundingBox();
    expect(box?.width).toBeGreaterThan(Number(pixels));
  },
);

// Selecting transactions

accountSteps.when(/^the user selects the (\w+) transaction$/, async ({ page }, ordinal: string) => {
  // A row added a moment ago may still be mounting, and a click on it would not select anything.
  const row = rowsOf(page).nth(indexOfOrdinal(ordinal));
  await expect(row.getByTestId("payee")).toBeVisible();
  await row.getByTestId("select").click();
});

accountSteps.when(
  /^the user shift-selects the transaction with notes "([^"]+)"$/,
  async ({ page }, notes: string) => {
    await rowsOf(page)
      .filter({ hasText: notes })
      .getByTestId("select")
      .click({ modifiers: ["Shift"] });
  },
);

accountSteps.when(
  /^the user selects the transaction with notes "([^"]+)"$/,
  async ({ page }, notes: string) => {
    await rowsOf(page).filter({ hasText: notes }).getByTestId("select").click();
  },
);

accountSteps.when(
  /^the user chooses "([^"]+)" for the selection$/,
  async ({ page }, action: string) => {
    await page.getByTestId("transactions-select-button").click();
    await page
      .getByTestId("transactions-select-tooltip")
      .getByRole("button", { name: action })
      .click();
  },
);

accountSteps.then(/^the selection is "([^"]+)"$/, async ({ page }, text: string) => {
  await expect(page.getByTestId("transactions-select-button")).toHaveText(text);
});

accountSteps.then(
  /^the transaction with notes "([^"]+)" is (hidden|listed)$/,
  async ({ page }, notes: string, state: string) => {
    const row = rowsOf(page).filter({ hasText: notes });
    if (state === "hidden") await expect(row).not.toBeVisible();
    else await expect(row.first()).toBeVisible();
  },
);

accountSteps.then("a date picker is centred in the dialog", async ({ page }) => {
  const dialog = page.getByRole("dialog");
  const grid = dialog.locator(".react-aria-CalendarGrid");
  await expect(grid).toBeVisible();
  const dialogBox = (await dialog.boundingBox())!;
  const gridBox = (await grid.boundingBox())!;
  const leftGap = gridBox.x - dialogBox.x;
  const rightGap = dialogBox.x + dialogBox.width - (gridBox.x + gridBox.width);
  expect(Math.abs(leftGap - rightGap)).toBeLessThanOrEqual(2);
});

accountSteps.then(
  /^every month of the date picker keeps its grid inside the dialog for the next (\d+) months$/,
  async ({ page }, months: string) => {
    // Actual's own test steps to April 2017, a six-row month relative to its pinned date. Any
    // run of months holds one, so the check is structural instead: whatever the month, the grid
    // is fully inside the dialog, and over the run a month with six week rows must occur.
    const dialog = page.getByRole("dialog");
    const grid = dialog.locator(".react-aria-CalendarGrid");
    const title = dialog.locator(".calendar-header-title");
    const weekRows = grid.locator("tbody tr");
    let sixRowMonths = 0;
    for (let month = 0; month <= Number(months); month++) {
      if (month > 0) {
        const previous = await title.textContent();
        await dialog.getByRole("button", { name: "Next month" }).click();
        await expect(title).not.toHaveText(previous!);
      }
      if ((await weekRows.count()) === 6) sixRowMonths++;
      const dialogBox = (await dialog.boundingBox())!;
      const gridBox = (await grid.boundingBox())!;
      expect(gridBox.y + gridBox.height).toBeLessThanOrEqual(dialogBox.y + dialogBox.height);
    }
    expect(sixRowMonths, "no month with six week rows was visited").toBeGreaterThan(0);
  },
);

accountSteps.then(
  /^the date picker dialog is at most (\d+) pixels wide on a (\d+) pixel wide screen$/,
  async ({ page }, maximum: string, screen: string) => {
    await page.setViewportSize({ width: Number(screen), height: 1080 });
    const box = (await page.getByRole("dialog").boundingBox())!;
    expect(box.width).toBeLessThanOrEqual(Number(maximum));
  },
);

// Reconciling

accountSteps.when(
  /^the user marks the transaction with notes "([^"]+)" as cleared$/,
  async ({ page }, notes: string) => {
    await rowsOf(page).filter({ hasText: notes }).getByTestId("cleared").click();
  },
);

accountSteps.when(
  "the user reconciles the account and locks the transactions",
  async ({ page }) => {
    await page.getByRole("button", { name: "Reconcile" }).click();
    // The amount is pre-filled with the cleared balance, so submitting at once leaves no difference.
    const popover = page.locator("[data-popover]");
    await popover.getByRole("textbox").waitFor();
    await popover.getByRole("button", { name: "Reconcile" }).click();
    await page.getByRole("button", { name: "Lock transactions" }).click();
  },
);

accountSteps.when("the user hides the reconciled transactions", async ({ page }) => {
  await page.getByRole("button", { name: "Account menu" }).click();
  await page.getByRole("button", { name: "Hide reconciled transactions" }).click();
});

// Imports

async function importFile(page: Page, file: { name: string; mimeType: string; content: string }) {
  const chooser = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: "Import" }).click();
  await (
    await chooser
  ).setFiles({
    name: file.name,
    mimeType: file.mimeType,
    buffer: Buffer.from(file.content),
  });
}

const importButton = (page: Page) => page.getByRole("button", { name: /Import \d+ transactions/ });

/** Yadda hands a block step its first line, then the lines between the `---` fences. */
const blockOf = (block: string) =>
  `${block
    .trim()
    .split("\n")
    .map((line) => line.trim())
    .join("\n")}\n`;

accountSteps.when(
  /^the user opens a (CSV|QIF) file for import:(?:\n([\s\S]+))?$/,
  async ({ page }, kind: string, block = "") => {
    if (!block.trim()) throw new Error("the import step needs a block between two --- lines");
    await importFile(page, {
      name: `import.${kind.toLowerCase()}`,
      mimeType: kind === "CSV" ? "text/csv" : "application/qif",
      content: blockOf(block),
    });
    await importButton(page).waitFor();
  },
);

accountSteps.when("the user imports the transactions", async ({ page }) => {
  const button = importButton(page);
  await button.click();
  await expect(button).not.toBeVisible();
});

accountSteps.then(
  /^the import preview lists the categories "([^"]+)" and "([^"]+)"$/,
  async ({ page }, first: string, second: string) => {
    const dialog = page.getByRole("dialog");
    await expect(dialog.getByText(first, { exact: true })).toBeVisible();
    await expect(dialog.getByText(second, { exact: true })).toBeVisible();
  },
);

accountSteps.when("the user closes the import", async ({ page }) => {
  await page.getByRole("button", { name: "Close" }).click();
  await expect(importButton(page)).not.toBeVisible();
});

accountSteps.then("the import offers 0 transactions and cannot be started", async ({ page }) => {
  await expect(importButton(page)).toBeDisabled();
  await expect(importButton(page)).toHaveText(/Import 0 transactions/);
});

accountSteps.then(/^the import does not offer to import notes$/, async ({ page }) => {
  await expect(page.getByRole("checkbox", { name: "Import notes from file" })).not.toBeVisible();
});

accountSteps.then(
  /^the transaction "([^"]+)" is listed with category "([^"]+)"$/,
  async ({ page }, payee: string, category: string) => {
    await expect(rowsOf(page).filter({ hasText: payee }).getByTestId("category")).toHaveText(
      category,
    );
  },
);
