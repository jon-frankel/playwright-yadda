import type { Page } from "@playwright/test";

/** What every step of this suite shares: the page, and the state a scenario builds up as it goes. */
export interface ActualContext {
  page: Page;
  /** How many condition and action rows of the open rule editor have been filled in so far. */
  ruleEditor: { conditions: number; actions: number; splits: number };
  [key: string]: unknown;
}

/** Builds the context each scenario starts with, passed to `defineFeatureSuite` as `createContext`. */
export function createActualContext({ page }: { page: Page }): ActualContext {
  return { page, ruleEditor: { conditions: 0, actions: 0, splits: 0 } };
}
