/* oxlint-disable no-empty-pattern -- Playwright needs a destructuring pattern, even an empty one */
import { test as base } from "@playwright/test";
import { describe, expect, it, vi } from "vitest";
import Yadda from "yadda";
import { defineFeature, defineFeatureSuite, parseFeature } from "./feature.js";
import {
  collectScenarioFixtures,
  createFixtureCollector,
  createFixtureTestBody,
  FixtureDeclarationError,
  parseFixtureNames,
  withScenarioFixtures,
} from "./fixtures.js";
import { createBdd, createLibraryRegistry, createStepLibrary } from "./registry.js";
import { runScenarioSteps } from "./runner.js";

describe("parseFixtureNames", () => {
  it("reads destructured names from arrow, async and function steps", () => {
    expect(parseFixtureNames(({ page, request }: any) => [page, request])).toEqual([
      "page",
      "request",
    ]);
    expect(parseFixtureNames(async ({ account }: any, name: string) => [account, name])).toEqual([
      "account",
    ]);
    expect(
      parseFixtureNames(async function named({ page }: any) {
        return page;
      }),
    ).toEqual(["page"]);
    expect(
      parseFixtureNames({ method: async ({ page }: any) => page }.method as (...a: any[]) => any),
    ).toEqual(["page"]);
  });

  it("uses the property name, not the local alias or default", () => {
    expect(parseFixtureNames(({ page: p, user = {} }: any) => [p, user])).toEqual(["page", "user"]);
  });

  it("copes with comments, multi-line patterns and parentheses in defaults", () => {
    const make = (...numbers: number[]) => numbers;
    const fn = async ({
      page, // the page
      /* block, comment */ account = make(1, 2),
    }: any) => [page, account];
    expect(parseFixtureNames(fn)).toEqual(["page", "account"]);
  });

  it("ignores $-prefixed names such as $testInfo", () => {
    expect(parseFixtureNames(({ page, $testInfo }: any) => [page, $testInfo])).toEqual(["page"]);
  });

  it("needs nothing from steps without parameters or with an empty pattern", () => {
    expect(parseFixtureNames(() => {})).toEqual([]);
    expect(parseFixtureNames(async function () {})).toEqual([]);
    expect(parseFixtureNames(({}: any, arg: string) => arg)).toEqual([]);
  });

  it("rejects a first parameter that is not destructured", () => {
    expect(() => parseFixtureNames((fixtures: any) => fixtures)).toThrow(FixtureDeclarationError);
    expect(() => parseFixtureNames(async (fixtures: any) => fixtures)).toThrow(/destructure/);
  });

  it("rejects fixture names Playwright could not read", () => {
    expect(() => parseFixtureNames(({ "my-fixture": f }: any) => f)).toThrow(
      FixtureDeclarationError,
    );
  });

  it("keeps custom fixtures whose names start with $, and leaves out only $testInfo", () => {
    expect(parseFixtureNames(({ $custom, $testInfo }: any) => [$custom, $testInfo])).toEqual([
      "$custom",
    ]);
  });

  it("points testInfo at $testInfo, since testInfo is not a fixture", () => {
    expect(() => parseFixtureNames(({ testInfo }: any) => testInfo)).toThrow(/\$testInfo/);
  });

  it("rejects functions whose parameters cannot be read", () => {
    const bound = (({ page }: any) => page).bind(null);
    expect(() => parseFixtureNames(bound)).toThrow(/bound|native/);
    expect(() => parseFixtureNames(Math.max)).toThrow(FixtureDeclarationError);
  });

  it("reads past regex literals in default values, including ones with brackets and quotes", () => {
    expect(parseFixtureNames(({ a = /\(/, b }: any) => [a, b])).toEqual(["a", "b"]);
    expect(parseFixtureNames(({ a = /\//, b }: any) => [a, b])).toEqual(["a", "b"]);
    expect(parseFixtureNames(({ a = /{/, b }: any) => [a, b])).toEqual(["a", "b"]);
    expect(parseFixtureNames(({ a = /[/)]/, b }: any) => [a, b])).toEqual(["a", "b"]);
    expect(parseFixtureNames(({ a = /'"`/, b }: any) => [a, b])).toEqual(["a", "b"]);
    expect(parseFixtureNames(({ a = 6 / 2, b }: any) => [a, b])).toEqual(["a", "b"]);
    expect(parseFixtureNames(({ a = /\//, b }: any) => [a, b])).toEqual(["a", "b"]);
  });

  it("does not take [native code] in a step's body for a native function", () => {
    expect(
      parseFixtureNames(async ({ page }: any) => String(page).includes("[native code]")),
    ).toEqual(["page"]);
  });

  it("reads fixture names with non-ASCII letters, and explains a non-destructured one", () => {
    expect(parseFixtureNames(({ ñandú }: any) => ñandú)).toEqual(["ñandú"]);
    expect(() => parseFixtureNames((ñ: any) => ñ)).toThrow(/destructure/);
  });

  it("rejects rest properties", () => {
    expect(() => parseFixtureNames(({ page, ...rest }: any) => [page, rest])).toThrow(/Rest/);
  });
});

describe("createFixtureTestBody", () => {
  it("builds a callback whose destructuring names the fixtures, as Playwright reads it", () => {
    const body = createFixtureTestBody(["page", "context", "account"], async () => {});
    expect(parseFixtureNames(body)).toEqual(["page", "context", "account"]);
    expect(Function.prototype.toString.call(body)).toMatch(/^async function \(\{/);
  });

  it("passes the destructured fixtures and test info to the runner", async () => {
    const seen: unknown[] = [];
    const body = createFixtureTestBody(["page", "run", "testInfo"], async (fixtures, testInfo) => {
      seen.push(fixtures, testInfo);
    });
    await body({ page: "P", run: "R", testInfo: "T", extra: "X" } as any, "INFO" as any);
    expect(seen).toEqual([{ page: "P", run: "R", testInfo: "T" }, "INFO"]);
  });

  it("refuses names that are not identifiers", () => {
    expect(() => createFixtureTestBody(["my-fixture"], async () => {})).toThrow(
      FixtureDeclarationError,
    );
  });
});

describe("fixtures mode step libraries", () => {
  it("rejects a step that does not destructure, naming it", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    expect(() => steps.given("a thing", (fx: any) => fx)).toThrow(/a thing/);
  });

  it("collects the fixtures of steps a scenario uses, and only those", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("a signed-in user", async ({ account }) => account);
    steps.when(/^they visit "(.*)"$/, async ({ page, request }, _path: string) => [page, request]);
    steps.then("an unrelated check", async ({ expensive }) => expensive);

    const plain = new Yadda.Library();
    plain.define("a plain step", () => {});

    expect(
      collectScenarioFixtures(
        [steps, plain],
        ["Given a signed-in user", 'When they visit "/home"', "Given a plain step"],
      ),
    ).toEqual(["account", "page", "request"]);
  });

  it("takes the fixtures of the definition Yadda would run, not of every one that matches", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given(/^a (.*)$/, async ({ a }) => a);
    steps.given(/^a specific thing$/, async ({ b }) => b);
    expect(collectScenarioFixtures([steps], ["Given a specific thing"])).toEqual(["b"]);
    expect(collectScenarioFixtures([steps], ["Given a vague thing"])).toEqual(["a"]);
  });

  it("follows Yadda in preferring the library of the previous step on a tie", () => {
    const first = createStepLibrary(undefined, "fixtures");
    first.given("the first step", async ({ one }) => one);
    first.given("a shared step", async ({ fromFirst }) => fromFirst);
    const second = createStepLibrary(undefined, "fixtures");
    second.given("a shared step", async ({ fromSecond }) => fromSecond);

    expect(
      collectScenarioFixtures([first, second], ["Given the first step", "Given a shared step"]),
    ).toEqual(["one", "fromFirst"]);
  });

  it("takes no fixtures from a step Yadda cannot choose a definition for", () => {
    // Yadda refuses to run an ambiguous step, so none of its candidates would use their fixtures
    const first = createStepLibrary(undefined, "fixtures");
    first.given("a shared step", async ({ fromFirst }) => fromFirst);
    const second = createStepLibrary(undefined, "fixtures");
    second.given("a shared step", async ({ fromSecond }) => fromSecond);

    expect(collectScenarioFixtures([first, second], ["Given a shared step"])).toEqual([]);
  });

  it("does no work for libraries that do not use fixtures mode", () => {
    const interpreter = vi.spyOn(Yadda, "Interpreter");
    const bound = createStepLibrary();
    bound.given("a bound step", function () {});
    const param = createStepLibrary(undefined, "param");
    param.given("a param step", () => {});

    expect(collectScenarioFixtures([bound, param], ["Given a bound step"])).toEqual([]);
    expect(interpreter).not.toHaveBeenCalled();
    interpreter.mockRestore();
  });

  it("looks a step line up once per library however many scenarios repeat it", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("a repeated step", async ({ account }) => account);
    steps.given("another step", async ({ page }) => page);
    const lookups = vi.spyOn(steps, "find_compatible_macros");

    const collect = createFixtureCollector();
    for (let i = 0; i < 50; i++) {
      expect(collect([steps], ["Given a repeated step", "Given another step"])).toEqual([
        "account",
        "page",
      ]);
    }
    expect(lookups).toHaveBeenCalledTimes(2);
  });

  it("answers a repeated sequence of steps without ranking it again", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("a first step", async ({ account }) => account);
    steps.given("a second step", async ({ page }) => page);
    const interpreter = vi.spyOn(Yadda, "Interpreter");

    const collect = createFixtureCollector();
    const lines = ["Given a first step", "Given a second step"];
    expect(collect([steps], lines)).toEqual(["account", "page"]);
    expect(collect([steps], [...lines])).toEqual(["account", "page"]);
    expect(interpreter).toHaveBeenCalledTimes(1);

    // a different sequence, or different libraries, is not mistaken for it
    expect(collect([steps], [lines[1]!, lines[0]!])).toEqual(["page", "account"]);
    const other = createStepLibrary(undefined, "fixtures");
    other.given("a first step", async ({ elsewhere }) => elsewhere);
    expect(collect([other], [lines[0]!])).toEqual(["elsewhere"]);
    expect(interpreter).toHaveBeenCalledTimes(3);
    interpreter.mockRestore();
  });

  it("does not remember lookups between collectors, so steps defined later are seen", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    // a recorded definition, so the first collector really looks "a late step" up (and finds nothing)
    steps.given("an early step", async ({ page }) => page);
    const first = createFixtureCollector();
    expect(first([steps], ["Given a late step"])).toEqual([]);
    steps.given("a late step", async ({ account }) => account);
    expect(createFixtureCollector()([steps], ["Given a late step"])).toEqual(["account"]);
    // the first collector still has its earlier answer, which is why one is made per suite
    expect(first([steps], ["Given a late step"])).toEqual([]);
  });

  it("takes no fixtures for a step nothing defines", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("a known step", async ({ a }) => a);
    expect(collectScenarioFixtures([steps], ["Given an unknown step"])).toEqual([]);
  });

  it("keeps the first definition's fixtures when a duplicate is refused", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("a dup", async ({ first }) => first);
    expect(() => steps.given("a dup", async ({ second }) => second)).toThrow(/Duplicate/);
    expect(collectScenarioFixtures([steps], ["Given a dup"])).toEqual(["first"]);
  });

  it("rejects testInfo in a step, naming the step", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    expect(() => steps.given("uses test info", async ({ testInfo }) => testInfo)).toThrow(
      /\$testInfo.*uses test info|uses test info.*\$testInfo/s,
    );
  });

  it("runs the step with the scenario's fixtures and its captured arguments", async () => {
    const steps = createStepLibrary(undefined, "fixtures");
    const calls: unknown[][] = [];
    steps.given("they have (.*) items", async ({ page }, n: string) => {
      calls.push([{ page }, n]);
    });
    await runScenarioSteps(Yadda.createInstance([steps]), ["Given they have 3 items"], {
      page: "ctx page",
    });
    // outside a feature suite the scenario context is what the step receives
    expect(calls[0]?.[0]).toMatchObject({ page: "ctx page" });
    expect(calls[0]?.[1]).toBe("3");
  });
});

describe("fixtures a step asked for but did not receive", () => {
  const run = (steps: ReturnType<typeof createStepLibrary>, step: string) =>
    runScenarioSteps(Yadda.createInstance([steps as any]), [step], { page: "P" });

  it("fails naming the step and the fixture, instead of passing undefined", async () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("a step needing an account", async ({ account }) => account);
    await expect(
      withScenarioFixtures({ page: "P" }, () => run(steps, "Given a step needing an account")),
    ).rejects.toThrow(/"account".*step needing an account|step needing an account.*"account"/s);
  });

  it("fails the same way when the step runs outside a suite on a context without it", async () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("a step needing an account", async ({ account }) => account);
    await expect(run(steps, "Given a step needing an account")).rejects.toThrow(/"account"/);
  });

  it("accepts a fixture whose value is undefined, such as an unset option", async () => {
    const steps = createStepLibrary(undefined, "fixtures");
    const seen: unknown[] = [];
    steps.given("a step needing a base url", async ({ baseURL }) => {
      seen.push(baseURL);
    });
    await withScenarioFixtures({ page: "P", baseURL: undefined }, () =>
      run(steps, "Given a step needing a base url"),
    );
    expect(seen).toEqual([undefined]);
  });
});

describe("the scenario context in fixtures mode", () => {
  it("is `this` in a function step, shared with later steps and with other modes", async () => {
    const steps = createStepLibrary(undefined, "fixtures");
    const seen: unknown[] = [];
    steps.given("a step that sets state", function (this: any, { page }) {
      this.shared = `set with ${page}`;
    });
    const bound = createStepLibrary();
    bound.then("a bound step that reads it", function (this: any) {
      seen.push(this.shared);
    });
    steps.then("a step that reads it too", function (this: any, {}) {
      seen.push(this.shared);
    });

    await runScenarioSteps(
      Yadda.createInstance([steps, bound]),
      [
        "Given a step that sets state",
        "Then a bound step that reads it",
        "Then a step that reads it too",
      ],
      { page: "P" },
    );
    expect(seen).toEqual(["set with P", "set with P"]);
  });

  it("carries the macroContext of define to `this`, not to the fixtures", async () => {
    const steps = createStepLibrary(undefined, "fixtures");
    const seen: unknown[] = [];
    steps.given(
      "a step reading its macro context",
      function (this: any, {}) {
        seen.push(this.fromMacro);
      },
      { fromMacro: "M" },
    );
    steps.given("a step asking for it as a fixture", async ({ fromMacro }) => fromMacro, {
      fromMacro: "M",
    });
    // as in a suite, where the fixtures come from Playwright and not from Yadda's context
    const run = (step: string) =>
      withScenarioFixtures({ page: "P" }, () =>
        runScenarioSteps(Yadda.createInstance([steps]), [step], { page: "P" }),
      );

    await run("Given a step reading its macro context");
    expect(seen).toEqual(["M"]);
    await expect(run("Given a step asking for it as a fixture")).rejects.toThrow(/"fromMacro"/);
  });
});

describe("step styles in fixtures mode", () => {
  const run = (steps: ReturnType<typeof createStepLibrary>, step: string) =>
    runScenarioSteps(Yadda.createInstance([steps as any]), [step], { page: "P" });

  it("runs sync steps", async () => {
    const steps = createStepLibrary(undefined, "fixtures");
    const seen: unknown[] = [];
    steps.given("a sync step", ({ page }) => {
      seen.push(page);
    });
    await run(steps, "Given a sync step");
    expect(seen).toEqual(["P"]);
  });

  it("waits for steps that return a promise, and fails on a rejection", async () => {
    const steps = createStepLibrary(undefined, "fixtures");
    const seen: string[] = [];
    steps.given("a slow step", async ({}) => {
      await new Promise((resolve) => setTimeout(resolve, 20));
      seen.push("done");
    });
    steps.given("a failing step", async ({}) => {
      throw new Error("boom");
    });
    await run(steps, "Given a slow step");
    expect(seen).toEqual(["done"]);
    await expect(run(steps, "Given a failing step")).rejects.toThrow("boom");
  });

  it("passes next to callback-style steps, with and without captured arguments", async () => {
    const steps = createStepLibrary(undefined, "fixtures");
    const seen: unknown[] = [];
    steps.given("a callback step", ({ page }, next: () => void) => {
      setTimeout(() => {
        seen.push(["no args", page]);
        next();
      }, 10);
    });
    steps.given(/^a callback step with (\d+)$/, ({ page }, n: string, next: () => void) => {
      setTimeout(() => {
        seen.push(["arg", page, n]);
        next();
      }, 10);
    });
    await run(steps, "Given a callback step");
    await run(steps, "Given a callback step with 7");
    expect(seen).toEqual([
      ["no args", "P"],
      ["arg", "P", "7"],
    ]);
  });

  it("fails a callback-style step that passes next an error", async () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("a bad callback step", ({}, next: (err?: Error) => void) => {
      next(new Error("callback failed"));
    });
    await expect(run(steps, "Given a bad callback step")).rejects.toThrow("callback failed");
  });

  it("reads fixtures from a callback-style step like any other", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("a callback step", ({ account }, next: () => void) => {
      void account;
      next();
    });
    expect(collectScenarioFixtures([steps], ["Given a callback step"])).toEqual(["account"]);
  });
});

describe("defineFeature with fixtures", () => {
  const feature = (libraries: string, steps: string[]) =>
    parseFeature(
      `@Libraries=${libraries}\nFeature: F\n  Scenario: S\n${steps.map((s) => `    ${s}`).join("\n")}\n`,
    );

  /** A stand-in for Playwright's `test` that records what each scenario callback would set up */
  function fakeTest() {
    const declared: Array<{ title: string; fn: (...args: any[]) => Promise<void> }> = [];
    const declare = (title: string, ...rest: unknown[]) =>
      declared.push({ title, fn: rest.at(-1) as any });
    const test: any = Object.assign((...args: [string, ...unknown[]]) => declare(...args), {
      describe: (_: string, body: () => void) => body(),
      skip: declare,
      only: declare,
      step: (_: string, body: () => Promise<void>) => body(),
    });
    return { test, declared };
  }

  it("destructures page, context and the fixtures of the scenario's steps only", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("a signed-in user", async ({ account }) => account);
    const other = createStepLibrary(undefined, "fixtures");
    other.given("an expensive thing", async ({ expensive }) => expensive);

    const { test, declared } = fakeTest();
    defineFeature(feature("steps", ["Given a signed-in user"]), {
      test,
      registry: createLibraryRegistry({ steps, other }, { defaultLibraries: [] }),
    });

    expect(parseFixtureNames(declared[0]!.fn)).toEqual(["page", "context", "account"]);
  });

  it("keeps an ambiguous step's candidates' fixtures out of the test callback", () => {
    const one = createStepLibrary(undefined, "fixtures");
    one.given("a shared step", async ({ known }) => known);
    const two = createStepLibrary(undefined, "fixtures");
    two.given("a shared step", async ({ onlyAnotherSuiteHasThis }) => onlyAnotherSuiteHasThis);

    const { test, declared } = fakeTest();
    defineFeature(feature("one, two", ["Given a shared step"]), {
      test,
      registry: createLibraryRegistry({ one, two }, { defaultLibraries: [] }),
    });

    expect(parseFixtureNames(declared[0]!.fn)).toEqual(["page", "context"]);
  });

  it("does not collect fixtures from libraries the scenario does not use", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("a thing", async ({ a }) => a);
    const other = createStepLibrary(undefined, "fixtures");
    other.given("a thing", async ({ b }) => b);

    const { test, declared } = fakeTest();
    defineFeature(feature("other", ["Given a thing"]), {
      test,
      registry: createLibraryRegistry({ steps, other }, { defaultLibraries: [] }),
    });

    expect(parseFixtureNames(declared[0]!.fn)).toEqual(["page", "context", "b"]);
  });

  it("hands steps their fixtures and $testInfo, and keeps bound and param modes working", async () => {
    const events: unknown[] = [];
    const fixtureSteps = createStepLibrary(undefined, "fixtures");
    fixtureSteps.given("a fixture step", async ({ account, $testInfo }) => {
      events.push(["fixtures", account, $testInfo]);
    });
    const boundSteps = createStepLibrary();
    boundSteps.when("a bound step", function (this: any) {
      events.push(["bound", this.page]);
    });
    const paramSteps = createStepLibrary(undefined, "param");
    paramSteps.then("a param step", async (ctx: any) => {
      events.push(["param", ctx.page, ctx.testInfo]);
    });

    const { test, declared } = fakeTest();
    defineFeature(
      feature("fixtureSteps, boundSteps, paramSteps", [
        "Given a fixture step",
        "When a bound step",
        "Then a param step",
      ]),
      {
        test,
        registry: createLibraryRegistry(
          { fixtureSteps, boundSteps, paramSteps },
          { defaultLibraries: [] },
        ),
      },
    );

    await declared[0]!.fn({ page: "PAGE", context: "CTX", account: "ACCOUNT" }, "INFO");
    expect(events).toEqual([
      ["fixtures", "ACCOUNT", "INFO"],
      ["bound", "PAGE"],
      ["param", "PAGE", "INFO"],
    ]);
  });

  it("sets up and passes a custom fixture whose name starts with $", async () => {
    const seen: unknown[] = [];
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("a step with a dollar fixture", async ({ $custom }) => {
      seen.push($custom);
    });

    const { test, declared } = fakeTest();
    defineFeature(feature("steps", ["Given a step with a dollar fixture"]), {
      test,
      registry: createLibraryRegistry({ steps }, { defaultLibraries: [] }),
    });

    expect(parseFixtureNames(declared[0]!.fn)).toEqual(["page", "context", "$custom"]);
    await declared[0]!.fn({ page: "P", context: "C", $custom: "V" }, "INFO");
    expect(seen).toEqual(["V"]);
  });

  it("gives createContext every fixture set up for the scenario", async () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("a signed-in user", async ({ account }) => account);
    const seen: unknown[] = [];

    const { test, declared } = fakeTest();
    defineFeature(feature("steps", ["Given a signed-in user"]), {
      test,
      registry: createLibraryRegistry({ steps }, { defaultLibraries: [] }),
      createContext: (fixtures: any) => {
        seen.push(fixtures);
        return fixtures;
      },
    });

    await declared[0]!.fn({ page: "P", context: "C", account: "A" }, "INFO");
    expect(seen).toEqual([{ page: "P", context: "C", account: "A", testInfo: "INFO" }]);
  });

  it("warns about an unknown library when declared, and fails the scenario when it runs", async () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    const { test, declared } = fakeTest();
    defineFeature(feature("missing", ["Given a thing"]), {
      test,
      registry: createLibraryRegistry({}, { defaultLibraries: [] }),
    });
    expect(warn).toHaveBeenCalledOnce();
    expect(warn.mock.calls[0]?.[0]).toMatch(
      /"F" > "S".*page and context.*Unknown step library "missing"/,
    );
    warn.mockRestore();

    expect(parseFixtureNames(declared[0]!.fn)).toEqual(["page", "context"]);
    await expect(declared[0]!.fn({ page: "P", context: "C" }, {})).rejects.toThrow(
      /Unknown step library "missing"/,
    );
  });
});

