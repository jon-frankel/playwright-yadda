import { createLibraryRegistry } from "playwright-yadda";
import { commonSteps } from "./common.js";
import { storefrontSteps } from "./storefront.js";
import { adminSteps } from "./admin.js";
import { orderSteps } from "./orders.js";

export const registry = createLibraryRegistry({
  common: commonSteps,
  storefront: storefrontSteps,
  admin: adminSteps,
  orders: orderSteps,
});
