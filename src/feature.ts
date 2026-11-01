import fs from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import Yadda from "yadda";
import {
  BASE_FIXTURE_NAMES,
  createFixtureCollector,
  type FixtureCollector,
  createFixtureTestBody,
  TEST_INFO_KEY,
  usesFixturesMode,
  withScenarioFixtures,
} from "./fixtures.js";
import { extractLibraryNames } from "./annotations.js";
import { createLibraryRegistry, LibraryRegistryManager } from "./registry.js";
import { createScenarioYadda, runScenarioSteps } from "./runner.js";
import { resolveTags, type ResolvedTags } from "./tags.js";
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
 * Tags a feature file's path gives: every `@`-prefixed directory below `root`, and the file's own
 * name when it starts with `@` (`@smoke.feature`, `@smoke.yadda.feature`). Returned without the `@`,
 * outermost first, so `features/@game/@mode:serial/x.feature` gives `["game", "mode:serial"]`.
 * Without a `root` only the file name counts.
 */
export function pathTagsOf(filepath: string, root?: string): string[] {
  const absolute = path.resolve(filepath);
  let relative = root ? path.relative(path.resolve(root), absolute) : path.basename(absolute);
  // A file that is not under the root (or is on another drive) has no directories of the root's to count
  if (relative === ".." || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    relative = path.basename(absolute);
  }
  const segments = relative.split(path.sep).filter(Boolean);
  const file = segments.pop();
  const names = segments.filter((segment) => segment.startsWith("@"));
  if (file?.startsWith("@")) {
    names.push(file.replace(/(\.yadda)?\.(feature|md|markdown)$/i, ""));
  }
  const tags: string[] = [];
  for (const name of names) {
    const tag = name.slice(1).trim().toLowerCase();
    if (tag && !tags.includes(tag)) tags.push(tag);
  }
  return tags;
}

/** The part of a tag before a `:` or `=`, which is what an explicit tag of the same name overrides */
const tagName = (tag: string) => tag.split(/[:=]/)[0]!.trim().toLowerCase();

/**
 * Add path tags to a feature. A tag written in the file wins over a path tag of the same name
 * (`@mode:parallel` in the file beats `@mode:serial/` in the path), and libraries from both are used.
 */
function applyPathTags(feature: ParsedFeature, tags: string[]): void {
  if (tags.length === 0) return;
  feature.pathTags = tags;
  const annotations: Record<string, string | boolean | undefined> = { ...feature.annotations };
  const explicit = new Set(Object.keys(annotations).map(tagName));
  for (const tag of tags) {
    if (explicit.has(tagName(tag))) continue;
    // `@timeout=5000/` is the spelling for systems that do not allow `:` in a file name
    const equals = tag.indexOf("=");
    if (equals > 0) annotations[tag.slice(0, equals).trim()] = tag.slice(equals + 1).trim() || true;
    else annotations[tag] = true;
  }
  feature.annotations = annotations;
}

/**
 * Load and parse a feature from a local file. With a `root`, the `@` directories between it and the
 * file tag the feature, and so does an `@` file name; see `pathTagsOf`.
 */
