import { expect } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";
import type { ActualContext } from "./context.js";
import { fillEditorRow } from "./support/rule-editor.js";
import { indexOfOrdinal } from "./support/ordinal.js";

export const ruleSteps = createStepLibrary();

const modal = (ctx: ActualContext) => ctx.page.getByTestId("edit-rule-modal");

ruleSteps.when("the user starts a new rule", async function (this: ActualContext) {
  await this.page.getByRole("button", { name: "Create new rule" }).click();
  this.ruleEditor = { conditions: 0, actions: 0, splits: 0 };
});

ruleSteps.when(
  /^the rule applies when the (.+?) (is|is not|contains|does not contain|one of|matches) "([^"]+)"$/,
  async function (this: ActualContext, field: string, op: string, value: string) {
    const editor = this.ruleEditor;
    await fillEditorRow(
      {
        page: this.page,
        root: modal(this).getByTestId("condition-list"),
        index: editor.conditions++,
      },
      { field, op, value },
      { fieldFirst: true },
    );
  },
);

ruleSteps.when(
  /^the rule sets the (.+?) to "([^"]+)"$/,
  async function (this: ActualContext, field: string, value: string) {
    const editor = this.ruleEditor;
    await fillEditorRow(
      { page: this.page, root: modal(this).getByTestId("action-list"), index: editor.actions++ },
      { field, value },
      { fieldFirst: false },
    );
  },
);

// A split is a group of actions of its own, which the editor adds after the plain actions.
async function addSplit(
  ctx: ActualContext,
  share: { field: string; value?: string },
  category: string,
) {
  const editor = ctx.ruleEditor;
  await modal(ctx).getByTestId("add-split-transactions").click();
  const group = modal(ctx)
    .getByTestId("action-list")
    .nth(editor.actions + editor.splits++);
  await fillEditorRow({ page: ctx.page, root: group, index: 0 }, share, { fieldFirst: false });
  await fillEditorRow(
    { page: ctx.page, root: group, index: 1 },
    { field: "category", value: category },
    { fieldFirst: false },
  );
}

ruleSteps.when(
  /^the rule splits (\d+)% of the remainder to "([^"]+)"$/,
  async function (this: ActualContext, percent: string, category: string) {
    await addSplit(this, { field: "a fixed percent of the remainder", value: percent }, category);
  },
);

ruleSteps.when(
  /^the rule splits what is left equally to "([^"]+)"$/,
  async function (this: ActualContext, category: string) {
    await addSplit(this, { field: "an equal portion of the remainder" }, category);
  },
);

ruleSteps.when("the user saves the rule", async function (this: ActualContext) {
  await modal(this).getByRole("button", { name: "Save" }).click();
});

ruleSteps.then(
  /^the (\w+) rule's (conditions|actions) are "([^"]+)"$/,
  async function (this: ActualContext, ordinal: string, part: string, text: string) {
    const row = this.page.getByTestId("table").getByTestId("row").nth(indexOfOrdinal(ordinal));
    await expect(row.getByTestId(part).locator(":scope > div")).toHaveText([text]);
  },
);

ruleSteps.when(
  /^the user deletes the rule "([^"]+)"$/,
  async function (this: ActualContext, text: string) {
    // Found by its text: the list reloads after a save, and the new rule isn't always first yet.
    const row = this.page.getByTestId("table").getByTestId("row").filter({ hasText: text });
    await row.first().click({ button: "right" });
    await this.page.getByRole("menu").getByRole("button", { name: "Delete" }).click();
  },
);
