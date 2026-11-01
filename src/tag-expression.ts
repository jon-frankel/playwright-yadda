/**
 * Tag expressions, as playwright-bdd writes them: `@mobile and not @slow`, `(@a or @b) and @c`.
 * `not` binds tighter than `and`, which binds tighter than `or`. Keywords and tags are
 * case-insensitive, and `@name=value` is the same tag as `@name:value`.
 */

export type TagExpression = (tags: readonly string[]) => boolean;

type Token = { kind: "(" | ")" | "and" | "or" | "not" } | { kind: "tag"; name: string };

const normalise = (tag: string) => tag.trim().toLowerCase().replace("=", ":");

function tokenise(source: string): Token[] {
  const tokens: Token[] = [];
  const re = /\s*(\(|\)|[^\s()]+)/y;
  let match: RegExpExecArray | null;
  while (re.lastIndex < source.length && (match = re.exec(source))) {
    const word = match[1]!;
    const lower = word.toLowerCase();
    if (word === "(" || word === ")") tokens.push({ kind: word });
    else if (lower === "and" || lower === "or" || lower === "not") tokens.push({ kind: lower });
    else if (word.startsWith("@") && word.length > 1) {
      tokens.push({ kind: "tag", name: normalise(word) });
    } else {
      throw new Error(
        `Invalid tag expression "${source}": expected a tag (@name), "and", "or", "not" or a parenthesis, got "${word}"`,
      );
    }
  }
  return tokens;
}

/** Compile a tag expression. Throws on a syntax error, so a mistake shows when the hook is registered. */
export function parseTagExpression(source: string): TagExpression {
  const tokens = tokenise(source);
  if (tokens.length === 0) throw new Error("Invalid tag expression: it is empty");
  let pos = 0;
  const fail = (what: string): never => {
    const at = tokens[pos];
    throw new Error(
      `Invalid tag expression "${source}": expected ${what}, got ${at ? `"${at.kind === "tag" ? "@" + at.name.slice(1) : at.kind}"` : "the end"}`,
    );
  };

  const parseOr = (): TagExpression => {
    let left = parseAnd();
    while (tokens[pos]?.kind === "or") {
      pos++;
      const l = left;
      const r = parseAnd();
      left = (tags) => l(tags) || r(tags);
    }
    return left;
  };
  const parseAnd = (): TagExpression => {
    let left = parseNot();
    while (tokens[pos]?.kind === "and") {
      pos++;
      const l = left;
      const r = parseNot();
      left = (tags) => l(tags) && r(tags);
    }
    return left;
  };
  const parseNot = (): TagExpression => {
    if (tokens[pos]?.kind === "not") {
      pos++;
      const inner = parseNot();
      return (tags) => !inner(tags);
    }
    return parsePrimary();
  };
  const parsePrimary = (): TagExpression => {
    const token = tokens[pos];
    if (token?.kind === "(") {
      pos++;
      const inner = parseOr();
      if (tokens[pos]?.kind !== ")") fail('")"');
      pos++;
      return inner;
    }
    if (token?.kind === "tag") {
      pos++;
      const name = token.name;
      return (tags) => tags.some((tag) => normalise(tag) === name);
    }
    return fail('a tag, "not" or "("');
  };

  const expression = parseOr();
  if (pos < tokens.length) fail('"and", "or" or the end');
  return expression;
}
