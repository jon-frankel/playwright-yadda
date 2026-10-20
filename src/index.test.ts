import { describe, expect, it } from "vitest";
import {
  createLibraryRegistry,
  createScenarioYadda,
  defineFeature,
  defineFeatureSuite,
  extractLibraryNames,
  parseFeature,
  resolveLibraries,
  runStep,
  UnknownLibraryError,
  VERSION,
} from "./index.js";

describe("playwright-yadda public exports", () => {
  it("should export VERSION and core functions", () => {
    expect(VERSION).toBe("0.0.4");
    expect(typeof createLibraryRegistry).toBe("function");
    expect(typeof resolveLibraries).toBe("function");
    expect(typeof UnknownLibraryError).toBe("function");
    expect(typeof createScenarioYadda).toBe("function");
    expect(typeof runStep).toBe("function");
    expect(typeof defineFeature).toBe("function");
    expect(typeof defineFeatureSuite).toBe("function");
    expect(typeof parseFeature).toBe("function");
    expect(typeof extractLibraryNames).toBe("function");
  });
});
