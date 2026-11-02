import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineFeatureSuite } from "playwright-yadda";
import { createActualContext } from "../steps/context.js";
import { registry } from "../steps/registry.js";
import { test } from "../steps/test.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// A second suite, because inheritance is an option of the whole suite.
defineFeatureSuite({
  test,
  registry,
  features: path.resolve(__dirname, "../overrides"),
  libraryInheritance: "override",
  createContext: createActualContext,
});
