import type { AnnotationRecord, ResolveLibrariesOptions } from "./types.js";

/**
 * Find annotation value case-insensitively
 */
export function getAnnotationValue(
  annotations: AnnotationRecord | undefined,
  targetKey: string,
): string | boolean | undefined {
  if (!annotations || typeof annotations !== "object") return undefined;

  const normalizedTarget = targetKey.trim().toLowerCase();
  for (const [key, value] of Object.entries(annotations)) {
    if (key.trim().toLowerCase() === normalizedTarget) {
      return value;
    }
  }

  return undefined;
}

/**
 * Extract an array of library names from an annotation record
 */
export function extractLibraryNames(
  annotations: AnnotationRecord | undefined,
  annotationKey = "libraries",
): string[] {
  const rawValue = getAnnotationValue(annotations, annotationKey);
  if (typeof rawValue !== "string") return [];

  return rawValue
    .split(",")
    .map((item) => item.trim().toLowerCase())
    .filter((item) => item.length > 0);
}

/**
 * Resolve the final ordered list of library names from feature & scenario annotations
 */
export function resolveLibraryNames(
  featureAnnotations?: AnnotationRecord,
  scenarioAnnotations?: AnnotationRecord,
  options: ResolveLibrariesOptions = {},
): string[] {
  const {
    defaultLibraries = ["common"],
    inheritance = "merge",
    annotationKey = "libraries",
  } = options;

  const result = new Set<string>();

  // 1. Add default libraries
  for (const lib of defaultLibraries) {
    const trimmed = String(lib).trim().toLowerCase();
    if (trimmed) result.add(trimmed);
  }

  const featureLibs = extractLibraryNames(featureAnnotations, annotationKey);
  const scenarioLibs = extractLibraryNames(scenarioAnnotations, annotationKey);

  if (inheritance === "override" && scenarioLibs.length > 0) {
    // Override: scenario libraries replace feature libraries
    for (const lib of scenarioLibs) {
      result.add(lib);
    }
  } else {
    // Merge: scenario libraries are additive to feature libraries
    for (const lib of featureLibs) {
      result.add(lib);
    }
    for (const lib of scenarioLibs) {
      result.add(lib);
    }
  }

  return Array.from(result);
}

/**
 * Checks whether a scenario should be skipped/pending based on annotations
 */
export function isScenarioPending(
  featureAnnotations?: AnnotationRecord,
  scenarioAnnotations?: AnnotationRecord,
): boolean {
  const check = (annotations?: AnnotationRecord) => {
    if (!annotations) return false;
    const pending = getAnnotationValue(annotations, "pending");
    const skip = getAnnotationValue(annotations, "skip");
    return (
      Boolean(pending !== undefined && pending !== false) ||
      Boolean(skip !== undefined && skip !== false)
    );
  };

  return check(featureAnnotations) || check(scenarioAnnotations);
}

/**
 * Checks whether a scenario has an `@only` annotation
 */
export function isScenarioOnly(
  featureAnnotations?: AnnotationRecord,
  scenarioAnnotations?: AnnotationRecord,
): boolean {
  const check = (annotations?: AnnotationRecord) => {
    if (!annotations) return false;
    const only = getAnnotationValue(annotations, "only");
    return Boolean(only !== undefined && only !== false);
  };

  return check(featureAnnotations) || check(scenarioAnnotations);
}
