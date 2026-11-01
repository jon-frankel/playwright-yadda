import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { beforeAll, describe, expect, it } from "vitest";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const project = path.join(root, "test", "playwright");

/** This process's environment without anything that would move Playwright's JSON report off stdout */
function cleanEnv() {
  const env = { ...process.env, FORCE_COLOR: "0" };
  for (const name of Object.keys(env))
    if (name.startsWith("PLAYWRIGHT_JSON_OUTPUT")) delete env[name];
  return env;
}

/** Run one spec of test/playwright with the real Playwright CLI and read its JSON report */
function runPlaywright(spec: string) {
  const run = spawnSync(
    path.join(root, "node_modules", ".bin", "playwright"),
    ["test", spec, "--config", path.join(project, "playwright.config.ts")],
    { cwd: project, encoding: "utf8", env: cleanEnv() },
  );
  let report: any;
  try {
    report = JSON.parse(run.stdout);
  } catch {
    // no report: Playwright refused to load the spec
  }
  return { ...run, report };
}

type Spec = {
  title: string;
  tags?: string[];
  tests: { annotations: { type: string; description?: string }[]; results: { status: string }[] }[];
};

/** The specs of a JSON report, flattened */
function specsOf(suite: { specs?: Spec[]; suites?: any[] }): Spec[] {
  return [...(suite.specs ?? []), ...(suite.suites ?? []).flatMap(specsOf)];
}

describe("fixtures with the real Playwright", () => {
  // Playwright reads each generated test callback itself, so this is the proof that its own parser
  // accepts what is generated and sets up exactly what the scenario's steps destructure
  let status: number | null;
  let specs: Spec[] = [];
  beforeAll(() => {
    const run = runPlaywright("fixtures.spec.ts");
    status = run.status;
    specs = run.report ? specsOf(run.report) : [];
  }, 60_000);

  const setUps = (title: string) =>
    specs
      .find((spec) => spec.title === `Scenario: ${title}`)
      ?.tests[0]?.annotations.filter((a) => a.type === "set-up")
      .map((a) => a.description);

  it("runs every scenario to a pass", () => {
    expect(status).toBe(0);
    expect(specs).toHaveLength(5);
    for (const spec of specs) expect(spec.tests[0]?.results[0]?.status).toBe("passed");
  });

  it("sets up a custom fixture for the scenario whose steps use it", () => {
    expect(setUps("Uses a custom fixture")).toEqual(["account"]);
    expect(setUps("Uses another custom fixture")).toEqual(["expensive"]);
  });

  it("does not set up custom fixtures a scenario's steps do not use", () => {
    expect(setUps("Uses a worker fixture")).toEqual([]);
    expect(setUps("Uses no custom fixture")).toEqual([]);
  });

  it("sets up the fixtures of a scenario in the order its steps first use them", () => {
    expect(setUps("Uses two custom fixtures")).toEqual(["expensive", "account"]);
  });
});

describe("a fixture the test does not have", () => {
  it("stops the spec from loading, naming the fixture", () => {
    const { report, status } = runPlaywright("typo.spec.ts");
    expect(status).not.toBe(0);
    expect(report?.stats?.expected ?? 0).toBe(0);
    const messages = (report?.errors ?? []).map((error: { message: string }) => error.message);
    expect(messages.join("\n")).toMatch(/unknown parameter "pgae"/);
  }, 60_000);
});

describe("tags from path with the real Playwright", () => {
  // Playwright validates the `tag` list itself, and `--grep` matches it, so this is the proof that
  // what is generated is accepted and selects the right tests
  let run: ReturnType<typeof runPlaywright>;
  beforeAll(() => {
    run = runPlaywright("path-tags.spec.ts");
  }, 60_000);

  it("gives each scenario the tags of its path, and runs the library the path names", () => {
    const specs = run.report ? specsOf(run.report) : [];
    const byTitle = Object.fromEntries(specs.map((spec) => [spec.title, spec]));
    // `plain/other.feature` is untagged, and its step is only in `storefront`: it fails, which
    // shows a library is loaded by the path alone for the tagged one
    expect(byTitle["Scenario: Add item"]?.tags).toEqual(["storefront", "slow"]);
    expect(byTitle["Scenario: Add item"]?.tests[0]?.results[0]?.status).toBe("passed");
    expect(byTitle["Scenario: Nothing special"]?.tags ?? []).toEqual([]);
    expect(byTitle["Scenario: Nothing special"]?.tests[0]?.results[0]?.status).toBe("failed");
  });

  it("lets --grep select by a path tag", () => {
    const grep = spawnSync(
      path.join(root, "node_modules", ".bin", "playwright"),
      [
        "test",
        "path-tags.spec.ts",
        "--config",
        path.join(project, "playwright.config.ts"),
        "--grep",
        "@slow",
      ],
      { cwd: project, encoding: "utf8", env: cleanEnv() },
    );
    const titles = specsOf(JSON.parse(grep.stdout)).map((spec) => spec.title);
    expect(titles).toEqual(["Scenario: Add item"]);
  }, 60_000);
});
