import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 60000,
  reporter: [["list"]],
  use: {
    headless: true,
    baseURL: "http://127.0.0.1:3000",
  },
  webServer: {
    command: "node scripts/serve.mjs",
    url: "http://127.0.0.1:3000",
    reuseExistingServer: !process.env.CI,
    timeout: 15000,
  },
});
