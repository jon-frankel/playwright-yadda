import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineFeatureSuite } from "playwright-yadda";
import { hooks } from "../steps/hooks.js";
import { registry } from "../steps/registry.js";
import { test } from "../steps/test.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

defineFeatureSuite({
  test,
  registry,
  features: path.resolve(__dirname, "../features"),
  hooks,
});
