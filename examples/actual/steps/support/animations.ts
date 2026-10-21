import type { Page } from "@playwright/test";

/**
 * Snaps CSS transitions to their end state. Actual's own suite does this too: modal fade-ins race
 * with Playwright's click-stability check when many scenarios run in parallel.
 */
export async function disableAnimations(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const css = `*, *::before, *::after {
      transition-duration: 0s !important; transition-delay: 0s !important;
      animation-duration: 0s !important; animation-delay: 0s !important;
    }`;
    const install = () => {
      const style = document.createElement("style");
      style.textContent = css;
      document.head.appendChild(style);
    };
    if (document.head) install();
    else document.addEventListener("DOMContentLoaded", install);
  });
}
