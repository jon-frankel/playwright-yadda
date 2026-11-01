import fs from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import Yadda from "yadda";
import { isScenarioOnly, isScenarioPending } from "./annotations.js";
import {
  BASE_FIXTURE_NAMES,
  collectScenarioFixtures,
  createFixtureTestBody,
  TEST_INFO_KEY,
  withScenarioFixtures,
} from "./fixtures.js";
import { createLibraryRegistry, LibraryRegistryManager } from "./registry.js";
import { createScenarioYadda, runScenarioSteps } from "./runner.js";
import type {
  DefaultScenarioContext,
  FeatureSuiteOptions,
  ILibraryRegistryManager,
  LibraryRegistry,
  ParsedFeature,
  ParsedScenario,
  ResolveLibrariesOptions,
} from "./types.js";

/**
 * Parse feature content using Gherkin or Markdown parser.
 *
 * `language` is the Yadda localisation the feature is written in, English unless given.
 */
export function parseFeature(
  content: string,
  parserType: "auto" | "gherkin" | "markdown" = "auto",
  filepath?: string,
  language?: Yadda.Language,
): ParsedFeature {
  let resolvedType = parserType;

  if (resolvedType === "auto") {
    if (filepath) {
      const ext = path.extname(filepath).toLowerCase();
      resolvedType = ext === ".md" || ext === ".markdown" ? "markdown" : "gherkin";
    } else {
      // Auto-detect based on markdown heading syntax, with the language's word for Feature
      const feature = (language ?? Yadda.localisation.English).translate("feature");
      resolvedType = new RegExp(`^\\s*#\\s+${feature}:`, "im").test(content)
        ? "markdown"
        : "gherkin";
    }
  }

  const parser =
    resolvedType === "markdown"
      ? new Yadda.parsers.MarkdownFeatureParser(language)
      : new Yadda.parsers.FeatureParser(language);

  const parsed = parser.parse(content) as ParsedFeature;
  if (filepath) parsed.filepath = path.resolve(filepath);
  return parsed;
}

/**
 * Load and parse a feature from a local file
 */
export function loadFeatureFile(
  filepath: string,
  parserType: "auto" | "gherkin" | "markdown" = "auto",
  language?: Yadda.Language,
): ParsedFeature {
  const content = fs.readFileSync(filepath, "utf8");
  return parseFeature(content, parserType, filepath, language);
}

/**
 * Scan a directory or file path for feature files (.feature, .md)
 */
export function findFeatureFiles(targetPath: string): string[] {
  if (!fs.existsSync(targetPath)) return [];

  const stat = fs.statSync(targetPath);
  if (stat.isFile()) {
    return [path.resolve(targetPath)];
  }

  const files: string[] = [];
  const entries = fs.readdirSync(targetPath, { recursive: true, withFileTypes: true });

  for (const entry of entries) {
    if (entry.isFile()) {
      const ext = path.extname(entry.name).toLowerCase();
      if (ext === ".feature" || ext === ".md" || ext === ".markdown") {
        const fullPath = path.resolve(entry.parentPath || targetPath, entry.name);
        files.push(fullPath);
      }
    }
  }

  return files.sort();
}

/**
 * Whether the installed Playwright accepts a details object (`{ annotation }`) in `test(title, details, fn)`,
 * which it has done since 1.42.
 */
function supportsTestDetails(): boolean {
  try {
    const { version } = createRequire(import.meta.url)("@playwright/test/package.json");
    const [major = 0, minor = 0] = String(version).split(".").map(Number);
    return major > 1 || (major === 1 && minor >= 42);
  } catch {
    return false;
  }
}

/** Test annotation type carrying the path of the feature file a scenario came from */
export const FEATURE_FILE_ANNOTATION = "feature-file";

/**
 * Define Playwright tests for a parsed feature
 */
