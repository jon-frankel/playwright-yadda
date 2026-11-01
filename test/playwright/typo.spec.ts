import { test } from "@playwright/test";
import {
  createStepLibrary,
  createLibraryRegistry,
  defineFeatureSuite,
  parseFeature,
} from "../../src/index.ts";

const steps = createStepLibrary(undefined, "fixtures");
// "pgae" is not a fixture of this test
steps.given("a typo", async ({ pgae }) => pgae);

defineFeatureSuite({
  test,
  registry: createLibraryRegistry({ steps }, { defaultLibraries: [] }),
  features: [parseFeature("@Libraries=steps\nFeature: Typo\n  Scenario: S\n    Given a typo\n")],
});
