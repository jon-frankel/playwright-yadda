import { createHooks } from "playwright-yadda";

export const hooks = createHooks();

// A failed step leaves a screenshot in the report, whichever scenario it was.
hooks.AfterStep(async ({ error, step, fixtures }) => {
  if (!error) return;
  await fixtures.page.screenshot({ path: `test-results/failed-${Date.now()}.png` }).catch(() => {});
  console.log(`failed: ${step}`);
});

// Actual switches to its narrow layout by viewport width, and decides at load time. This runs after
// the fixtures are set up but before the first step, and the mobile steps open the budget themselves
// (not through the `budget` fixture), so the app loads at phone size. 350x600 is what Actual's own
// mobile tests use.
hooks.BeforeScenario(
  { tags: "@mobile", name: "phone viewport", order: -10 },
  async ({ fixtures }) => {
    await fixtures.page.setViewportSize({ width: 350, height: 600 });
  },
);
