import { describe, expect, it, vi } from "vitest";
import Yadda from "yadda";
import { defineFeature, defineFeatureSuite, parseFeature } from "./feature.js";
import { createLibraryRegistry } from "./registry.js";

describe("feature", () => {
  const gherkinText = `
@Libraries=auth, catalog
Feature: Storefront Shopping
  Scenario: Add item to cart
    Given the store is open
    When they add "Keyboard" to cart
    Then the cart has 1 item
`;

  const markdownText = `
@Libraries=auth
# Feature: Markdown Feature
  ## Scenario: Markdown Scenario
  - Given step one
  - Then step two
`;

  it("should parse standard Gherkin features", () => {
    const feature = parseFeature(gherkinText, "gherkin");
    expect(feature.title).toBe("Storefront Shopping");
    expect(feature.annotations).toEqual({ libraries: "auth, catalog" });
    expect(feature.scenarios).toHaveLength(1);
    expect(feature.scenarios[0]?.title).toBe("Add item to cart");
    expect(feature.scenarios[0]?.steps).toHaveLength(3);
  });

  it("should parse Markdown features", () => {
    const feature = parseFeature(markdownText, "markdown");
    expect(feature.title).toBe("Markdown Feature");
    expect(feature.annotations).toEqual({ libraries: "auth" });
    expect(feature.scenarios[0]?.title).toBe("Markdown Scenario");
    expect(feature.scenarios[0]?.steps).toHaveLength(2);
  });

  it("should auto-detect markdown vs gherkin", () => {
    const md = parseFeature(markdownText, "auto");
    expect(md.title).toBe("Markdown Feature");

    const gh = parseFeature(gherkinText, "auto");
    expect(gh.title).toBe("Storefront Shopping");
  });

  it("should define Playwright tests with dynamic library resolution", async () => {
    const commonLib = new Yadda.Library();
    const authLib = new Yadda.Library();
    const catalogLib = new Yadda.Library();

    const executedSteps: string[] = [];
    authLib.define("the store is open", () => {
      executedSteps.push("store open");
    });
    catalogLib.define(/they add "(.*)" to cart/, (item: string) => {
      executedSteps.push(`added ${item}`);
    });
    commonLib.define("the cart has 1 item", () => {
      executedSteps.push("cart 1");
    });

    const registry = createLibraryRegistry({
      common: commonLib,
      auth: authLib,
      catalog: catalogLib,
    });

    const feature = parseFeature(gherkinText);

    // Mock Playwright test API
    let describeTitle = "";
    let scenarioTitle = "";
    let scenarioFn: any = null;

    const mockTest: any = Object.assign(
      (title: string, fn: any) => {
        scenarioTitle = title;
        scenarioFn = fn;
      },
      {
        describe: (title: string, fn: any) => {
          describeTitle = title;
          fn();
        },
        skip: vi.fn<() => void>(),
        only: vi.fn<() => void>(),
        step: vi.fn<(_stepTitle: string, stepBody: () => Promise<void>) => Promise<void>>(
          async (_stepTitle: string, stepBody: () => Promise<void>) => {
            await stepBody();
          },
        ),
      },
    );

    defineFeature(feature, {
      test: mockTest,
      registry,
    });

    expect(describeTitle).toBe("Feature: Storefront Shopping");
    expect(scenarioTitle).toBe("Scenario: Add item to cart");

    // Execute scenario function
    await scenarioFn({ page: {}, context: {} });

    expect(executedSteps).toEqual(["store open", "added Keyboard", "cart 1"]);
    expect(mockTest.step).toHaveBeenCalledTimes(3);
  });

  it("should use test.skip when scenario is marked pending", () => {
    const pendingGherkin = `
Feature: Pending Feature
  @Pending
  Scenario: Pending Scenario
    Given step one
`;
    const feature = parseFeature(pendingGherkin);
    const mockTest: any = Object.assign(vi.fn<() => void>(), {
      describe: (_: string, fn: any) => fn(),
      skip: vi.fn<() => void>(),
      only: vi.fn<() => void>(),
      step: vi.fn<() => void>(),
    });

    const registry = createLibraryRegistry({ common: new Yadda.Library() });

    defineFeature(feature, {
      test: mockTest,
      registry,
    });

    expect(mockTest.skip).toHaveBeenCalledWith("Scenario: Pending Scenario", expect.any(Function));
  });

  it("should run through defineFeatureSuite with pre-parsed features", async () => {
    const feature = parseFeature(gherkinText);
    const mockTest: any = Object.assign(vi.fn<() => void>(), {
      describe: vi.fn<(_: string, fn: any) => void>((_: string, fn: any) => fn()),
      skip: vi.fn<() => void>(),
      only: vi.fn<() => void>(),
      step: vi.fn<() => void>(),
    });

    const registry = createLibraryRegistry({
      common: new Yadda.Library(),
      auth: new Yadda.Library(),
      catalog: new Yadda.Library(),
    });

    defineFeatureSuite({
      test: mockTest,
      registry,
      features: [feature],
    });

    expect(mockTest.describe).toHaveBeenCalledWith(
      "Feature: Storefront Shopping",
      expect.any(Function),
    );
  });
});
