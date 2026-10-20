import path from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "@playwright/test";
import { defineFeatureSuite } from "playwright-yadda";
import { registry } from "../steps/registry.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const featuresDir = path.resolve(__dirname, "../features");

defineFeatureSuite({
  test,
  registry,
  features: featuresDir,
});
