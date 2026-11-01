import { createHooks } from "playwright-yadda";

export const hooks = createHooks();

// A failed step leaves a screenshot in the report, whichever scenario it was.
hooks.AfterStep(async ({ error, step, fixtures }) => {
  if (!error) return;
  await fixtures.page.screenshot({ path: `test-results/failed-${Date.now()}.png` }).catch(() => {});
  console.log(`failed: ${step}`);
});
