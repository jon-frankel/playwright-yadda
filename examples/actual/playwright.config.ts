import { defineConfig } from "@playwright/test";

const port = Number(process.env.E2E_PORT) || 3001;

export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  // Each scenario gets its own browser context, and so its own demo budget.
  fullyParallel: true,
  reporter: [["playwright-yadda/reporter"]],
  expect: { timeout: 10_000 },
  use: {
    browserName: "chromium",
    headless: true,
    baseURL: `http://localhost:${port}`,
    // Actual checks for this user agent; it is what its own suite sends.
    userAgent: "playwright",
  },
  webServer: {
    command: "node scripts/serve.mjs",
    url: `http://localhost:${port}`,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000, // the very first run also fetches and installs Actual
  },
});
