import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 30000,
  reporter: [["playwright-yadda/reporter"]],
  use: {
    headless: true,
  },
});
