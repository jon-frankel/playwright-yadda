import type { TestType } from "@playwright/test";
import Yadda from "yadda";
import { resolveLibraryNames } from "./annotations.js";
import {
  bindFixtureStep,
  markFixturesLibrary,
  parseFixtureNames,
  recordStepFixtures,
  type FixtureStepLibrary,
  type FixturesOf,
} from "./fixtures.js";
import type {
  AnnotationRecord,
  LibraryRegistry,
  ResolveLibrariesOptions,
  StepLibrary,
} from "./types.js";

/**
 * Creates a Gherkin-localised step library that natively supports
 * Given, When, Then, And, But prefixes with context-binding support.
 *
 * `mode` decides what a step receives:
 * - `'bound'` (default): the scenario context as `this`.
 * - `'param'`: the scenario context as the first argument.
 * - `'fixtures'`: the scenario's Playwright fixtures as the first argument, which the step
 *   destructures (`async ({ page, myFixture }, arg) => ...`). Only the fixtures that the steps a
 *   scenario uses destructure are set up for it. See `createBdd` for typed fixtures.
 *
 * The prefixes are English unless `language` is another of Yadda's
 * localisations (`Yadda.localisation.French`, and so on). A feature suite has to
 * be told the same language (`defineFeatureSuite({ language })`) to read the
 * feature files' headings in it.
 */
export function createStepLibrary(
  dictionary?: Yadda.Dictionary,
  mode?: "bound" | "param",
  language?: Yadda.Language,
): Yadda.Library;
export function createStepLibrary(
  dictionary: Yadda.Dictionary | undefined,
  mode: "fixtures",
  language?: Yadda.Language,
): FixtureStepLibrary<Record<string, any>>;
export function createStepLibrary(
  dictionary: Yadda.Dictionary | undefined,
  mode: "bound" | "param" | "fixtures",
  language?: Yadda.Language,
): Yadda.Library | FixtureStepLibrary<Record<string, any>>;
export function createStepLibrary(
  dictionary?: Yadda.Dictionary,
  mode: "bound" | "param" | "fixtures" = "bound",
  language: Yadda.Language = Yadda.localisation.English,
): Yadda.Library | FixtureStepLibrary<Record<string, any>> {
  const base =
    mode === "bound"
      ? new Yadda.ContextBoundLibrary(dictionary)
      : new Yadda.ContextParamLibrary(dictionary);

  if (mode === "fixtures") {
    markFixturesLibrary(base);
    const define = base.define.bind(base);
    base.define = (signatures, fn, macroContext, options) => {
      if (!fn) return define(signatures, fn, macroContext, options);
      let names: string[];
      try {
        names = parseFixtureNames(fn);
      } catch (error) {
        if (error instanceof Error) error.message += ` (step: ${String(signatures)})`;
        throw error;
      }
      // Recorded once Yadda has accepted it, so a refused duplicate cannot overwrite the original
      const library = define(signatures, bindFixtureStep(fn, names), macroContext, options);
      recordStepFixtures(base, signatures, names);
      return library;
    };
  }

  return language.localise(base as any) as any;
}

/**
 * Bind step libraries to a Playwright `test` (usually one built with `test.extend({...})`), so
 * the fixtures a step destructures are typed from it:
 *
 * ```ts
 * const { createStepLibrary } = createBdd(test);
 * export const steps = createStepLibrary();
 * steps.given("a signed-in user", async ({ page, account }) => { ... });
 * ```
 *
 * `test` is used for its type only; the suite still needs the same `test` in `defineFeatureSuite`.
 */
export function createBdd<T extends TestType<any, any>>(
  _test: T,
): {
  createStepLibrary: (
    dictionary?: Yadda.Dictionary,
    language?: Yadda.Language,
  ) => FixtureStepLibrary<FixturesOf<T>>;
} {
  return {
    createStepLibrary: (dictionary, language) =>
      createStepLibrary(dictionary, "fixtures", language) as unknown as FixtureStepLibrary<
        FixturesOf<T>
      >,
  };
}

