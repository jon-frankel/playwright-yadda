import path from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "@playwright/test";
import { defineFeatureSuite } from "playwright-yadda";
import { registry } from "../steps/registry.js";
import { disableAnimations } from "../steps/support/animations.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

defineFeatureSuite({
  test,
  registry,
  features: path.resolve(__dirname, "../features"),
  onBeforeScenario: (_scenario, ctx) => disableAnimations(ctx.page),
});
