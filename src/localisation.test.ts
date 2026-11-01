import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { describe, expect, it, vi } from "vitest";
import Yadda from "yadda";
import { defineFeatureSuite, parseFeature } from "./feature.js";
import { createLibraryRegistry, createStepLibrary } from "./registry.js";

const { French, Russian } = Yadda.localisation;

const frenchGherkin = `
@Libraries=panier
Fonctionnalité: Le panier
  Scénario: Ajouter un article
    Soit un panier vide
    Quand le client ajoute "Clavier"
    Alors le panier contient 1 article
`;

const frenchMarkdown = `
# Fonctionnalité: Le panier
  ## Scénario: Ajouter un article
  - Soit un panier vide
  - Alors le panier contient 0 article
`;

function mockTest() {
  const titles: string[] = [];
  const scenarios: Array<() => Promise<void>> = [];
  const test: any = Object.assign(
    (title: string, ...rest: any[]) => {
      const fn = rest[rest.length - 1]; // (title, fn) or (title, details, fn)
      titles.push(title);
      scenarios.push(() => fn({ page: {}, context: {} }, {}));
    },
    {
      describe: (title: string, fn: () => void) => {
        titles.push(title);
        fn();
      },
      skip: vi.fn<() => void>(),
      only: vi.fn<() => void>(),
      step: async (_title: string, body: () => Promise<void>) => body(),
    },
  );
  return { test, titles, scenarios };
}

describe("localisation", () => {
  it("reads the headings of a feature in the language it is given", () => {
    const feature = parseFeature(frenchGherkin, "gherkin", undefined, French);
    expect(feature.title).toBe("Le panier");
    expect(feature.annotations).toEqual({ libraries: "panier" });
    expect(feature.scenarios.map((scenario) => scenario.title)).toEqual(["Ajouter un article"]);
    expect(feature.scenarios[0]?.steps).toHaveLength(3);
  });

  it("does not read them in English without it", () => {
    expect(() => parseFeature(frenchGherkin, "gherkin")).toThrow(/scenarios/);
  });

  it("reads Markdown headings in the language, and finds the file is Markdown by its word for Feature", () => {
    const feature = parseFeature(frenchMarkdown, "auto", undefined, French);
    expect(feature.title).toBe("Le panier");
    expect(feature.scenarios[0]?.steps).toHaveLength(2);
    // Without a language, the same text is not recognised as Markdown and so is read as Gherkin.
    expect(() => parseFeature(frenchMarkdown, "auto")).toThrow(/scenarios/);
  });

  it("puts the language's keywords in front of a step library's definitions", async () => {
    const executed: string[] = [];
    const library = createStepLibrary(undefined, "bound", French);
    library.given(/un panier vide/, () => executed.push("vide"));
    library.when(/le client ajoute "(.*)"/, (item: string) => executed.push(`ajoute ${item}`));
    library.then(/le panier contient (\d+) article/, (count: string) =>
      executed.push(`contient ${count}`),
    );

    const { test, titles, scenarios } = mockTest();
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "playwright-yadda-fr-"));
    try {
      fs.writeFileSync(path.join(dir, "panier.feature"), frenchGherkin);
      defineFeatureSuite({
        test,
        registry: createLibraryRegistry({ common: new Yadda.Library(), panier: library }),
        features: dir,
        language: French,
      });
      expect(titles).toEqual(["Feature: Le panier", "Scenario: Ajouter un article"]);
      await scenarios[0]?.();
    } finally {
      fs.rmSync(dir, { recursive: true, force: true });
    }
    expect(executed).toEqual(["vide", "ajoute Clavier", "contient 1"]);
  });

  it("loads Markdown features from a path in the language too", () => {
    const { test, titles } = mockTest();
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), "playwright-yadda-fr-md-"));
    try {
      fs.writeFileSync(path.join(dir, "panier.md"), frenchMarkdown);
      defineFeatureSuite({
        test,
        registry: createLibraryRegistry({ common: new Yadda.Library() }),
        features: dir,
        language: French,
      });
    } finally {
      fs.rmSync(dir, { recursive: true, force: true });
    }
    expect(titles).toEqual(["Feature: Le panier", "Scenario: Ajouter un article"]);
  });

  it("keeps English the default for the library and the parser", () => {
    const library = createStepLibrary();
    const executed: string[] = [];
    library.given(/the cart is empty/, () => executed.push("empty"));
    const feature = parseFeature("Feature: F\n  Scenario: S\n    Given the cart is empty");
    expect(feature.scenarios[0]?.steps).toEqual(["Given the cart is empty"]);
    new Yadda.Yadda(library).yadda(feature.scenarios[0]!.steps);
    expect(executed).toEqual(["empty"]);
  });

  it("works for a language with a different script", () => {
    const feature = parseFeature(
      "Функция: Корзина\n  Сценарий: Товар\n    Допустим пустая корзина",
      "gherkin",
      undefined,
      Russian,
    );
    expect(feature.title).toBe("Корзина");
    expect(feature.scenarios[0]?.title).toBe("Товар");
  });
});
