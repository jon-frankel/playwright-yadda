import type { Frame, Page } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";

export interface WordPressScenarioContext {
  page: Page;
  wpFrame?: Frame;
  scopeUrl?: string;
  expectedPostTitle?: string;
  [key: string]: unknown;
}

export const commonSteps = createStepLibrary();

commonSteps.given(
  "WordPress WASM is loaded in the browser",
  async function (this: WordPressScenarioContext) {
    // Navigate to the local static wrapper hosting WordPress Playground WASM
    await this.page.goto("/");

    // Wait for the WordPress WASM runtime frame to mount and register
    let wpFrame: Frame | undefined;
    const start = Date.now();
    while (Date.now() - start < 45000) {
      wpFrame = this.page.frames().find((f) => f.url().includes("/scope:"));
      if (wpFrame) break;
      await new Promise((resolve) => setTimeout(resolve, 500));
    }

    if (!wpFrame) {
      throw new Error(
        "Timed out waiting for WordPress WASM instance (/scope:*) to initialize in browser",
      );
    }

    this.wpFrame = wpFrame;
    this.scopeUrl = wpFrame.url().replace(/\/$/, "");
  },
);
