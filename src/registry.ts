import Yadda from "yadda";
import { resolveLibraryNames } from "./annotations.js";
import type {
  AnnotationRecord,
  LibraryRegistry,
  ResolveLibrariesOptions,
  StepLibrary,
} from "./types.js";

/**
 * Creates an English Gherkin-localised step library that natively supports
 * Given, When, Then, And, But prefixes with context-binding support.
 */
export function createStepLibrary(
  dictionary?: Yadda.Dictionary,
  mode: "bound" | "param" = "bound",
): Yadda.Library {
  const base =
    mode === "param"
      ? new Yadda.ContextParamLibrary(dictionary)
      : new Yadda.ContextBoundLibrary(dictionary);
  return Yadda.localisation.English.localise(base as any) as Yadda.Library;
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
