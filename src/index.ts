/**
 * playwright-yadda
 * Dynamic step library loading for Playwright and Yadda BDD
 */

export const VERSION = "0.0.4";

// Type definitions
export type {
  AnnotationRecord,
  DefaultScenarioContext,
  FeatureSuiteOptions,
  LibraryRegistry,
  ParsedFeature,
  ParsedScenario,
  ResolveLibrariesOptions,
  StepLibrary,
} from "./types.js";

// Annotation parsing utilities
export {
  extractLibraryNames,
  getAnnotationValue,
  isScenarioOnly,
  isScenarioPending,
  resolveLibraryNames,
} from "./annotations.js";

// Library Registry
export {
  createLibraryRegistry,
  createStepLibrary,
  LibraryRegistryManager,
  resolveLibraries,
  UnknownLibraryError,
} from "./registry.js";

// Step & Scenario Runners
export { createScenarioYadda, runScenarioSteps, runStep, type TestStepWrapper } from "./runner.js";

// Feature and Suite Definitions
export {
  defineFeature,
  defineFeatureSuite,
  findFeatureFiles,
  loadFeatureFile,
  parseFeature,
} from "./feature.js";
