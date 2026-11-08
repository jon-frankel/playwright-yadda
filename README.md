# playwright-yadda

> Run Yadda BDD specifications in Playwright Test, with modular step libraries and Markdown or Gherkin features.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

`playwright-yadda` lets you run Behavior-Driven Development (BDD) specifications in [@playwright/test](https://playwright.dev/) using [Yadda](https://github.com/acuminous/yadda). Write specifications in Gherkin (`.feature`) or Markdown (`.md`), organize your step implementations into domain-specific libraries, and run them with `playwright test`.

## Features

- It parses features and registers them as tests when Playwright Test runs. Point `defineFeatureSuite()` at your `features/` folder and run `playwright test`.
- Every scenario is a native Playwright test, and every step runs inside `test.step()`. You get Trace Viewer timelines, screenshots, video recording, HTML reports, parallelism, and retries. Steps link to their lines in the `.feature` file.
- You group step definitions by domain (`storefront`, `admin`, `checkout`) and activate only the libraries a feature or scenario needs, using `@Libraries` tags or `@folder/` directory conventions. Two domains can use identical step phrasing without colliding.
- Steps can destructure typed fixtures from `test.extend()`. You control execution from feature files with tags such as `@only`, `@skip`, `@fixme`, `@slow`, `@timeout:N`, `@retries:N`, and `@mode:serial`, and you can register tag-filtered `BeforeScenario`, `AfterScenario`, `BeforeStep`, and `AfterStep` hooks.
- The `playwright-yadda/reporter` reporter prints step-by-step terminal output. A companion [JetBrains IDE plugin](ide/jetbrains) understands `@Libraries` scoping and Yadda's dialect.

## Why BDD?

As end-to-end test suites grow, raw browser scripts often become long lists of selectors, waits, and assertions that hide the product requirements being tested.

BDD separates **what** the application should do from **how** the test drives the browser:

- Features written in plain language (`Given` / `When` / `Then`) can be read and reviewed by engineers, QA, and product stakeholders.
- Once you define steps for your core workflows, new scenarios compose existing sentences instead of duplicating boilerplate setup and DOM queries.
- Because the specifications are the tests, the documentation stays in sync with what CI verifies.

## Why Yadda?

Most JavaScript BDD tools are built on Cucumber, which puts every step definition in your repository into a **single global namespace**. In a small project that works fine, but as your test suite grows across multiple teams or product areas, global steps become a bottleneck:

- A step like `When they remove the item` or `Given the user is logged in` can only have one definition across the entire project.
- Teams end up writing overly specific phrasing (`When the admin removes the catalog item on the inventory page`) just to avoid regex collisions with other features.

[Yadda](https://github.com/acuminous/yadda) works differently:

- Steps are organized into independent libraries instead of a global pool. With `playwright-yadda`, each feature or scenario loads only the libraries it needs (plus any shared defaults like `common`), so domain vocabularies stay separate.
- You can write features in standard Markdown (`# Feature: …`, `## Scenario: …`, and bulleted steps) alongside or instead of `.feature` files. GitHub renders Markdown natively.
- You can pass JSON payloads, CSV data, tables, or free-form text below any step between `---` fences, without escaping pipes in Gherkin tables.
- Scenario outlines use `[placeholder]` syntax with pipe-optional `Examples:` / `Where:` tables, and you can build reusable parameter converters with Yadda dictionaries.

## Why not `playwright-bdd`?

[`playwright-bdd`](https://github.com/vitalets/playwright-bdd) is a common way to run Gherkin features in Playwright, and `playwright-yadda` shares several of its conventions (such as `createBdd(test)` for typed fixtures, `@timeout:N` / `@retries:N` / `@mode:serial` control tags, and tag-filtered hooks).

Choose `playwright-yadda` when you want:

1. No `bddgen` code generation step. `playwright-bdd` compiles `.feature` files into generated `.spec.js` files on disk before Playwright runs, which requires a separate build step in CI and a file watcher during local development. `playwright-yadda` parses features and registers tests at runtime, so you run `playwright test`.
2. Scoped step libraries instead of global step matching. `playwright-bdd` uses Cucumber-style global step definitions, where overlapping patterns across domains collide. `playwright-yadda` scopes step resolution per feature or scenario through `@Libraries` and `@folder/` path tags.
3. Gherkin or Markdown specs, `---` fenced blocks, and dictionaries. `playwright-bdd` is limited to strict `@cucumber/gherkin` syntax. With `playwright-yadda`, you can write specs in Gherkin or Markdown, attach `---` fenced blocks (JSON, CSV, tables, text) to steps, and use Yadda dictionaries.

## Installation

```bash
pnpm add playwright-yadda
pnpm add -D yadda @types/yadda @playwright/test
```

## Quick start

### 1. Define step libraries

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

### 2. Create the library registry

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

### 3. Annotate features or scenarios

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

### 4. Configure and run Playwright Test

Create a spec file that points `defineFeatureSuite` at your `features/` directory:

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

Configure Playwright and enable [`playwright-yadda/reporter`](#reporter) for step-by-step terminal output and for `--list` output grouped by feature:

```typescript
// playwright.config.ts
import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  reporter: [["playwright-yadda/reporter"]],
});
```

Run the tests with the Playwright CLI:

```bash
pnpm playwright test
```

Each step is reported at its line in the `.feature` or `.md` file in Playwright's reports and trace viewer (Playwright 1.48+).

#### Feature file syntax (Gherkin and Markdown)

`defineFeatureSuite` loads `.feature`, `.md`, and `.markdown` files automatically (`parserType: "auto"`):

- **Scenario Outlines** use `[placeholder]` in the scenario title and steps (rather than Cucumber's `<placeholder>`), expanded from an `Examples:` or `Where:` table.
- **Multiline step arguments** (tables, JSON, CSV, text) are fenced between two `---` lines immediately below the step line and passed as the final argument to the step function.
- **Markdown features** (`.md`, `.markdown`) use `# Feature: …` and `## Scenario: …` headings with bulleted steps.

### 5. Special tags

Tags on a feature or a scenario can also control how Playwright runs it. A scenario's tag overrides the feature's tag of the same name and adds to the rest. For example, `@skip=false` on a scenario runs it even if its feature is `@skip`. `@skip`, `@pending`, and `@fixme` are treated as the same tag for overrides. `@timeout`, `@retries`, and `@mode` take no `=false`. To override a feature's value, use `@retries:0` or `@timeout:0` (no timeout at all). Yadda reads one tag per line, as `@name`, `@name=value`, or `@name:value` (the spelling playwright-bdd uses). For the tags in this table, `@name=value` and `@name:value` mean the same.

| Tag                                               | What it does                                                                                                                                                          |
| ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `@only`                                           | `test.only`                                                                                                                                                           |
| `@skip`, `@pending`                               | `test.skip`. The two are aliases. They win over `@fixme` and `@only`.                                                                                                 |
| `@fixme`                                          | `test.fixme`: skipped, and reported as a fixme. Wins over `@only`.                                                                                                    |
| `@fail`                                           | `test.fail()`: the scenario is expected to fail                                                                                                                       |
| `@slow`                                           | `test.slow()`: triples the timeout, including the scenario's `@timeout`                                                                                               |
| `@timeout:N`                                      | Sets a timeout of `N` milliseconds for the scenario, its fixtures, and its hooks.                                                                                     |
| `@retries:N`                                      | Retries a failing scenario up to `N` times (on the feature or the scenario; in a `@mode:serial` feature, place it on the feature rather than an individual scenario). |
| `@mode:parallel`, `@mode:serial`, `@mode:default` | Configures the execution mode for a feature (`test.describe.configure({ mode })`). Must be placed on a feature, not an individual scenario.                           |

```gherkin
@retries:2
@mode:serial
Feature: Checkout

  @slow
  @timeout:20000
  Scenario: Pays by card
    ...

  @fixme
  Scenario: Applies a gift card
    ...
```

Every tag is also passed to Playwright as the test's `tag` (Playwright 1.42 or later), so `--grep` and `--grep-invert` select by them: `playwright test --grep @smoke`, `playwright test --grep-invert @slow`. A tag with a value is passed as `@name:value` (`@timeout=5000` becomes `@timeout:5000`); one whose value has spaces, such as `@Libraries=auth, catalog`, is passed as `@name`. A tag switched off with `=false` is not passed. Where a scenario overrides a feature's tag, the scenario's value is the one passed. Yadda lowercases the names of tags, and for the `@name:value` spelling the value too, so `@Smoke` is `@smoke` to `--grep` and `@Area:Cart` is `@area:cart`. Only the tags in the table treat the two spellings alike: `@Libraries:auth` does not load a library.

Invalid tag values (`@timeout:soon`, `@retries:1.5`, `@mode:fast`) are ignored with a warning. Any other tag is passed through to Playwright.

### 6. Tags from path

A directory or file name that starts with `@` tags every feature inside it, so you don't have to repeat in a tag line what the location already specifies. Directories count from the `features` path given to `defineFeatureSuite`, and an `@name.feature` file name tags that file.

```
features/
├── @storefront/          # loads the `storefront` library
│   ├── cart.feature
│   └── @smoke.feature    # also tagged @smoke
└── @slow/@mode:serial/   # special tags work as directory names too
    └── import.feature
```

- Where `:` is not allowed in file names (Windows), write `@timeout=5000/` and `@mode=serial/`: `=` means the same.
- Path tags are added to the feature's tags, so they reach Playwright's `tag` list (`--grep @smoke`). Only the tag name (before `:` or `=`) is lowercased; a value keeps its case (`@Env=Staging/`). Path tags with whitespace are ignored with a warning because Playwright's `--grep` cannot match them.
- A path tag that names a registered library (case-insensitive) selects it, as if the feature said `@Libraries=storefront` (or your registry's custom `annotationKey`). It counts as the feature's own, so a scenario's `@Libraries` merges with it, or replaces it with `libraryInheritance: "override"`. A feature can also switch off a path library with `@storefront=false`.
- A tag written in the file wins over a path tag of the same name: `@mode:parallel` in the file beats an `@mode:serial/` directory.
- File tags strip `.feature`, `.md`, `.markdown`, and `.yadda.feature` / `.yadda.md` / `.yadda.markdown` extensions while preserving dots in the tag name (`@v1.2.feature` becomes `@v1.2`).
- A single file passed as `features` only has its own `@` file name counted. `loadFeatureFile(file, parserType, language, root)` takes the root as its fourth argument.

## API reference

### `createLibraryRegistry(libraries, options?)`

Creates a strongly typed `LibraryRegistryManager`.

- `defaultLibraries`: Library keys always included for every scenario (defaults to `['common']`).
- `inheritance`: `'merge'` (default) or `'override'` (scenario annotations replace feature annotations when the scenario specifies any).
- `annotationKey`: Annotation key to inspect for step libraries (defaults to `'libraries'`, case-insensitive).

`LibraryRegistryManager` methods:

- `registry.resolve(featureAnnotations?, scenarioAnnotations?, overrideOptions?)`: Resolves the ordered, deduplicated `StepLibrary[]` for a scenario. Throws `UnknownLibraryError` (with `requestedLibrary` and `availableLibraries`) if an unknown library is requested.
- `registry.getAvailableKeys()`: Returns all registered library keys.
- `registry.has(name)`: Checks whether a library key exists (case-insensitive).
- `registry.get(name)`: Returns the `StepLibrary` for `name` (case-insensitive), or `undefined`.

`resolveLibraries(registry, featureAnnotations?, scenarioAnnotations?, options?)` is a standalone helper that accepts either a plain `LibraryRegistry` record or a `LibraryRegistryManager`.

### `createStepLibrary(dictionary?, mode?, language?)`

Creates a Gherkin-localized step library supporting `given`, `when`, `then`, and `define`.

- `dictionary`: Optional `Yadda.Dictionary` for custom parameter converters and terms.
- `mode`: `'bound'` (default, binds context to `this`), `'param'` (passes context as 1st argument), or `'fixtures'` (passes the scenario's Playwright fixtures as 1st argument; see [Playwright fixtures in steps](#playwright-fixtures-in-steps)).
- `language`: One of Yadda's localizations, such as `Yadda.localisation.French` (default `Yadda.localisation.English`). It sets the keywords a step may start with (`Soit`, `Quand`, `Alors`, …); `given`, `when` and `then` stay the method names.

### `defineFeatureSuite(options)`

Discovers feature files and registers them with Playwright's `test` runner.

- `test`: The Playwright `test` instance.
- `registry`: The `LibraryRegistry` or `LibraryRegistryManager`.
- `features`: Directory path, file path, array of directory/file paths, or array of pre-parsed `ParsedFeature` objects. Directories are scanned recursively for `.feature`, `.md`, and `.markdown` files.
- `parserType`: `'auto'` (default; uses the Markdown parser for `.md`/`.markdown` files or `# Feature:` headings, and the Gherkin parser otherwise), `'gherkin'`, or `'markdown'`.
- `defaultLibraries`: Overrides `defaultLibraries` for this suite.
- `libraryInheritance`: `'merge'` (default) or `'override'`, overriding the registry's `inheritance` for this suite.
- `createContext`: Optional custom factory function to initialize scenario context (`(fixtures) => Promise<TCtx> | TCtx`). It receives `page`, `context` and `testInfo`, plus any other fixtures the scenario's steps use. It can't ask for fixtures itself: Playwright only sets up what a step or this library names.
- `hooks`: Optional hook registry from `createHooks()` (see [Hooks](#hooks)).
- `language`: The Yadda localization the feature files are written in (default `Yadda.localisation.English`). It sets the words read as `Feature:`, `Scenario:`, `Examples:` and so on. Use the same language for the step libraries.

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

Binds step libraries to your extended `test`, returning `{ createStepLibrary(dictionary?, language?) }` so the fixtures a step destructures are typed. See [Playwright fixtures in steps](#playwright-fixtures-in-steps).

### Lower-level exports

For custom runners or advanced workflows, `playwright-yadda` also exports:

- **Feature registration and parsing**:
  - `defineFeature(feature, options)`: Registers a single `ParsedFeature` with Playwright's `test`.
  - `parseFeature(content, parserType?, filepath?, language?)`: Parses Gherkin or Markdown feature content into a `ParsedFeature`.
  - `loadFeatureFile(filepath, parserType?, language?, root?)`: Loads and parses a feature file from disk and applies `@`-prefixed path tags.
  - `findFeatureFiles(targetPath)`: Recursively finds `.feature`, `.md`, and `.markdown` files in a directory (or returns `[path.resolve(targetPath)]` for a file).
  - `pathTagsOf(filepath, root?)`: Returns the `@`-prefixed directory and file tags (without `@`, outermost first) between `root` and `filepath`.
- **Step and scenario runners**:
  - `createScenarioYadda(libraries)`: Creates an isolated Yadda instance for `StepLibrary[]`.
  - `runStep(yadda, stepText, context)`: Promisified wrapper around `yadda.run()` that syncs properties mutated on `this` back to `context`.
  - `runScenarioSteps(yadda, steps, context, testStep?, stepHooks?, stopped?, locations?)`: Runs an array of steps in sequence, optionally wrapping each in `test.step` and running `stepHooks`.
- **Annotation and tag helpers**:
  - `getAnnotationValue(annotations, targetKey)`, `extractLibraryNames(annotations, annotationKey?)`, `resolveLibraryNames(featureAnnotations?, scenarioAnnotations?, options?)`.
  - `isScenarioPending(featureAnnotations?, scenarioAnnotations?)`, `isScenarioOnly(featureAnnotations?, scenarioAnnotations?)`.
  - `parseTagExpression(source)`: Compiles a boolean tag expression (`@mobile and not (@slow or @wip)`) into a predicate `(tags: readonly string[]) => boolean`.

## Hooks

`createHooks()` makes a registry of scenario, step and worker hooks, each optionally limited by a tag expression. Pass it to `defineFeatureSuite` as `hooks`.

```typescript
import { createHooks, defineFeatureSuite } from "playwright-yadda";

const hooks = createHooks();

hooks.BeforeScenario({ tags: "@mobile and not @slow" }, async ({ fixtures }) => {
  await fixtures.page.setViewportSize({ width: 390, height: 844 });
});
hooks.AfterStep(({ step, error }) => {
  if (error) console.log(`failed: ${step}`);
});
hooks.AfterScenario({ name: "cleanup", order: 10 }, async ({ ctx }) => ctx.cleanup?.());

defineFeatureSuite({ test, registry, features, hooks });
```

| Hook                               | Runs                                                                                                                                                           |
| ---------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `BeforeScenario` / `AfterScenario` | Around a scenario's steps. `AfterScenario` also runs when a step or a `BeforeScenario` hook fails, and when the test times out (see the list below the table). |
| `BeforeStep` / `AfterStep`         | Around each step, inside its `test.step()`. `AfterStep` gets the step's `error` and runs when the step fails.                                                  |
| `BeforeWorker` / `AfterWorker`     | In `test.beforeAll` / `test.afterAll` of each feature's describe, see below.                                                                                   |

When a test times out, `AfterScenario` hooks run from `afterEach`:

- The step in progress, if any, goes to `AfterStep` with the timeout as its `error`.
- An error from these hooks is only logged as a warning.
- The abandoned scenario runs no more steps or hooks if it resumes.
- A hook that is still running when the timeout hits is cut short. The hooks after it in the same list run from `afterEach` only if they hadn't started.

Each hook is called as `Hook(fn)` or `Hook({ tags, name, order }, fn)`:

- `tags` is an expression of `@tags` with `and`, `or`, `not`, and parentheses (`not` binds tightest, then `and`, then `or`). It's checked when the hook is registered and matched against the scenario's tags, including its feature's.
- `order` (default 0) sorts hooks, lowest first, with ties in registration order. Before hooks run in that order and After hooks in reverse.
- If one After hook throws, the others still run and the first error is reported. If a Before hook throws, the rest of the Before hooks and the steps are skipped.
- When a step or scenario has already failed, its error is the one reported, and an After hook's own error is only logged as a warning.
- `AfterScenario` hooks don't run if `createContext` throws, because the hooks get that context.
- A tag with a value matches only in full (`@owner:bob`, not `@owner`). `@name=value` and `@name:value` are the same tag, and a tag switched off with `=false` isn't matched.
- Tag matching ignores case (`@Wip` matches `@wip`). A tag whose value has spaces is passed to Playwright without it (`@owner=bob smith` is `@owner`).
- Playwright's own signals, such as `test.skip()` called in a step, reach `AfterStep` as its `error` too.
- Tags with parentheses in their names (`@issue(12)`) can't be used in expressions.

Hooks receive `{ feature, scenario, ctx, fixtures, tags }` (steps add `step`, and `AfterStep` `error`).

**Worker hooks run per feature, not once per worker.** They are `beforeAll`/`afterAll` in the feature's describe, so a worker that runs several features runs them once for each, and with `fullyParallel` once for every worker that gets a test of the feature. Their `tags` are matched against the feature's tags alone. They receive `{ feature, tags, testInfo }`, and no fixtures: Playwright only sets up the ones a `beforeAll` callback destructures. Register them before calling `defineFeatureSuite`: they are picked up when a feature is declared, while scenario and step hooks are looked up when each scenario runs. A once-per-worker hook would need an auto worker-scoped fixture on your `test`.

`onBeforeScenario` and `onAfterScenario` still work but are deprecated: use `BeforeScenario` and `AfterScenario`. Unlike `AfterScenario`, `onAfterScenario` doesn't run when the scenario fails. Where both are given, the old `onBeforeScenario` runs before the hooks, and the old `onAfterScenario` after the scenario's steps and before the `AfterScenario` hooks.

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

### Fixture rules and lifecycle

Only the fixtures destructured by the steps a scenario runs (plus `page` and `context`) are initialized for that scenario, before its first step runs, and torn down after its last step completes.

When writing `'fixtures'` steps:

- **Destructure in the first parameter**: Write `async ({ page, account }) => ...` (or `({})` if a step needs no fixtures). Don't use a plain identifier (`async (fixtures) => ...`) or rest spread (`{ page, ...rest }`), which will throw when the step is defined.
- **Use `$testInfo` for `TestInfo`**: Destructure `{ $testInfo }` to access Playwright's `TestInfo` (destructured `testInfo` is rejected at definition time).
- **Define all step libraries before calling `defineFeatureSuite`**: Fixtures are inspected when `defineFeatureSuite` registers the suite. Pass the step function itself rather than wrapping it in `.bind()` or a higher-order wrapper.
- **Accessing scenario context (`this`)**: In `'fixtures'` mode, the first argument is the fixtures object. If you also need the shared scenario context, use a regular `function` (not an arrow function) and access it via `this`.

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

The tags in effect are printed on one line, where they apply: a feature's after its title, and a scenario's other tags after the scenario's title. A tag a scenario overrides is printed on the scenario with its own value. They come from Playwright's `tag` (Playwright 1.42 or later). To turn them off, pass `{ tags: false }`:

```
features/@admin/@smoke.feature
Feature: Admin smoke test @admin @smoke
✓ Scenario: Administrator deletes product (113ms)
```

With `playwright test --list`, the reporter lists scenarios grouped by their feature file (along with any project name, feature/scenario tags, and a `Total: N scenarios in M features` summary) rather than showing the spec file location.

## IDE support

### WebStorm and other JetBrains IDEs

WebStorm's built-in Cucumber.js support already jumps from a step in a `.feature` file to its `.given()` / `.when()` / `.then()` definition. The companion plugin in [`ide/jetbrains`](ide/jetbrains) adds navigation in the other direction: press Cmd+B or use Find Usages on a step definition to list the feature steps that run it. It also understands `@Libraries`, so when two libraries define the same step text, navigation in both directions follows the libraries a scenario enables. Install **Playwright Yadda** from **Settings** > **Plugins** > **Marketplace** in WebStorm 2026.2+, or see its [README](ide/jetbrains/README.md) to install from GitHub Releases or build from source.

It also reads Yadda's own dialect, which WebStorm's Gherkin support does not: `---` fenced blocks (tables, JSON, CSV, text) under a step, `[name]` placeholders, `Where:` and `Examples:` rows without pipes, steps without a keyword, Yadda's own parse errors, and Markdown features. A `.feature` file is read as Yadda in any project that depends on `playwright-yadda`. For features in another language, set the same language in **Settings** > **Tools** > **Playwright Yadda** as in `defineFeatureSuite({ language })`.

## License

[MIT](LICENSE)