export function loadFeatureFile(
  filepath: string,
  parserType: "auto" | "gherkin" | "markdown" = "auto",
  language?: Yadda.Language,
  root?: string,
): ParsedFeature {
  const content = fs.readFileSync(filepath, "utf8");
  const feature = parseFeature(content, parserType, filepath, language);
  applyPathTags(feature, pathTagsOf(filepath, root));
  return feature;
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

/** Apply `@retries`, `@mode` and `@timeout` to the describe being declared */
function configureDescribe(test: any, settings: ResolvedTags["feature"]): void {
  const options = Object.fromEntries(
    Object.entries(settings).filter(([, value]) => value !== undefined),
  );
  if (Object.keys(options).length > 0) test.describe.configure(options);
}

/** Problems already warned about by this process, so each is reported once */
const warned = new Set<string>();

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
    /**
     * Works out which fixtures a scenario's steps use. `defineFeatureSuite` makes one and shares it
     * between its features, so a step line is matched once for the suite; pass one when defining
     * several features yourself. Each call makes its own if it is not given one.
     */
    fixtureCollector?: FixtureCollector;
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
    fixtureCollector,
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
   * The feature's annotations, with the path tags that name a registered library added to its
   * `@Libraries`. They count as the feature's own, so a scenario's `@Libraries` merges with them or,
   * with `override` inheritance, replaces them.
   */
  const annotationsOf = (parsedFeature: ParsedFeature) => {
    const named = (parsedFeature.pathTags ?? []).filter((tag) => registry.has(tag));
    if (named.length === 0) return parsedFeature.annotations;
    // The key `registry.resolve` reads: its own option, unless the suite overrides it
    const key = String(
      resolveOpts.annotationKey ?? (registry as any).options?.annotationKey ?? "libraries",
    )
      .trim()
      .toLowerCase();
    const annotations = { ...parsedFeature.annotations };
    const existing = extractLibraryNames(annotations, key);
    for (const name of named) if (!existing.includes(name)) existing.push(name);
    for (const k of Object.keys(annotations))
      if (k.trim().toLowerCase() === key) delete annotations[k];
    annotations[key] = existing.join(", ");
    return annotations;
  };

  const featureAnnotations = annotationsOf(feature);

  /**
   * Fixtures to set up for a scenario: `page` and `context`, which the default scenario context is
   * made of, plus whatever the steps of the libraries it resolves destructure.
   */
  const collectFixtures = fixtureCollector ?? createFixtureCollector();
  const usesFixtures = () =>
    registry.getAvailableKeys().some((key) => {
      const library = registry.get(key);
      return library !== undefined && usesFixturesMode(library);
    });
  const scenarioFixtureNames = (parsedFeature: ParsedFeature, scenario: ParsedScenario) => {
    const names = new Set<string>(BASE_FIXTURE_NAMES);
    try {
      const libraries = registry.resolve(
        parsedFeature === feature ? featureAnnotations : annotationsOf(parsedFeature),
        scenario.annotations,
        resolveOpts,
      );
      for (const name of collectFixtures(libraries, scenario.steps)) names.add(name);
    } catch (error) {
      // The scenario fails with this same error when it runs. Say why its fixtures are only the
      // base ones, which would otherwise show up as a confusing "fixture not found" first. Only
      // suites that use fixtures mode need telling, and once per problem: every Playwright process
      // that loads the spec declares every scenario.
      if (usesFixtures()) {
        const reason = error instanceof Error ? error.message : String(error);
        if (!warned.has(reason)) {
          warned.add(reason);
          console.warn(
            `playwright-yadda: could not work out the fixtures for "${parsedFeature.title}" > ` +
              `"${scenario.title}" (nor any other scenario with this problem), so only ` +
              `${BASE_FIXTURE_NAMES.join(" and ")} are set up for them: ${reason}`,
          );
        }
      }
    }
    return [...names];
  };

  const featureSettings = resolveTags(feature.annotations, undefined, {
    where: `"${feature.title}"`,
  }).feature;

  test.describe(`Feature: ${feature.title}`, () => {
    configureDescribe(test, featureSettings);

    for (const scenario of feature.scenarios) {
      const { scenario: tags, own } = resolveTags(feature.annotations, scenario.annotations, {
        where: `"${feature.title}" > "${scenario.title}"`,
        featureWarnings: false,
      });

      // `@skip` and `@pending` skip, and win over `@fixme` (which only differs in how Playwright
      // reports it) and `@only`
      const kind = tags.skip
        ? tags.skip === "fixme" && test.fixme
          ? "fixme"
          : "skip"
        : tags.only
          ? "only"
          : undefined;
      const declare = (kind ? test[kind] : test) as (...args: unknown[]) => void;
      // Attached when the test is declared, so reporters still see it if a fixture or hook fails
      // before the body runs, and for skipped scenarios whose body never does
      const attachFile = !!feature.filepath;
      const details: { annotation?: unknown; tag?: string[] } = {};
      if (attachFile && detailsSupported) {
        details.annotation = { type: FEATURE_FILE_ANNOTATION, description: feature.filepath };
      }
      // Every tag goes to Playwright, so `--grep @smoke` and `--grep-invert @slow` select by them
      if (detailsSupported && tags.playwrightTags.length > 0) details.tag = tags.playwrightTags;
      const attachUpFront = Object.keys(details).length > 0;
      const attachLate = attachFile && !detailsSupported;
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
        if (attachLate) {
          testInfo?.annotations?.push({
            type: FEATURE_FILE_ANNOTATION,
            description: feature.filepath,
          });
        }

        // 1. Resolve step libraries dynamically for this specific scenario
        const libraries = registry.resolve(featureAnnotations, scenario.annotations, resolveOpts);

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

      const declareScenario = () =>
        testFn(
          `Scenario: ${scenario.title}`,
          createFixtureTestBody(fixtureNames, runScenario) as unknown,
        );

      // `@fail`, `@slow`, `@timeout` and `@retries` are given to Playwright in a describe of the
      // scenario's own, with no title (microsoft/playwright#10825). Calling `test.fail()` or
      // `test.slow()` in the running test would be too late for its fixtures and `beforeEach`
      // hooks, which Playwright has set up by then, and a describe's settings cover them. A
      // feature's `@timeout` and `@retries` are on the feature's describe, which this one inherits.
      if (
        !tags.skip &&
        (tags.fail || tags.slow || own.timeout !== undefined || own.retries !== undefined)
      ) {
        test.describe(() => {
          configureDescribe(test, { timeout: own.timeout, retries: own.retries });
          if (tags.fail) test.fail();
          if (tags.slow) test.slow();
          declareScenario();
        });
      } else {
        declareScenario();
      }
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

      // A directory is the root `@` directories are counted from; for a single file only its name is
      const root = fs.existsSync(target) && fs.statSync(target).isDirectory() ? target : undefined;
      for (const file of files) {
        parsedFeatures.push(loadFeatureFile(file, parserType, language, root));
      }
    }
  }

  // One for the whole suite: steps repeat across feature files, and matching them is the costly part
  const fixtureCollector = createFixtureCollector();
  for (const feature of parsedFeatures) {
    defineFeature(feature, { ...options, fixtureCollector });
  }
}
