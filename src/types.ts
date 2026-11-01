import type {
  Page,
  BrowserContext,
  TestType,
  PlaywrightTestArgs,
  PlaywrightTestOptions,
  PlaywrightWorkerArgs,
  PlaywrightWorkerOptions,
} from "@playwright/test";
import type Yadda from "yadda";

/**
 * Standard Yadda Step Library type
 */
export type StepLibrary = Yadda.Library | Yadda.ContextParamLibrary;

/**
 * Strong-typed dictionary of step libraries mapped by domain key (e.g. auth, catalog, checkout)
 */
export type LibraryRegistry<K extends string = string> = Record<K, StepLibrary>;

/**
 * Interface implemented by LibraryRegistryManager
 */
export interface ILibraryRegistryManager<K extends string = string> {
  resolve(
    featureAnnotations?: AnnotationRecord,
    scenarioAnnotations?: AnnotationRecord,
    options?: ResolveLibrariesOptions<K>,
  ): StepLibrary[];
  getAvailableKeys(): string[];
  has(name: string): boolean;
  get(name: string): StepLibrary | undefined;
}

/**
 * Raw annotation object parsed from Yadda features or scenarios
 */
export type AnnotationRecord = Record<string, string | boolean | undefined>;

/**
 * Resolution configuration options
 */
export interface ResolveLibrariesOptions<K extends string = string> {
  /**
   * Library keys that are always included by default (unless explicitly disabled)
   * @default ['common']
   */
  defaultLibraries?: (K | string)[];

  /**
   * How scenario-level @Libraries interact with feature-level @Libraries
   * - 'merge': scenario libraries are appended to feature libraries (default)
   * - 'override': scenario libraries completely replace feature libraries
   * @default 'merge'
   */
  inheritance?: "merge" | "override";

  /**
   * Annotation key to inspect for step libraries
   * @default 'libraries'
   */
  annotationKey?: string;
}

/**
 * Default context injected into every scenario step
 */
export interface DefaultScenarioContext {
  page: Page;
  context: BrowserContext;
  [key: string]: unknown;
}

/**
 * Parsed Gherkin/Markdown scenario representation
 */
export interface ParsedScenario {
  title: string;
  annotations?: AnnotationRecord;
  description?: string[];
  steps: string[];
}

/**
 * Parsed Gherkin/Markdown feature representation
 */
export interface ParsedFeature {
  title: string;
  /** Path of the file the feature was loaded from, when it came from one */
  filepath?: string;
  annotations?: AnnotationRecord;
  /**
   * Tags taken from `@`-prefixed directory and file names in the feature's path (without the `@`).
   * Those that name a registered library also select it.
   */
  pathTags?: string[];
  description?: string[];
  scenarios: ParsedScenario[];
  rules?: unknown[];
}

/**
 * Configuration options for `defineFeatureSuite`
 */
export interface FeatureSuiteOptions<
  TFixtures extends PlaywrightTestArgs & PlaywrightTestOptions = PlaywrightTestArgs &
    PlaywrightTestOptions,
  TWorkerFixtures extends PlaywrightWorkerArgs & PlaywrightWorkerOptions = PlaywrightWorkerArgs &
    PlaywrightWorkerOptions,
  TCtx = DefaultScenarioContext,
  K extends string = string,
> {
  /**
   * Playwright `test` instance to register suites and steps with
   */
  test: TestType<TFixtures, TWorkerFixtures>;

  /**
   * Registry containing all available step libraries
   */
  registry: LibraryRegistry<K> | ILibraryRegistryManager<K>;

  /**
   * Feature files to run: glob pattern, file path, array of file paths, or pre-parsed features
   */
  features: string | string[] | ParsedFeature[];

  /**
   * Parser to use when loading string file paths
   * - 'auto': detects based on file extension (.md -> markdown, .feature -> gherkin)
   * - 'gherkin': forces standard FeatureParser
   * - 'markdown': forces MarkdownFeatureParser
   * @default 'auto'
   */
  parserType?: "auto" | "gherkin" | "markdown";

  /**
   * Yadda localisation the feature files are written in, for example `Yadda.localisation.French`.
   * It sets the words the parser reads as `Feature:`, `Scenario:`, `Examples:` and so on, and applies to
   * the files this suite loads from a path; pre-parsed features are used as they are. The step
   * libraries need the same language (`createStepLibrary(dictionary, mode, language)`).
   * @default Yadda.localisation.English
   */
  language?: Yadda.Language;

  /**
   * Library keys to include for every scenario by default
   * @default ['common']
   */
  defaultLibraries?: (K | string)[];

  /**
   * How scenario-level @Libraries interact with feature-level @Libraries
   * @default 'merge'
   */
  libraryInheritance?: "merge" | "override";

  /**
   * Custom context factory to build the context passed to each step
   */
  createContext?: (fixtures: TFixtures & { context: BrowserContext }) => Promise<TCtx> | TCtx;

  /**
   * Optional hook executed before the scenario steps run
   */
  onBeforeScenario?: (scenario: ParsedScenario, context: TCtx) => Promise<void> | void;

  /**
   * Optional hook executed after all scenario steps complete
   */
  onAfterScenario?: (scenario: ParsedScenario, context: TCtx) => Promise<void> | void;
}

/**
 * Options for the `playwright-yadda/reporter` Playwright reporter
 */
export interface YaddaReporterOptions {
  /** Keep yadda and playwright-yadda frames in stack traces. Default: false. */
  fullStackTrace?: boolean;
}