/**
 * Custom error thrown when an unknown library is requested in @Libraries
 */
export class UnknownLibraryError extends Error {
  public readonly requestedLibrary: string;
  public readonly availableLibraries: string[];

  constructor(requestedLibrary: string, availableLibraries: string[]) {
    const list = availableLibraries.length > 0 ? availableLibraries.join(", ") : "<none>";
    super(
      `Unknown step library "${requestedLibrary}". Available libraries in registry: [${list}].`,
    );
    this.name = "UnknownLibraryError";
    this.requestedLibrary = requestedLibrary;
    this.availableLibraries = availableLibraries;
  }
}

/**
 * Strongly-typed Library Registry Manager
 */
export class LibraryRegistryManager<K extends string = string> {
  private readonly normalizedMap: Map<string, StepLibrary> = new Map();
  private readonly originalKeys: string[] = [];
  public readonly options: ResolveLibrariesOptions<K>;

  constructor(libraries: LibraryRegistry<K>, options: ResolveLibrariesOptions<NoInfer<K>> = {}) {
    this.options = options;

    for (const [key, library] of Object.entries(libraries)) {
      this.originalKeys.push(key);
      this.normalizedMap.set(key.trim().toLowerCase(), library as StepLibrary);
    }
  }

  /**
   * Return a list of all registered library keys
   */
  public getAvailableKeys(): string[] {
    return [...this.originalKeys];
  }

  /**
   * Check if a library exists by key (case-insensitive)
   */
  public has(name: string): boolean {
    return this.normalizedMap.has(name.trim().toLowerCase());
  }

  /**
   * Get a library instance by key (case-insensitive)
   */
  public get(name: string): StepLibrary | undefined {
    return this.normalizedMap.get(name.trim().toLowerCase());
  }

  /**
   * Resolve step library instances based on feature and scenario annotations
   */
  public resolve(
    featureAnnotations?: AnnotationRecord,
    scenarioAnnotations?: AnnotationRecord,
    overrideOptions?: ResolveLibrariesOptions<K>,
  ): StepLibrary[] {
    const cleanOverrides = Object.fromEntries(
      Object.entries(overrideOptions ?? {}).filter(([, val]) => val !== undefined),
    ) as ResolveLibrariesOptions<K>;

    const mergedOptions: ResolveLibrariesOptions<K> = {
      ...this.options,
      ...cleanOverrides,
    };

    const requestedNames = resolveLibraryNames(
      featureAnnotations,
      scenarioAnnotations,
      mergedOptions,
    );

    const resolvedLibraries: StepLibrary[] = [];

    for (const name of requestedNames) {
      const lib = this.normalizedMap.get(name);
      if (!lib) {
        throw new UnknownLibraryError(name, this.originalKeys);
      }
      resolvedLibraries.push(lib);
    }

    return resolvedLibraries;
  }
}

/**
 * Factory to create a strongly-typed LibraryRegistryManager
 */
export function createLibraryRegistry<K extends string = string>(
  libraries: LibraryRegistry<K>,
  options?: ResolveLibrariesOptions<NoInfer<K>>,
): LibraryRegistryManager<K> {
  return new LibraryRegistryManager(libraries, options);
}

/**
 * Standalone helper to resolve libraries from an ad-hoc registry record
 */
export function resolveLibraries<K extends string = string>(
  registry: LibraryRegistry<K> | LibraryRegistryManager<K>,
  featureAnnotations?: AnnotationRecord,
  scenarioAnnotations?: AnnotationRecord,
  options?: ResolveLibrariesOptions<NoInfer<K>>,
): StepLibrary[] {
  if (registry instanceof LibraryRegistryManager) {
    return registry.resolve(featureAnnotations, scenarioAnnotations, options);
  }

  const manager = new LibraryRegistryManager(registry, options);
  return manager.resolve(featureAnnotations, scenarioAnnotations, options);
}
