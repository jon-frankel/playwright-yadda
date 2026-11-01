import { createLibraryRegistry } from "playwright-yadda";
import { commonSteps } from "./common.js";
import { storefrontSteps } from "./storefront.js";
import { adminSteps } from "./admin.js";
import { inventorySteps } from "./inventory.js";
import { orderSteps } from "./orders.js";
import { tagsSteps } from "./tags.js";
import { playgroundBoundSteps } from "./playground/bound.js";
import { playgroundFixtureSteps } from "./playground/fixtures.js";
import { playgroundNamespacedSteps } from "./playground/namespaced.js";
import { playgroundParamSteps } from "./playground/param.js";
import { playgroundSharedSteps } from "./playground/shared.js";

export const registry = createLibraryRegistry({
  common: commonSteps,
  storefront: storefrontSteps,
  admin: adminSteps,
  orders: orderSteps,
  inventory: inventorySteps,
  tags: tagsSteps,
  // Empty, for ide-playground/create-step.feature
  fixtures: playgroundFixtureSteps,
  bound: playgroundBoundSteps,
  namespaced: playgroundNamespacedSteps,
  shared: playgroundSharedSteps,
  param: playgroundParamSteps,
});
