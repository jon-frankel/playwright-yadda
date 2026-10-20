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
    command: "pnpm exec wp-playground-cli server --port 3000",
    url: "http://127.0.0.1:3000/wp-login.php",
    reuseExistingServer: !process.env.CI,
    timeout: 30000,
  },
});