describe("defineFeatureSuite with fixtures", () => {
  it("matches a step line once for the whole suite, not once per feature file", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given("the user is signed in", async ({ account }) => account);
    steps.when("they open the dashboard", async ({ page }) => page);
    const lookups = vi.spyOn(steps, "find_compatible_macros");

    const features = ["A", "B", "C"].map((title) =>
      parseFeature(
        `@Libraries=steps\nFeature: ${title}\n  Scenario: S\n    Given the user is signed in\n    When they open the dashboard\n`,
      ),
    );
    const declared: Array<(...args: any[]) => Promise<void>> = [];
    const declare = (_: string, ...rest: unknown[]) => declared.push(rest.at(-1) as any);
    const test: any = Object.assign((...args: [string, ...unknown[]]) => declare(...args), {
      describe: (_: string, body: () => void) => body(),
      skip: declare,
      only: declare,
      step: (_: string, body: () => Promise<void>) => body(),
    });

    defineFeatureSuite({
      test,
      registry: createLibraryRegistry({ steps }, { defaultLibraries: [] }),
      features,
    });

    expect(declared).toHaveLength(3);
    for (const fn of declared) {
      expect(parseFixtureNames(fn)).toEqual(["page", "context", "account"]);
    }
    expect(lookups).toHaveBeenCalledTimes(2);
  });
});

describe("createBdd", () => {
  it("returns step libraries in fixtures mode", async () => {
    const { createStepLibrary: create } = createBdd(base);
    const steps = create();
    const seen: unknown[] = [];
    steps.given("a step", async ({ page }) => {
      seen.push(page);
    });
    expect(collectScenarioFixtures([steps], ["Given a step"])).toEqual(["page"]);
  });
});
