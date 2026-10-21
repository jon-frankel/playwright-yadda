const ORDINALS = ["first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth"];

/** "first" is 0, "second" is 1, and so on, for steps that name a row. */
export function indexOfOrdinal(word: string): number {
  const index = ORDINALS.indexOf(word.toLowerCase());
  if (index < 0) throw new Error(`Unknown ordinal "${word}" (use ${ORDINALS.join(", ")})`);
  return index;
}
