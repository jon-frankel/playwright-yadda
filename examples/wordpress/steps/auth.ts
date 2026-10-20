import { expect } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";
import type { WordPressScenarioContext } from "./common.js";

export const authSteps = createStepLibrary();

authSteps.when(
  /the user logs in as "([^"]+)" with password "([^"]+)"/,
  async function (this: WordPressScenarioContext, username: string, password: string) {
    // Navigate directly to the native /wp-login.php endpoint
    await this.page.goto("/wp-login.php");
    await this.page.waitForSelector("input#user_login", { timeout: 15000 });

    // Submit credentials
    await this.page.fill("input#user_login", username);
    await this.page.fill("input#user_pass", password);
    await this.page.click("#wp-submit");
  },
);

authSteps.then(
  "the WordPress admin bar should be visible",
  async function (this: WordPressScenarioContext) {
    await this.page.waitForSelector("#wpadminbar", { state: "visible", timeout: 15000 });
    const isVisible = await this.page.locator("#wpadminbar").isVisible();
    expect(isVisible).toBe(true);
  },
);
