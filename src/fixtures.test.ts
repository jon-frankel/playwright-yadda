/* oxlint-disable no-empty-pattern -- Playwright needs a destructuring pattern, even an empty one */
import { test as base } from "@playwright/test";
import { describe, expect, it, vi } from "vitest";
import Yadda from "yadda";
import { defineFeature, parseFeature } from "./feature.js";
import {
  collectScenarioFixtures,
  createFixtureTestBody,
  FixtureDeclarationError,
  parseFixtureNames,
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

  it("includes the fixtures of every macro a step matches", () => {
    const steps = createStepLibrary(undefined, "fixtures");
    steps.given(/^a (.*)$/, async ({ a }) => a);
    steps.given(/^a specific thing$/, async ({ b }) => b);
    expect(collectScenarioFixtures([steps], ["Given a specific thing"])).toEqual(["a", "b"]);
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
