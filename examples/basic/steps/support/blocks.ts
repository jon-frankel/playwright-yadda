// Yadda hands a multi-line step to its definition as one string: the step's first line, a newline,
// then the lines between the two `---` fences. These read the three shapes the orders feature uses.

/** The fenced block of a step; a blank one is as good as none. */
export function requireBlock(block: string, step: string): string {
  if (!block.trim()) throw new Error(`"${step}" needs a block between two --- lines`);
  return block;
}

/** `| a | b |` rows, the first row naming the columns. */
export function parseTable(block: string): Record<string, string>[] {
  const rows = block
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) =>
      line
        .replace(/^\||\|$/g, "")
        .split("|")
        .map((cell) => cell.trim()),
    );
  return withHeader(rows);
}

/** Comma separated rows, the first row naming the columns. A field in double quotes may contain commas. */
export function parseCsv(block: string): Record<string, string>[] {
  const rows = block
    .split("\n")
    .filter((line) => line.trim())
    .map((line) =>
      [...line.matchAll(/("([^"]*)"|[^,]*)(,|$)/g)].slice(0, -1).map((m) => (m[2] ?? m[1]!).trim()),
    );
  return withHeader(rows);
}

function withHeader(rows: string[][]): Record<string, string>[] {
  const [header = [], ...body] = rows;
  return body.map((row) => Object.fromEntries(header.map((name, i) => [name, row[i] ?? ""])));
}
