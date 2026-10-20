import type { Page } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";

export interface WordPressScenarioContext {
  page: Page;
  expectedPostTitle?: string;
  [key: string]: unknown;
}

export const commonSteps = createStepLibrary();

commonSteps.given("WordPress WASM is running", async function (this: WordPressScenarioContext) {
  // Navigate to root to ensure the local WASM WordPress server is responsive
  await this.page.goto("/");
});
