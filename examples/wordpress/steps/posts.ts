import { expect } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";
import type { WordPressScenarioContext } from "./common.js";

export const postSteps = createStepLibrary();

postSteps.given(
  "the admin navigates to the posts list",
  async function (this: WordPressScenarioContext) {
    await this.page.goto("/wp-admin/edit.php");
    await this.page.waitForSelector(".wp-list-table", { timeout: 15000 });
  },
);

postSteps.when(
  /they update the first post title to "([^"]+)"/,
  async function (this: WordPressScenarioContext, newTitle: string) {
    const postRow = this.page.locator("tr#post-1, tr.type-post").first();
    await postRow.hover();

    // Open Quick Edit
    const quickEditBtn = postRow.locator("button.editinline");
    await quickEditBtn.click();

    // Fill inline editor
    const inlineEditor = this.page.locator("tr.inline-editor");
    await inlineEditor.waitFor({ state: "visible", timeout: 10000 });
    await inlineEditor.locator("input.ptitle").fill(newTitle);

    // Save and wait for editor row to hide
    await inlineEditor.locator("button.save").click();
    await inlineEditor.waitFor({ state: "hidden", timeout: 15000 });

    this.expectedPostTitle = newTitle;
  },
);

postSteps.then(
  /the posts list should display "([^"]+)"/,
  async function (this: WordPressScenarioContext, expectedTitle: string) {
    const postRow = this.page.locator("tr#post-1, tr.type-post").first();
    const titleLocator = postRow.locator(".row-title");
    await expect(titleLocator).toHaveText(expectedTitle, { timeout: 10000 });
  },
);
