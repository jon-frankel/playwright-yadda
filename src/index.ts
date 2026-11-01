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
  YaddaReporterOptions,
} from "./types.js";

// Annotation parsing utilities
export {
  extractLibraryNames,
  getAnnotationValue,
  isScenarioOnly,
  isScenarioPending,
  resolveLibraryNames,
} from "./annotations.js";

// Playwright fixtures in steps
export {
  createFixtureCollector,
  FixtureDeclarationError,
  parseFixtureNames,
  type FixtureCollector,
  type FixtureStepFunction,
  type FixtureStepLibrary,
  type FixturesOf,
  type StepFixtures,
} from "./fixtures.js";

// Tags from `@` directory and file names
export { pathTagAnnotations, tagsFromPath } from "./path-tags.js";

// Library Registry
export {
  createBdd,
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
