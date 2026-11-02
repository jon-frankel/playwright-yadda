/**
 * The `column "value"` pairs of a step such as `the first transaction shows payee "Ikea" and debit
 * "100.00"`. A phrase with no pairs, or text between them that isn't just a connecting word, is a
 * typo, and throws: silently checking nothing would let the step pass.
 */
export function parseChecks(phrase: string): { column: string; expected: string }[] {
  const pairs = [...phrase.matchAll(/(\w+) "([^"]*)"/g)];
  const rest = phrase.replace(/(\w+) "([^"]*)"/g, "").replace(/\b(and)\b|[,\s]/g, "");
  if (pairs.length === 0 || rest) {
    throw new Error(`Cannot read the checks in: ${phrase} (expected: column "value", ...)`);
  }
  return pairs.map(([, column, expected]) => ({ column: column!, expected: expected! }));
}
