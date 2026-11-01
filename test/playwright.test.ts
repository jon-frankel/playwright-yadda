import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const project = path.join(root, "test", "playwright");

/** Run one spec of test/playwright with the real Playwright CLI and read its JSON report */
function runPlaywright(spec: string) {
  const run = spawnSync(
    path.join(root, "node_modules", ".bin", "playwright"),
    ["test", spec, "--config", path.join(project, "playwright.config.ts")],
    { cwd: project, encoding: "utf8", env: { ...process.env, FORCE_COLOR: "0" } },
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
  tests: { annotations: { type: string; description?: string }[]; results: { status: string }[] }[];
};

/** The specs of a JSON report, flattened */
function specsOf(suite: { specs?: Spec[]; suites?: any[] }): Spec[] {
  return [...(suite.specs ?? []), ...(suite.suites ?? []).flatMap(specsOf)];
}

describe("fixtures with the real Playwright", () => {
  // Playwright reads each generated test callback itself, so this is the proof that its own parser
  // accepts what is generated and sets up exactly what the scenario's steps destructure
  const { report, status } = runPlaywright("fixtures.spec.ts");
  const specs = report ? specsOf(report) : [];
  const setUps = (title: string) =>
    specs
      .find((spec) => spec.title === `Scenario: ${title}`)
      ?.tests[0]?.annotations.filter((a) => a.type === "set-up")
      .map((a) => a.description);

  it("runs every scenario to a pass", () => {
    expect(status).toBe(0);
    expect(specs).toHaveLength(4);
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
});

describe("a fixture the test does not have", () => {
  it("stops the spec from loading, naming the fixture", () => {
    const { report, status } = runPlaywright("typo.spec.ts");
    expect(status).not.toBe(0);
    expect(report?.stats?.expected ?? 0).toBe(0);
    const messages = (report?.errors ?? []).map((error: { message: string }) => error.message);
    expect(messages.join("\n")).toMatch(/unknown parameter "pgae"/);
  });
});
