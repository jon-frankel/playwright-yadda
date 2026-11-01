import type { AnnotationRecord } from "./types.js";

/** Values `@mode:` takes, as `test.describe.configure({ mode })` does */
export const TEST_MODES = ["parallel", "serial", "default"] as const;
export type TestMode = (typeof TEST_MODES)[number];

/** How Playwright is to run a scenario, as the feature's and the scenario's tags ask */
export interface ScenarioTags {
  /** `@skip`, `@pending` or `@fixme`: how a scenario that is not to run is declared. `skip` wins. */
  skip?: "skip" | "fixme";
  /** `@only` */
  only: boolean;
  /** `@fail` */
  fail: boolean;
  /** `@slow` */
  slow: boolean;
  /** `@timeout:N`, in milliseconds */
  timeout?: number;
  /** `@retries:N` */
  retries?: number;
  /** `@mode:parallel|serial|default` */
  mode?: TestMode;
  /** Every tag, as Playwright's `tag`, so `--grep` and `--grep-invert` see them */
  playwrightTags: string[];
}

/** The feature-wide settings, and the ones the scenario sets itself, which need their own describe */
export interface ResolvedTags {
  scenario: ScenarioTags;
  /** Settings only the feature's tags give: applied once, to the feature's describe */
  feature: Pick<ScenarioTags, "timeout" | "retries" | "mode">;
  /** Settings the scenario's own tags give: applied to a describe around that scenario alone */
  own: Pick<ScenarioTags, "timeout" | "retries" | "mode">;
}

/**
 * Yadda reads one annotation per line, as `@name` (true) or `@name=value`. A tag written as
 * `@name:value`, the way playwright-bdd writes them, therefore comes through as the name
 * `name:value`. Both spellings are read as the same tag.
 */
function tagsOf(annotations: AnnotationRecord | undefined): Map<string, string | true> {
  const tags = new Map<string, string | true>();
  if (!annotations || typeof annotations !== "object") return tags;
  for (const [rawKey, rawValue] of Object.entries(annotations)) {
    if (rawValue === undefined) continue;
    const key = rawKey.trim().toLowerCase();
    // `false` is kept, so a scenario can switch off what its feature switched on
    if (rawValue === false) {
      if (key) tags.set(key, "false");
      continue;
    }
    const colon = key.indexOf(":");
    if (rawValue === true && colon > 0) {
      tags.set(key.slice(0, colon), key.slice(colon + 1).trim() || true);
    } else if (key) {
      tags.set(key, rawValue === true ? true : String(rawValue).trim() || true);
    }
  }
  return tags;
}

/** The tags as Playwright wants them: `@name`, or `@name:value` when there is a value without spaces */
function toPlaywrightTags(tags: Map<string, string | true>): string[] {
  return [...tags]
    .filter(([, value]) => isSet(value))
    .map(([name, value]) =>
      value === true || /\s/.test(value) ? `@${name}` : `@${name}:${value}`,
    );
}

const isSet = (value: string | true | undefined): value is string | true =>
  value !== undefined && !(typeof value === "string" && value.toLowerCase() === "false");

/** Problems already warned about by this process, so each is reported once */
const warned = new Set<string>();
function warnOnce(message: string) {
  if (warned.has(message)) return;
  warned.add(message);
  console.warn(`playwright-yadda: ${message}`);
}

type Report = (message: string) => void;

function readNumber(
  tags: Map<string, string | true>,
  name: string,
  report: Report,
): number | undefined {
  const value = tags.get(name);
  if (!isSet(value)) return undefined;
  const number = typeof value === "string" && /^\d+$/.test(value) ? Number(value) : NaN;
  if (!Number.isInteger(number)) {
    report(`ignoring @${name}, which needs a whole number, e.g. @${name}:2 (got ${String(value)})`);
    return undefined;
  }
  return number;
}

function readMode(tags: Map<string, string | true>, report: Report): TestMode | undefined {
  const value = tags.get("mode");
  if (!isSet(value)) return undefined;
  const mode = typeof value === "string" ? value.toLowerCase() : "";
  if (!(TEST_MODES as readonly string[]).includes(mode)) {
    report(`ignoring @mode, which needs one of ${TEST_MODES.join(", ")} (got ${String(value)})`);
    return undefined;
  }
  return mode as TestMode;
}

function settingsOf(tags: Map<string, string | true>, report: Report) {
  const settings: ResolvedTags["own"] = {};
  const timeout = readNumber(tags, "timeout", report);
  const retries = readNumber(tags, "retries", report);
  const mode = readMode(tags, report);
  if (timeout !== undefined) settings.timeout = timeout;
  if (retries !== undefined) settings.retries = retries;
  if (mode !== undefined) settings.mode = mode;
  return settings;
}

/**
 * Work out what the feature's and the scenario's tags ask of Playwright. A scenario's tag overrides
 * the feature's own value of it, and adds to the rest. Tags this does not know stay inert: they are
 * only passed on to Playwright as tags, for `--grep`.
 */
export function resolveTags(
  featureAnnotations?: AnnotationRecord,
  scenarioAnnotations?: AnnotationRecord,
  /** `quiet` for a caller that only asks a question of the tags, and has no business warning */
  { quiet = false }: { quiet?: boolean } = {},
): ResolvedTags {
  const report: Report = quiet ? () => {} : warnOnce;
  const featureTags = tagsOf(featureAnnotations);
  const scenarioTags = tagsOf(scenarioAnnotations);

  const feature = settingsOf(featureTags, report);
  const own = settingsOf(scenarioTags, report);
  // A mode is a setting of a group of tests. Playwright refuses a `parallel` describe inside a
  // `serial` one, and a describe of one scenario has nothing to run in serial with, so a scenario
  // cannot set it.
  if (own.mode !== undefined) {
    report("ignoring @mode on a scenario: it applies to a whole feature, so put it on the feature");
    delete own.mode;
  }
  // A scenario tag that is ignored is not passed to Playwright either: it would replace the
  // feature's, which still applies
  for (const name of ["timeout", "retries", "mode"] as const) {
    if (own[name] === undefined) scenarioTags.delete(name);
  }
  const merged = new Map([...featureTags, ...scenarioTags]);

  // `@skip`, `@pending` and `@fixme` all skip a scenario; `@fixme` only differs in how Playwright reports it
  const skipped = (name: string) => isSet(merged.get(name));
  const skip: ScenarioTags["skip"] =
    skipped("skip") || skipped("pending") ? "skip" : skipped("fixme") ? "fixme" : undefined;
  const effective = { ...feature, ...own };

  const scenario: ScenarioTags = {
    only: isSet(merged.get("only")),
    fail: isSet(merged.get("fail")),
    slow: isSet(merged.get("slow")),
    playwrightTags: toPlaywrightTags(merged),
    ...effective,
  };
  if (skip) scenario.skip = skip;

  return { scenario, feature, own };
}

/** For tests: forget the problems already warned about, so each test sees its own warnings */
export function resetWarningsForTests(): void {
  warned.clear();
}
