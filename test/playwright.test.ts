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
function runPlaywright(spec: string, extraArgs: string[] = []) {
  const run = spawnSync(
    path.join(root, "node_modules", ".bin", "playwright"),
    ["test", spec, "--config", path.join(project, "playwright.config.ts"), ...extraArgs],
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
  tests: {
    annotations: { type: string; description?: string }[];
    expectedStatus: string;
    status: string;
    timeout: number;
    results: { status: string }[];
  }[];
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

describe("special tags with the real Playwright", () => {
  let specs: Spec[] = [];
  beforeAll(() => {
    const run = runPlaywright("tags.spec.ts");
    specs = run.report ? specsOf(run.report) : [];
  }, 60_000);

  const test = (title: string) =>
    specs.find((spec) => spec.title === `Scenario: ${title}`)?.tests[0];

  it("expects a @fail scenario to fail, and so passes it when it does", () => {
    expect(test("Expected to fail")).toMatchObject({
      expectedStatus: "failed",
      status: "expected",
    });
  });

  it("skips @fixme, @skip and @pending scenarios", () => {
    for (const title of ["Fixme", "Skipped", "Pending"]) {
      expect(test(title)).toMatchObject({ expectedStatus: "skipped", status: "skipped" });
    }
  });

  it("triples the timeout of a @slow scenario", () => {
    expect(test("Plain")?.timeout).toBe(30_000);
    expect(test("Slow")?.timeout).toBe(90_000);
  });

  it("times a scenario out after its own @timeout, and a feature's @timeout applies to all of it", () => {
    expect(test("Times out")?.results[0]?.status).toBe("timedOut");
    expect(test("Times out from the feature's tag")?.results[0]?.status).toBe("timedOut");
    expect(test("Overrides the feature's timeout")?.results[0]?.status).toBe("passed");
  });

  it("runs a feature's @mode:serial scenarios in serial, skipping what follows a failure", () => {
    expect(
      test("Is skipped, as serial mode skips what follows a failure")?.results[0]?.status,
    ).toBe("skipped");
  });

  it("retries as the feature's @retries asks, unless the scenario's @retries says otherwise", () => {
    expect(test("Retried once, from the feature")?.results.map((r) => r.status)).toEqual([
      "failed",
      "passed",
    ]);
    expect(test("Retries nothing")?.results.map((r) => r.status)).toEqual(["failed"]);
  });

  describe("on the hooks that run before a scenario", () => {
    let hooked: Spec[] = [];
    beforeAll(() => {
      const run = runPlaywright("tags-hooks.spec.ts");
      hooked = run.report ? specsOf(run.report) : [];
    }, 60_000);
    const hook = (title: string) =>
      hooked.find((spec) => spec.title === `Scenario: ${title}`)?.tests[0];

    it("lets @fail expect a failing hook", () => {
      expect(hook("Expected to fail, in a failing hook")).toMatchObject({ status: "expected" });
    });

    it("lets @slow triple the timeout for a slow hook, @timeout's included", () => {
      expect(hook("Slow, in a slow hook")).toMatchObject({ status: "expected", timeout: 1500 });
      expect(hook("Slow with its own timeout, in a slow hook")).toMatchObject({
        status: "expected",
        timeout: 1800,
      });
    });

    it("times out a slow hook that @timeout allows too little for", () => {
      expect(hook("Over the feature's timeout, in a slow hook")?.results[0]?.status).toBe(
        "timedOut",
      );
    });
  });

  it("changes nothing for tags it does not know", () => {
    expect(test("Unknown tags change nothing")).toMatchObject({
      expectedStatus: "passed",
      status: "expected",
    });
  });

  describe("--grep", () => {
    const titles = (extraArgs: string[]) => {
      const { report } = runPlaywright("tags.spec.ts", ["--list", ...extraArgs]);
      return report ? specsOf(report).map((spec) => spec.title.replace("Scenario: ", "")) : [];
    };

    // each of these starts the Playwright CLI, which can be slow to load TypeScript on a cold start
    it("selects scenarios by their own tags", { timeout: 60_000 }, () => {
      expect(titles(["--grep", "@slow"])).toEqual(["Slow"]);
      expect(titles(["--grep", "@unknown-tag"])).toEqual(["Unknown tags change nothing"]);
    });

    it("selects scenarios by their feature's tags", { timeout: 60_000 }, () => {
      // the report lists a nested describe's scenarios after the others, so compare them sorted
      expect(titles(["--grep", "@mode:serial"]).sort()).toEqual([
        "Is skipped, as serial mode skips what follows a failure",
        "Overrides the feature's timeout",
        "Times out from the feature's tag",
      ]);
      expect(titles(["--grep", "@smoke"])).toHaveLength(10);
    });

    it(
      "selects one scenario with the end-anchored pattern the IDE plugin builds",
      { timeout: 60_000 },
      () => {
        // Playwright matches a test's tags after its title, so the pattern allows them (PlaywrightGrep.kt)
        expect(titles(["--grep", "Feature: Special tags Scenario: Slow(?: @\\S+)*$"])).toEqual([
          "Slow",
        ]);
      },
    );

    it("leaves scenarios out with --grep-invert", { timeout: 60_000 }, () => {
      const all = titles([]);
      expect(all).toHaveLength(13);
      expect(titles(["--grep-invert", "@smoke|@mode:serial"])).toEqual([]);
      expect(titles(["--grep-invert", "@fail|@fixme|@skip|@pending"])).toHaveLength(9);
    });
  });
});

describe("hooks with the real Playwright", () => {
  // `beforeAll` is read by Playwright, which wants a destructuring pattern for its first parameter
  let status: number | null;
  let specs: Spec[] = [];
  beforeAll(() => {
    const run = runPlaywright("bdd-hooks.spec.ts");
    status = run.status;
    specs = run.report ? specsOf(run.report) : [];
  }, 60_000);

  const ran = (title: string) =>
    specs
      .find((spec) => spec.title === `Scenario: ${title}`)
      ?.tests[0]?.annotations.filter((a) => a.type === "hook")
      .map((a) => a.description);

  it("loads the spec; only the scenario that times out fails the run", () => {
    expect(status).toBe(1);
    const results = specs.map((spec) => [spec.title, spec.tests[0]?.status]);
    expect(results.filter(([, outcome]) => outcome === "unexpected")).toEqual([
      ["Scenario: Times out", "unexpected"],
    ]);
    expect(specs).toHaveLength(3);
  });

  it("runs Before hooks in order, filtered by tag, and After hooks in reverse", () => {
    expect(ran("Tagged")).toEqual([
      "BeforeWorker",
      "BeforeScenario 1",
      "BeforeScenario mobile",
      "BeforeStep Given a step that passes",
      "AfterStep Given a step that passes ok",
      "AfterScenario 2",
      "AfterScenario 1",
    ]);
  });

  it("runs AfterStep and AfterScenario when the test times out", () => {
    expect(ran("Times out")?.filter((hook) => hook !== "BeforeWorker")).toEqual([
      "BeforeScenario 1",
      "BeforeStep Given a step that hangs",
      "AfterStep Given a step that hangs failed",
      "AfterScenario 2",
      "AfterScenario 1",
    ]);
  });

  it("runs AfterStep and AfterScenario when a step fails", () => {
    // Playwright copies annotations added in `beforeAll` onto the other tests of the worker
    expect(ran("Fails")?.filter((hook) => hook !== "BeforeWorker")).toEqual([
      "BeforeScenario 1",
      "BeforeStep Given a step that fails",
      "AfterStep Given a step that fails failed",
      "AfterScenario 2",
      "AfterScenario 1",
    ]);
  });
});
