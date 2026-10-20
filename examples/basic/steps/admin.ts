import { expect } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";

export const adminSteps = createStepLibrary();

adminSteps.given("an admin is logged in", function (this: any) {
  this.adminLoggedIn = true;
});

// Identical step phrase to storefront library: "they remove the item"
adminSteps.when("they remove the item", function (this: any) {
  this.actionTaken = "admin_delete_product";
  this.productDeleted = true;
});

adminSteps.then("the product is deleted", function (this: any) {
  expect(this.actionTaken).toBe("admin_delete_product");
  expect(this.productDeleted).toBe(true);
});
