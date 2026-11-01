import path from "node:path";
import { fileURLToPath } from "node:url";
import { test as base } from "@playwright/test";
import { createLibraryRegistry, createStepLibrary, defineFeatureSuite } from "../../src/index.ts";

// no browser: the suite always asks for these two, so they are replaced
const test = base.extend({
  // oxlint-disable-next-line no-empty-pattern
  page: async ({}, use) => use({} as never),
  // oxlint-disable-next-line no-empty-pattern
  context: async ({}, use) => use({} as never),
});

const storefront = createStepLibrary();
storefront.given("the storefront is open", async function () {});
// `plain/other.feature` has no path tag, so it only passes if `storefront` is in the registry's defaults
const registry = createLibraryRegistry({ common: createStepLibrary(), storefront });

defineFeatureSuite({
  test,
  registry,
  features: path.join(path.dirname(fileURLToPath(import.meta.url)), "path-features"),
  defaultLibraries: ["common"],
});
