import { expect } from "@playwright/test";
import type { Locator, Page } from "@playwright/test";
import { createBdd } from "playwright-yadda";
import { test } from "./test.js";
import { parseTable, requireBlock } from "./support/blocks.js";
import { indexOfOrdinal } from "./support/ordinal.js";
import { parseChecks } from "./support/checks.js";

const { createStepLibrary } = createBdd(test);

export const scheduleSteps = createStepLibrary();

const rows = (page: Page) => page.getByTestId("table").getByTestId("row");

/** The nth schedule of a payee, top first. Rows are found by payee because the list also holds the demo's own schedules. */
const scheduleOf = (page: Page, payee: string, ordinal = "first"): Locator =>
  rows(page).filter({ hasText: payee }).nth(indexOfOrdinal(ordinal));

// The small steps the macros below are built from.

async function openNewScheduleForm(page: Page) {
  await page.getByRole("button", { name: "Add new schedule" }).click();
}

/** Typing into an autocomplete, then clicking the option: Enter on a not-yet-highlighted list saves "None". */
async function chooseOption(page: Page, field: string, text: string) {
  const modal = page.getByTestId("schedule-edit-modal");
  await modal.getByRole("textbox", { name: field }).pressSequentially(text);
  await page.getByRole("option").filter({ hasText: text }).first().click();
}

async function fillSchedule(page: Page, payee: string, account: string, amount: string) {
  const modal = page.getByTestId("schedule-edit-modal");
  await chooseOption(page, "Payee", payee);
  await chooseOption(page, "Account", account);
  await modal.getByLabel("Amount").fill(amount);
}

async function addSchedule(page: Page) {
  await page.getByTestId("schedule-edit-modal").getByRole("button", { name: "Add" }).click();
}

scheduleSteps.given("the Schedules page is open", async ({ page }) => {
  await page.getByRole("link", { name: "Schedules" }).click();
});

// `define` matches the whole line, keyword included, so these patterns start with `\w+` instead of a keyword:
// the same macro serves a When and an And.
// One macro, written once as the small steps it is made of, for as many schedules as a scenario needs.
scheduleSteps.define(
  /^\w+ the user schedules (\d+(?:\.\d+)?) for "([^"]+)" from the "([^"]+)" account$/,
  async ({ page }, amount: string, payee: string, account: string) => {
    await openNewScheduleForm(page);
    await fillSchedule(page, payee, account, amount);
    await addSchedule(page);
  },
);

// `define`'s third argument is handed to the step as `this`, so posting and completing share one body.
async function runScheduleAction(page: Page, row: Locator, action: string) {
  await row.getByTestId("actions").getByRole("button").click();
  await page.getByRole("button", { name: action }).click();
}

scheduleSteps.define(
  /^\w+ the user (posts|completes) the (\w+) "([^"]+)" schedule$/,
  async function (
    this: { actions: Record<string, string> },
    { page },
    verb: string,
    ordinal: string,
    payee: string,
  ) {
    await runScheduleAction(page, scheduleOf(page, payee, ordinal), this.actions[verb]!);
  },
  { actions: { posts: "Post transaction today", completes: "Complete" } },
);

scheduleSteps.when(
  /^the user right-clicks the "([^"]+)" schedule$/,
  async ({ page }, payee: string) => {
    await scheduleOf(page, payee).click({ button: "right" });
  },
);

scheduleSteps.then(
  /^the (\w+) "([^"]+)" schedule shows (.+)$/,
  async ({ page }, ordinal: string, payee: string, checks: string) => {
    const row = scheduleOf(page, payee, ordinal);
    for (const { column, expected } of parseChecks(checks)) {
      await expect(row.getByTestId(column)).toHaveText(expected);
    }
  },
);

scheduleSteps.then(
  /^the first rule's conditions are:(?:\n([\s\S]+))?$/,
  async ({ page }, block = "") => {
    const expected = parseTable(requireBlock(block, "the first rule's conditions are:"));
    await expect(rows(page).first().getByTestId("conditions").locator(":scope > div")).toHaveText(
      expected.map((row) => row.condition!),
    );
  },
);

/**
 * Edits the schedule's rule the way a user's rule editor would, through Actual's own API: the
 * editor has no screen for a split to another account's payee. The demo's "Phone bills" schedule
 * then keeps `amount` of each payment on its own payee (split 1) and sends the remainder
 * (split 2) to the account's transfer payee.
 */
scheduleSteps.given(
  /^the "([^"]+)" schedule keeps (\d+(?:\.\d+)?) of each payment and sends the rest on to the "([^"]+)" account$/,
  async ({ page }, name: string, amount: string, account: string) => {
    await page.evaluate(
      async ([scheduleName, cents, accountName]) => {
        type Send = <T>(type: string, args?: unknown) => Promise<T>;
        const send = (window as unknown as { $send: Send }).$send;
        const query = <T>(table: string, filter: unknown[], select: string[]) =>
          send<{ data: T[] }>("query", {
            table,
            tableOptions: {},
            filterExpressions: filter,
            selectExpressions: select,
            groupExpressions: [],
            orderExpressions: [],
            calculation: false,
            rawMode: false,
            withDead: false,
            validateRefs: true,
            limit: null,
            offset: null,
          });
        const {
          data: [destination],
        } = await query<{ id: string }>("accounts", [{ name: accountName }], ["id"]);
        const {
          data: [payee],
        } = await query<{ id: string }>("payees", [{ transfer_acct: destination!.id }], ["id"]);
        const {
          data: [schedule],
        } = await query<{ rule: string }>("schedules", [{ name: scheduleName }], ["rule"]);
        const rule = await send<{ actions: unknown[] }>("rule-get", { id: schedule!.rule });
        await send("rule-update", {
          ...rule,
          actions: [
            ...rule.actions,
            {
              op: "set-split-amount",
              value: -Number(cents) * 100,
              options: { splitIndex: 1, method: "fixed-amount" },
            },
            {
              op: "set-split-amount",
              value: null,
              options: { splitIndex: 2, method: "remainder" },
            },
            { op: "set", field: "payee", value: payee!.id, options: { splitIndex: 2 } },
          ],
        });
      },
      [name, amount, account] as const,
    );
  },
);

scheduleSteps.then(
  /^a scheduled transfer from "([^"]+)" of (\d+\.\d\d) is listed as due$/,
  async ({ page }, from: string, credit: string) => {
    const preview = rows(page).filter({
      has: page.getByTestId("schedule-icon"),
      hasText: from,
    });
    await expect(preview).toHaveCount(1);
    await expect(preview.getByTestId("category")).toContainText("Due");
    await expect(preview.getByTestId("debit")).toHaveText("");
    await expect(preview.getByTestId("credit")).toHaveText(credit);
  },
);