export function defineFeature<TCtx = DefaultScenarioContext, K extends string = string>(
  feature: ParsedFeature,
  options: {
    test: any;
    registry: LibraryRegistry<K> | ILibraryRegistryManager<K>;
    defaultLibraries?: (K | string)[];
    libraryInheritance?: "merge" | "override";
    createContext?: (fixtures: any) => Promise<TCtx> | TCtx;
    onBeforeScenario?: (scenario: ParsedScenario, context: TCtx) => Promise<void> | void;
    onAfterScenario?: (scenario: ParsedScenario, context: TCtx) => Promise<void> | void;
  },
): void {
  const {
    test,
    registry: rawRegistry,
    defaultLibraries,
    libraryInheritance,
    createContext,
    onBeforeScenario,
    onAfterScenario,
  } = options;

  const registry: ILibraryRegistryManager<K> =
    rawRegistry instanceof LibraryRegistryManager ||
    typeof (rawRegistry as any).resolve === "function"
      ? (rawRegistry as ILibraryRegistryManager<K>)
      : createLibraryRegistry(rawRegistry as LibraryRegistry<K>, {
          defaultLibraries,
          inheritance: libraryInheritance,
        });

  const detailsSupported = supportsTestDetails();

  const resolveOpts: ResolveLibrariesOptions<K> = {};
  if (defaultLibraries !== undefined) resolveOpts.defaultLibraries = defaultLibraries;
  if (libraryInheritance !== undefined) resolveOpts.inheritance = libraryInheritance;

  /**
   * Fixtures to set up for a scenario: `page` and `context`, which the default scenario context is
   * made of, plus whatever the steps of the libraries it resolves destructure.
   */
  const scenarioFixtureNames = (parsedFeature: ParsedFeature, scenario: ParsedScenario) => {
    const names = new Set<string>(BASE_FIXTURE_NAMES);
    try {
      const libraries = registry.resolve(
        parsedFeature.annotations,
        scenario.annotations,
        resolveOpts,
      );
      for (const name of collectScenarioFixtures(libraries, scenario.steps)) names.add(name);
    } catch {
      // An unknown library is reported when the scenario runs, as it always has been
    }
    return [...names];
  };

  test.describe(`Feature: ${feature.title}`, () => {
    for (const scenario of feature.scenarios) {
      const isPending = isScenarioPending(feature.annotations, scenario.annotations);
      const isOnly = isScenarioOnly(feature.annotations, scenario.annotations);

      const kind = isPending ? "skip" : isOnly ? "only" : undefined;
      const declare = (kind ? test[kind] : test) as (...args: unknown[]) => void;
      // Attached when the test is declared, so reporters still see it if a fixture or hook fails
      // before the body runs, and for skipped scenarios whose body never does
      const attachUpFront = !!feature.filepath && detailsSupported;
      const details = {
        annotation: { type: FEATURE_FILE_ANNOTATION, description: feature.filepath },
      };
      const testFn = attachUpFront
        ? (title: string, body: unknown) => declare(title, details, body)
        : declare;

      // Playwright sets up the fixtures the test callback destructures, so the callback has to name
      // the ones this scenario's steps use. They are read from the steps' own parameters.
      const fixtureNames = scenarioFixtureNames(feature, scenario);

      const runScenario = async (stepFixtures: Record<string, any>, testInfo: any) => {
        const { page, context } = stepFixtures;
        const fixtures = { ...stepFixtures, testInfo };

        // Older Playwright cannot take it at declaration; this is the best it can do
        if (feature.filepath && !attachUpFront) {
          testInfo?.annotations?.push({
            type: FEATURE_FILE_ANNOTATION,
            description: feature.filepath,
          });
        }

        // 1. Resolve step libraries dynamically for this specific scenario
        const libraries = registry.resolve(feature.annotations, scenario.annotations, resolveOpts);

        // 2. Build isolated Yadda runner instance
        const yadda = createScenarioYadda(libraries);

        // 3. Create context for this scenario
        const ctx: TCtx = createContext
          ? await createContext(fixtures)
          : ({
              page,
              context,
              testInfo,
            } as unknown as TCtx);

        // 4. Lifecycle before hook
        if (onBeforeScenario) {
          await onBeforeScenario(scenario, ctx);
        }

        // 5. Execute steps wrapped in test.step()
        await withScenarioFixtures({ ...stepFixtures, [TEST_INFO_KEY]: testInfo }, () =>
          runScenarioSteps(yadda, scenario.steps, ctx, test.step),
        );

        // 6. Lifecycle after hook
        if (onAfterScenario) {
          await onAfterScenario(scenario, ctx);
        }
      };

      testFn(
        `Scenario: ${scenario.title}`,
        createFixtureTestBody(fixtureNames, runScenario) as unknown,
      );
    }
  });
}

/**
 * High-level Playwright suite builder: discovers, parses, and executes features
 */
export function defineFeatureSuite<TCtx = DefaultScenarioContext, K extends string = string>(
  options: FeatureSuiteOptions<any, any, TCtx, K>,
): void {
  const { features: inputFeatures, parserType = "auto", language } = options;

  const parsedFeatures: ParsedFeature[] = [];

  const targets = Array.isArray(inputFeatures) ? inputFeatures : [inputFeatures];

  for (const target of targets) {
    if (typeof target !== "string") {
      // Pre-parsed feature object
      parsedFeatures.push(target);
    } else {
      // File path or directory
      const files = findFeatureFiles(target);
      if (files.length === 0 && fs.existsSync(target) && fs.statSync(target).isFile()) {
        files.push(path.resolve(target));
      }

      for (const file of files) {
        parsedFeatures.push(loadFeatureFile(file, parserType, language));
      }
    }
  }

  for (const feature of parsedFeatures) {
    defineFeature(feature, options);
  }
}
