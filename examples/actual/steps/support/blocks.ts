// Yadda hands a multi-line step to its definition as one string: the step's first line, a newline,
// then the lines between the two `---` fences.

/** The fenced block of a step; a blank one is as good as none. */
export function requireBlock(block: string, step: string): string {
  if (!block.trim()) throw new Error(`"${step}" needs a block between two --- lines`);
  return block;
}

/** `| a | b |` rows, the first row naming the columns. A blank cell is an empty string. */
export function parseTable(block: string): Record<string, string>[] {
  const [header = [], ...body] = block
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) =>
      line
        .replace(/^\||\|$/g, "")
        .split("|")
        .map((cell) => cell.trim()),
    );
  return body.map((row) => Object.fromEntries(header.map((name, i) => [name, row[i] ?? ""])));
}
