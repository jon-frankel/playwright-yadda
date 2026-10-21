import type { Page } from "@playwright/test";

/** What every step of this suite shares: the page, and the state a scenario builds up as it goes. */
export interface ActualContext {
  page: Page;
  /** How many condition and action rows of the open rule editor have been filled in so far. */
  ruleEditor?: { conditions: number; actions: number; splits: number };
  [key: string]: unknown;
}
