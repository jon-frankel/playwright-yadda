import { expect } from "@playwright/test";
import { createStepLibrary } from "playwright-yadda";

export const storefrontSteps = createStepLibrary();

storefrontSteps.given("a customer is on the storefront", function (this: any) {
  this.cart = ["Ergonomic Keyboard"];
});

// Identical step phrase to admin library: "they remove the item"
storefrontSteps.when("they remove the item", function (this: any) {
  this.actionTaken = "storefront_remove";
  this.cart = [];
});

storefrontSteps.then("the cart count is 0", function (this: any) {
  expect(this.actionTaken).toBe("storefront_remove");
  expect(this.cart).toHaveLength(0);
});
