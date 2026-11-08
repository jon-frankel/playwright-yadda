# Actual Budget: a real app's Playwright suite, as feature files

[Actual Budget](https://github.com/actualbudget/actual) is a local-first personal-finance app with a serious Playwright suite (138 tests in `packages/desktop-client/e2e`, written with page objects). This example re-implements that suite as Gherkin features on playwright-yadda, running against Actual's own dev server, to see what the same coverage looks like in practice.

It is a comparison, so each test is ported from its original and the differences are recorded below.

## Running it

The example installs `playwright-yadda` from a tarball built from this repository, so install it from the repository root, not with a bare `pnpm install`:

```bash
pnpm run examples:install actual
cd examples/actual
pnpm test
```

The first run fetches and installs Actual: a few minutes, ~2 GB.

`scripts/serve.mjs` fetches one pinned commit of Actual into `.cache/` (gitignored), installs its dependencies there with the Yarn version Actual pins (through corepack, nothing global), and runs its `yarn start` on port 3001. Delete `.cache/` to start over. Needs Node 22.18+ and git.

## Setup note

Actual's `yarn start` is several processes. `serve.mjs` keeps them in the process group Playwright started, because Playwright stops a web server by killing that group. Processes that left it outlived the run and held Playwright's output open, so teardown hung until its timeout.

## Structure

The step libraries are the layer the features talk to. Selectors and Playwright calls live in the steps. `steps/support/` holds only what two or more steps needed to share.

| Library        | Mode       | Steps for                                                        |
| -------------- | ---------- | ---------------------------------------------------------------- |
| `session`      | `fixtures` | the demo budget, as a Playwright fixture (`createBdd`)           |
| `common`       | `bound`    | navigation, filtering and right-clicking lists, assertions       |
| `rules`        | `bound`    | the rule editor: conditions, actions, splits, deleting a rule    |
| `transactions` | `fixtures` | adding, editing, filtering and checking transactions             |
| `accounts`     | `fixtures` | creating, closing and importing into accounts                    |
| `budgeting`    | `fixtures` | the budget table: summaries, transfers, spent amounts            |
| `schedules`    | `fixtures` | creating, posting and completing schedules (`define` macros)     |
| `mobile`       | `param`    | the narrow layout; the context is the first argument, not `this` |

`common` and `session` are defaults every scenario gets. The other libraries are selected by path tags: a directory named `@rules/@transactions/` loads both for the features inside, `@accounts/`, `@budgeting/`, `@schedules/` and `@mobile/` load the library of their name. `features/@smoke/` tags the payees feature `@smoke` (`playwright test --grep @smoke`).

## Package features exercised

| Feature                                                                           | Where                                                                                                                                                                                                                                                        |
| --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| library registry, default libraries                                               | every feature                                                                                                                                                                                                                                                |
| path tags (library selection, `@smoke`)                                           | the `features/@…` directories                                                                                                                                                                                                                                |
| scenario-level `@Libraries` (merge)                                               | `@schedules/schedules.feature`                                                                                                                                                                                                                               |
| `libraryInheritance: "override"`                                                  | `overrides/inheritance.feature` (run by `e2e/override.spec.ts` as a second suite): two libraries in `steps/inheritance.ts` define the same step text and report which one ran, so a scenario's `@Libraries` replacing its feature's is visible in the result |
| `bound`, `param` and `fixtures` step modes                                        | see the table above                                                                                                                                                                                                                                          |
| `createBdd` and a custom fixture                                                  | `steps/test.ts` (`budget`)                                                                                                                                                                                                                                   |
| `createContext`                                                                   | `createActualContext` in `steps/context.ts`                                                                                                                                                                                                                  |
| `define` with a macro context                                                     | `steps/schedules.ts`                                                                                                                                                                                                                                         |
| `Background:`, `Examples:` with `[name]`                                          | `Background:` in every feature but mobile settings and the override suite; `Examples:` in payees, accounts, transactions, budget, Markdown                                                                                                                   |
| `---` blocks (tables, CSV, QIF)                                                   | rules, transactions, accounts, schedules                                                                                                                                                                                                                     |
| Markdown feature                                                                  | `features/@markdown/payees.md`                                                                                                                                                                                                                               |
| `createHooks`: `AfterStep`, tag-filtered `BeforeScenario` with `name` and `order` | `steps/hooks.ts` (failed-step screenshot attached with `testInfo.attach`; the `@mobile` viewport). The viewport hook passes `name` and `order`, but there is only one such hook, so `order` has no observable effect here                                    |
| `@slow`, `@timeout`, `@retries`, `@fail`                                          | `@slow` in accounts and rules; `@timeout`, `@retries` and `@fail` in `@misc/expectations.feature`                                                                                                                                                            |
| reporter                                                                          | `playwright.config.ts`                                                                                                                                                                                                                                       |

Not exercised: `BeforeWorker` / `AfterWorker` (nothing here needs one), `@only`, `@skip`, `@pending`, `@fixme`, `@mode:serial` (every scenario gets its own demo budget, so none depend on another), `language`, and the reporter's `tags` and `fullStackTrace` options. `examples/basic` covers them.

## Pilot: rules and payees

The first ported areas. Accounts, transactions, budget, schedules and some of the mobile tests have been ported since; their counts are not in the table below.

| Original                   | Here                                                                                                                                                 |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `rules.test.ts` (4 tests)  | `features/@rules/@transactions/rules.feature` (4 scenarios: 3 ported, plus a delete scenario the original does not have)                             |
| `payees.test.ts` (5 tests) | `features/@smoke/payees.feature` (4 scenarios, one an outline over three payees), and its Markdown twin `features/@markdown/payees.md` (2 scenarios) |

The failed-step screenshot is attached to the report by the `AfterStep` hook with `testInfo.attach`.

Not ported: the two "checks the page visuals" tests (rules, payees). They only compare screenshots, and in Actual's own suite the screenshot assertion is a no-op outside its visual-regression mode.

Also different from the originals: "is shown" checks use the first match instead of requiring exactly one, and Actual's animation-disabling trick is applied by the `budget` fixture.

### Differences from the originals

Deliberately not ported, across all areas:

- Every `toMatchThemeScreenshots()` / `toHaveScreenshot()` call, including those inside otherwise ported tests (for example the account-creation and filter tests), and the tests that consist of nothing else ("checks the page visuals", "creates a full list of schedules", the command bar and help menu visuals).
- Transactions filtered "by date": the original picks day 20 of the current month and expects an empty table, which depends on the date the suite runs.
- The remaining original areas not ported yet: bank sync, onboarding, tour, reports, command bar, help menu, budget automations, calculator, and most of the mobile budget and transaction tests, plus the two account import tests ("an unchecked match with the same amount", "import notes checkbox").

### Numbers so far

Lines, pilot areas only (`rules.feature` and `payees.feature`; the Markdown twin is not counted). These are not a like-for-like comparison: the originals also lean on page objects that are shared with the rest of the suite, while the steps here are shared by every area.

|                        | Original | Here |
| ---------------------- | -------- | ---- |
| tests / scenarios      | 9        | 8    |
| spec and feature lines | 218      | 79   |

## Findings

Found while porting, about playwright-yadda 0.0.10 and the Yadda parser under it. Yadda's feature syntax is close to Gherkin but not the same, and the differences fail quietly:

1. **Tables need `---` fences.** Yadda joins a block into one step only between two `---` lines. Gherkin's bare `| … |` rows are not recognised: each becomes its own step, so no step can receive a table, and the failure is just `Undefined Step`. With the fences a step receives the whole table at run time as one string, which the step parses itself (`steps/support/blocks.ts`). WebStorm's own Gherkin parser underlines every fence as "Unexpected element", so the `ide/jetbrains` plugin parses these files as Yadda instead. `rules.feature` uses a table for the three transaction rows of the split rule, and the one-line `the first transaction shows …` step for the single-row case.
2. **Scenario Outline placeholders are `[name]`, not `<name>`.** Yadda expands `Examples:` itself, at parse time, but only for `[name]`. With Gherkin's `<name>` it still produces one scenario per row, with the placeholder left in the steps and identical titles, and nothing says so. Used in the payees, accounts, transactions, budget and Markdown features.
3. **`given`, `when` and `then` are keyword-sensitive.** A step registered with `given` isn't found when a scenario writes it as `When` (`And` and `But` do match). Cucumber ignores the keyword. `define` takes any keyword (see 5), but WebStorm's Cucumber support doesn't index it, so the IDE navigation in `ide/jetbrains` doesn't see it either.
4. **`Background:` works.** Yadda copies its steps into the start of every scenario before playwright-yadda sees them. Used in nearly every feature (all but mobile settings and the override suite).
5. **`define` patterns match the whole step line, keyword included.** `given`, `when` and `then` add the keyword to the pattern for you; `define` does not, so `/^the user…/` never matches `When the user…` and the step is `Undefined`. Write `/^\w+ the user…/`. A macro's `this` is the macro context, not the interpreter, so it cannot call other steps: compose helper functions instead.

These come from Yadda, not from playwright-yadda, which hands each parsed step to Yadda unchanged. They matter here because anyone arriving from Cucumber will hit them.
