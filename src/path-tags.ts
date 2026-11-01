import path from "node:path";
import type { AnnotationRecord } from "./types.js";

const FEATURE_EXTENSION = /(\.yadda)?\.(feature|md|markdown)$/i;

/**
 * Tags a feature file gets from its path: the `@`-prefixed directory names between `root` and the
 * file, and the file name itself when it starts with `@` (`@storefront.feature`). The `@` and a
 * feature file's extension (`.feature`, `.md`, `.markdown`, optionally after `.yadda`) are dropped,
 * and the case is kept; a tag repeated in another case is listed once. Segments outside `root` and
 * the root itself are ignored.
 */
export function tagsFromPath(filepath: string, root: string): string[] {
  const relative = path.relative(path.resolve(root), path.resolve(filepath));
  if (
    relative === "" ||
    relative === ".." ||
    relative.startsWith(".." + path.sep) ||
    path.isAbsolute(relative)
  ) {
    return [];
  }

  const segments = relative.split(path.sep);
  const fileName = segments.pop() ?? "";
  const tags: string[] = [];
  const add = (tag: string) => {
    if (tag && !tags.some((known) => known.toLowerCase() === tag.toLowerCase())) tags.push(tag);
  };

  for (const directory of segments) {
    if (directory.startsWith("@")) add(directory.slice(1));
  }
  if (fileName.startsWith("@")) {
    // `@storefront.feature` and `@storefront.yadda.feature` both tag `storefront`
    add(fileName.slice(1).replace(FEATURE_EXTENSION, ""));
  }
  return tags;
}

/**
 * The annotations path tags stand for: each is a flag, like a bare `@tag` written in the feature
 * (keys are lower case, as the parser makes them).
 */
export function pathTagAnnotations(tags: readonly string[]): AnnotationRecord {
  return Object.fromEntries(tags.map((tag) => [tag.toLowerCase(), true]));
}
