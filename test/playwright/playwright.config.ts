import { defineConfig } from "@playwright/test";

// Driven by test/playwright.test.ts, which names the spec to run. No browser is needed: the specs
// replace `page` and `context`.
export default defineConfig({
  testDir: ".",
  testMatch: "*.spec.ts",
  reporter: "json",
  workers: 1,
});
