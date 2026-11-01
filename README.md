# playwright-yadda

> Dynamic step library loading for Playwright and Yadda BDD test suites.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

`playwright-yadda` bridges [Yadda](https://github.com/acuminous/yadda) and [@playwright/test](https://playwright.dev/) with **dynamic step library loading**. Instead of compiling all step definitions into a single global regex dictionary, features and scenarios declare their dependencies via annotations (e.g. `@Libraries=auth, catalog, checkout`).

---

## Key Benefits

- **Zero Global Regex Collisions**: Domain step definitions remain strictly isolated. Two domains can use identical step text without conflict.
- **Trace Viewer & HTML Report Fidelity**: Every Gherkin step is executed inside Playwright's `test.step()`, giving you granular execution timelines, screenshots, and traces.
- **Negligible Overhead**: An isolated Yadda runner is instantiated per scenario with only the relevant pre-compiled libraries.
- **Native Async & State Persistence**: Supports modern `async/await` steps and automatically persists scenario context state across steps.

---

## Installation

```bash
pnpm add playwright-yadda
pnpm add -D yadda @types/yadda @playwright/test
```

---

## Quick Start

### 1. Define Step Libraries

Use `createStepLibrary()` to define domain-specific step libraries with English Gherkin keywords (`given`, `when`, `then`, `define`):

```typescript
// steps/storefront.ts
import { createStepLibrary } from "playwright-yadda";
import { expect } from "@playwright/test";

export const storefrontSteps = createStepLibrary();

storefrontSteps.given("a customer is on the storefront", function () {
  this.cart = ["Mechanical Keyboard"];
});

storefrontSteps.when("they remove the item", function () {
  this.cart = [];
});

storefrontSteps.then("the cart count is 0", function () {
  expect(this.cart).toHaveLength(0);
});
```

### 2. Create the Library Registry

```typescript
// steps/registry.ts
import { createLibraryRegistry } from "playwright-yadda";
import { commonSteps } from "./common.js";
import { storefrontSteps } from "./storefront.js";
import { adminSteps } from "./admin.js";

export const registry = createLibraryRegistry({
  common: commonSteps, // Included by default for every scenario
  storefront: storefrontSteps,
  admin: adminSteps,
});
```

### 3. Annotate Features or Scenarios

```gherkin
# features/storefront.feature
@Libraries=storefront
Feature: Storefront Shopping
  As a customer
  I want to manage my cart

  Scenario: Customer clears cart
    Given the test session is active
    And a customer is on the storefront
    When they remove the item
    Then the cart count is 0
```

### 4. Run with Playwright Test

```typescript
// e2e/features.spec.ts
import path from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "@playwright/test";
import { defineFeatureSuite } from "playwright-yadda";
import { registry } from "../steps/registry.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

defineFeatureSuite({
  test,
  registry,
  features: path.resolve(__dirname, "../features"),
});
```

Run tests with standard Playwright CLI:

```bash
pnpm playwright test
```

### 5. Special tags

Tags on a feature or a scenario can also control how Playwright runs it. A scenario's tag overrides the feature's tag of the same name, and adds to the rest. Yadda reads one tag per line, as `@name`, `@name=value` or, as playwright-bdd writes it, `@name:value`; the last two mean the same.

| Tag                                               | What it does                                                                                                                                                                                                     |
| ------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `@only`                                           | `test.only`                                                                                                                                                                                                      |
| `@skip`, `@pending`                               | `test.skip`. The two are aliases.                                                                                                                                                                                |
| `@fixme`                                          | `test.fixme`: skipped, and reported as a fixme. `@skip` or `@pending` wins if both are given.                                                                                                                    |
| `@fail`                                           | `test.fail()`: the scenario is expected to fail                                                                                                                                                                  |
| `@slow`                                           | `test.slow()`: triples the timeout                                                                                                                                                                               |
| `@timeout:N`                                      | `test.setTimeout(N)`, in milliseconds. On a feature: `test.describe.configure({ timeout: N })`.                                                                                                                  |
| `@retries:N`                                      | `test.describe.configure({ retries: N })`. On a scenario it gets a describe of its own, as Playwright has no per-test form ([microsoft/playwright#10825](https://github.com/microsoft/playwright/issues/10825)). |
| `@mode:parallel`, `@mode:serial`, `@mode:default` | `test.describe.configure({ mode })`                                                                                                                                                                              |

```gherkin
@retries:2
@mode:serial
Feature: Checkout

  @slow
  @timeout:60000
  Scenario: Pays by card
    ...

  @fixme
  Scenario: Applies a gift card
    ...
```

Every tag is also passed to Playwright as the test's `tag` (Playwright 1.42 or later), so `--grep` and `--grep-invert` select by them: `playwright test --grep @smoke`, `playwright test --grep-invert @slow`. A tag with a value is passed as `@name:value` (`@timeout=5000` becomes `@timeout:5000`); one whose value has spaces, such as `@Libraries=auth, catalog`, is passed as `@name`. Yadda lowercases tag names, so `@Smoke` is `@smoke` to `--grep`.

A value that is not valid (`@timeout:soon`, `@mode:fast`) is ignored with a warning. Any other tag does nothing but get passed to Playwright. `@lock` is not handled yet.

---

## API Reference

### `createLibraryRegistry(libraries, options?)`

Creates a typed registry manager.

- `defaultLibraries`: Library keys always included for every scenario (defaults to `['common']`).
- `inheritance`: `'merge'` (default) or `'override'` (scenario annotations replace feature annotations).

### `createStepLibrary(dictionary?, mode?, language?)`

Creates a Gherkin-localised step library supporting `given`, `when`, `then`, and `define`.

- `mode`: `'bound'` (default, binds context to `this`), `'param'` (passes context as 1st argument), or `'fixtures'` (passes the scenario's Playwright fixtures as 1st argument; see [Playwright fixtures in steps](#playwright-fixtures-in-steps)).
- `language`: One of Yadda's localisations, such as `Yadda.localisation.French` (default `Yadda.localisation.English`). It sets the keywords a step may start with (`Soit`, `Quand`, `Alors`, …); `given`, `when` and `then` stay the method names.

### `defineFeatureSuite(options)`

Discovers feature files and registers them with Playwright's `test` runner.

- `test`: The Playwright `test` instance.
- `registry`: The `LibraryRegistry` or `LibraryRegistryManager`.
- `features`: Directory path, file path, glob pattern, or pre-parsed feature array.
- `createContext`: Optional custom factory function to initialize scenario context. It receives `page`, `context` and `testInfo`, plus any other fixtures the scenario's steps use. It cannot ask for fixtures itself: Playwright only sets up what a step or this library names.
- `language`: The Yadda localisation the feature files are written in (default `Yadda.localisation.English`). It sets the words read as `Feature:`, `Scenario:`, `Examples:` and so on. Use the same language for the step libraries.

```ts
import Yadda from "yadda";

export const steps = createStepLibrary(undefined, "bound", Yadda.localisation.French);
steps.given(/^un panier vide$/, async function () {});

defineFeatureSuite({
  test,
  registry,
  features: path.resolve(__dirname, "../features"),
  language: Yadda.localisation.French,
});
```

The test names stay `Feature: …` and `Scenario: …`. Yadda 3.2 can't build a parser for Dutch (its scenario word is not a valid regular expression), and its German word list gives scenarios no title.

### `createBdd(test)`

Binds step libraries to your extended `test`, so the fixtures a step destructures are typed. See below.

---

## Playwright fixtures in steps

Steps can use the fixtures of your `test.extend({...})`, and Playwright's own (`request`, `browserName`, `baseURL`, …), the way a Playwright test does:

```ts
// steps/test.ts
import { test as base } from "@playwright/test";

export const test = base.extend<{ account: Account }>({
  account: async ({}, use) => {
    const account = await createAccount();
    await use(account);
    await account.delete();
  },
});

// steps/accounts.ts
import { createBdd } from "playwright-yadda";
import { test } from "./test.js";

const { createStepLibrary } = createBdd(test);
export const accountSteps = createStepLibrary();

accountSteps.given("a signed-in user", async ({ page, account }) => {
  await page.goto(account.loginUrl);
});

accountSteps.then(/^the balance is (\d+)$/, async ({ account, $testInfo }, balance: string) => {
  // ...
});

// e2e/features.spec.ts
defineFeatureSuite({ test, registry, features });
```

- The first argument holds the scenario's fixtures and `$testInfo` (its `TestInfo`); captured step arguments follow. Without `createBdd`, `createStepLibrary(dictionary, "fixtures", language)` does the same with untyped fixtures.
- Pass the same extended `test` to `defineFeatureSuite`. `createBdd(test)` only reads its type.
- `'bound'`, `'param'` and `'fixtures'` libraries can be mixed in one registry, and `createContext` keeps working.

### Which fixtures a scenario sets up

Playwright decides what to set up from the destructuring pattern of the test function's first argument. There is no generated spec file here, so for each scenario the test function is built when the suite is declared, with a pattern naming:

1. `page` and `context`, always. The default scenario context (`{ page, context, testInfo }`) is made of them, so `'bound'` and `'param'` steps keep working.
2. the fixtures destructured by the step definition Yadda would run for each of the scenario's steps, from the libraries the scenario enables (`@Libraries`, on the feature or the scenario, plus the defaults).

A fixture none of those steps destructures is not set up, so an expensive fixture costs only the scenarios whose steps use it.

### When fixtures are set up

Playwright sets up a test's fixtures before its first step runs and tears them down after its last, as for any test. So every fixture the scenario's steps use, including those of `Background:` steps, exists for the whole scenario, even if only the final step uses it. A fixture that throws while being set up fails the scenario before any step runs, and the failure is not tied to a step. They are set up in the order the scenario's steps first use them (after `page` and `context`), with a fixture's own dependencies first. `page` and `context` are always set up, so a scenario that only needs `request` still launches a browser.

Limits of reading the pattern from the step definition:

- **Destructure in the first parameter.** `async ({ page }) => ...` works. `async (fixtures) => ...` and a rest property (`{ page, ...rest }`) throw when the step is defined, as they would in a Playwright test. Use `({})` for a step that needs none.
- **Yadda's choice between definitions is repeated.** When a step text matches several definitions (a regex and a more specific one, or the same text in two libraries), the closest pattern wins, and a tie goes to the library of the previous step, as Yadda does when it runs the steps. Only the winner's fixtures are set up. A step Yadda cannot choose a definition for (ambiguous, or defined nowhere) sets up nothing, since the scenario fails when it runs, before any definition could use its fixtures.
- **Pass the function you wrote.** The names are read from `fn.toString()`. A wrapped or `.bind()`-ed function hides its parameters, and so would a bundler that rewrites destructuring (a minifier keeps the property names, which is all that is read).
- **Plain identifiers.** Fixture names like `"my-fixture"` cannot be destructured this way; Playwright reads them the same way.
- **Step styles are Yadda's own.** A step may be sync, return a promise, or take a trailing `next` callback; the first argument is just replaced by the fixtures.
- **A fixture the `test` does not have stops the whole run.** Playwright rejects a test that names an unknown fixture when it loads the spec file, so one typo in one step means no tests run. The message is `Test has unknown parameter "pgae"`, shown at a line inside playwright-yadda (`src/feature.ts`), not at your step, scenario or feature file, so search your step libraries for the name. `testInfo` is the common one: the `TestInfo` is `$testInfo` here, and `testInfo` throws when the step is defined.
- **Define every step library before the suite is declared.** The fixtures are collected when `defineFeatureSuite` runs. A step defined later fails when it runs with an error naming the fixture it did not get, instead of receiving `undefined`.
- **Names starting with `$`** are fixtures too, except `$testInfo`.
- **Declaring the suite does the matching up front.** Each distinct step line is matched against the patterns of the libraries its scenario enables, once per suite; repeated lines and repeated sequences cost nothing more. A scenario that enables no `'fixtures'`-mode library costs nothing; once it enables one, all of its enabled libraries are matched, because Yadda ranks across them, so keep fixtures libraries out of `defaultLibraries` unless every scenario needs them. With every line distinct, 16,000 lines against 500 patterns in one library takes about 5 seconds, and Playwright loads the spec in the runner and in each worker, so split large libraries by `@Libraries` to keep what each scenario matches small.
- **Unknown libraries** fail the scenario with `UnknownLibraryError` when it runs. If the registry has a `'fixtures'`-mode library, a warning is also logged when the suite is declared, once per problem in each process, saying that only `page` and `context` are set up for those scenarios.

### The scenario context in `'fixtures'` steps

The first argument is the fixtures, not the scenario context built by `createContext` or set by `'bound'` and `'param'` steps. An arrow function cannot reach that context. A `function` step can: `this` is the scenario context there, as in `'bound'` mode, and what it sets is carried to later steps. The third argument of `define` (`macroContext`) is reachable the same way, and not as a fixture.

---

## Reporter

A Playwright reporter prints each scenario with a ✓ / ✗ beside every step and the stack trace of the failing step below it:

```ts
// playwright.config.ts
export default defineConfig({
  reporter: [["playwright-yadda/reporter"]],
});
```

Stack traces hide frames from yadda and playwright-yadda so they start at your step code. To keep them, pass `{ fullStackTrace: true }`:

```ts
reporter: [["playwright-yadda/reporter", { fullStackTrace: true }]],
```

## IDE Support

### WebStorm and other JetBrains IDEs

WebStorm's built-in Cucumber.js support already jumps from a step in a `.feature` file to its `.given()` / `.when()` / `.then()` definition. The companion plugin in [`ide/jetbrains`](ide/jetbrains) adds the way back: **Cmd+B or Find Usages on a step definition lists the feature steps that run it.** It also understands `@Libraries`, so when two libraries define the same step text, navigation in both directions follows the libraries a scenario enables. See its [README](ide/jetbrains/README.md) to build and install it.

It also reads Yadda's own dialect, which WebStorm's Gherkin support does not: `---` fenced blocks (tables, JSON, CSV, text) under a step, `[name]` placeholders, `Where:` and `Examples:` rows without pipes, steps without a keyword, Yadda's own parse errors, and Markdown features. A `.feature` file is read as Yadda in any project that depends on `playwright-yadda`. For features in another language, set the same language in Settings | Tools | Playwright Yadda as in `defineFeatureSuite({ language })`.

---

## License

[MIT](LICENSE)
