import path from "node:path";
import type { AnnotationRecord } from "./types.js";

/**
 * Tags a feature file gets from its path: the `@`-prefixed directory names between `root` and the
 * file, and the file name itself when it starts with `@` (`@storefront.feature`,
 * `@mode:serial.feature`). The `@`, and any extensions after the first dot, are dropped, and
 * the case is kept. Segments outside `root` and the root itself are ignored.
 */
export function tagsFromPath(filepath: string, root: string): string[] {
  const relative = path.relative(path.resolve(root), path.resolve(filepath));
  if (relative === "" || relative.startsWith("..") || path.isAbsolute(relative)) return [];

  const segments = relative.split(path.sep);
  const fileName = segments.pop() ?? "";
  const tags: string[] = [];
  const add = (tag: string) => {
    if (tag && !tags.includes(tag)) tags.push(tag);
  };

  for (const directory of segments) {
    if (directory.startsWith("@")) add(directory.slice(1));
  }
  if (fileName.startsWith("@")) {
    // `@storefront.feature` and `@storefront.yadda.feature` both tag `storefront`
    const dot = fileName.indexOf(".");
    add(fileName.slice(1, dot === -1 ? undefined : dot));
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
