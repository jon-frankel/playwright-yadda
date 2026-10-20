import fs from "node:fs";
import path from "node:path";
import Yadda from "yadda";
import { isScenarioOnly, isScenarioPending } from "./annotations.js";
import { createLibraryRegistry, LibraryRegistryManager } from "./registry.js";
import { createScenarioYadda, runScenarioSteps } from "./runner.js";
import type {
  DefaultScenarioContext,
  FeatureSuiteOptions,
  ILibraryRegistryManager,
  LibraryRegistry,
  ParsedFeature,
  ParsedScenario,
} from "./types.js";

/**
 * Parse feature content using Gherkin or Markdown parser
 */
export function parseFeature(
  content: string,
  parserType: "auto" | "gherkin" | "markdown" = "auto",
  filepath?: string,
): ParsedFeature {
  let resolvedType = parserType;

  if (resolvedType === "auto") {
    if (filepath) {
      const ext = path.extname(filepath).toLowerCase();
      resolvedType = ext === ".md" || ext === ".markdown" ? "markdown" : "gherkin";
    } else {
      // Auto-detect based on markdown heading syntax
      resolvedType = /^\s*#\s+Feature:/im.test(content) ? "markdown" : "gherkin";
    }
  }

  const parser =
    resolvedType === "markdown"
      ? new Yadda.parsers.MarkdownFeatureParser()
      : new Yadda.parsers.FeatureParser();

  return parser.parse(content) as ParsedFeature;
}

/**
 * Load and parse a feature from a local file
 */
export function loadFeatureFile(
  filepath: string,
  parserType: "auto" | "gherkin" | "markdown" = "auto",
): ParsedFeature {
  const content = fs.readFileSync(filepath, "utf8");
  return parseFeature(content, parserType, filepath);
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

  test.describe(`Feature: ${feature.title}`, () => {
    for (const scenario of feature.scenarios) {
      const isPending = isScenarioPending(feature.annotations, scenario.annotations);
      const isOnly = isScenarioOnly(feature.annotations, scenario.annotations);

      let testFn = test;
      if (isPending) {
        testFn = test.skip;
      } else if (isOnly) {
        testFn = test.only;
      }

      testFn(`Scenario: ${scenario.title}`, async ({ page, context }: any, testInfo: any) => {
        const fixtures = { page, context, testInfo };

        // 1. Resolve step libraries dynamically for this specific scenario
        const libraries = registry.resolve(feature.annotations, scenario.annotations, {
          defaultLibraries,
          inheritance: libraryInheritance,
        });

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
        await runScenarioSteps(yadda, scenario.steps, ctx, test.step);

        // 6. Lifecycle after hook
        if (onAfterScenario) {
          await onAfterScenario(scenario, ctx);
        }
      });
    }
  });
}

/**
 * High-level Playwright suite builder: discovers, parses, and executes features
 */
export function defineFeatureSuite<TCtx = DefaultScenarioContext, K extends string = string>(
  options: FeatureSuiteOptions<any, any, TCtx, K>,
): void {
  const { features: inputFeatures, parserType = "auto" } = options;

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
        parsedFeatures.push(loadFeatureFile(file, parserType));
      }
    }
  }

  for (const feature of parsedFeatures) {
    defineFeature(feature, options);
  }
}
