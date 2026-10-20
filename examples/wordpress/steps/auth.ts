import { expect } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";
import type { WordPressScenarioContext } from "./common.js";

export const authSteps = createStepLibrary();

authSteps.when(
  /the user logs in as "([^"]+)" with password "([^"]+)"/,
  async function (this: WordPressScenarioContext, username: string, password: string) {
    const frame = this.wpFrame;
    if (!frame || !this.scopeUrl) {
      throw new Error(
        "WordPress frame not initialized. Ensure 'Given WordPress WASM is loaded' ran first.",
      );
    }

    // Navigate to wp-login.php
    await frame.goto(`${this.scopeUrl}/wp-login.php`);
    await frame.waitForSelector("input#user_login", { timeout: 20000 });

    // Fill credentials and submit
    await frame.fill("input#user_login", username);
    await frame.fill("input#user_pass", password);
    await frame.click("#wp-submit");
  },
);

authSteps.then(
  "the WordPress admin bar should be visible",
  async function (this: WordPressScenarioContext) {
    const frame = this.wpFrame;
    if (!frame) {
      throw new Error("WordPress frame not initialized.");
    }

    // Confirm authentication succeeded by checking the global admin bar
    await frame.waitForSelector("#wpadminbar", { state: "visible", timeout: 20000 });
    const isVisible = await frame.locator("#wpadminbar").isVisible();
    expect(isVisible).toBe(true);
  },
);
