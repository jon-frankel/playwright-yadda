# Actual Budget: a real app's Playwright suite, as feature files

[Actual Budget](https://github.com/actualbudget/actual) is a local-first personal-finance app with a serious Playwright suite (138 tests in `packages/desktop-client/e2e`, written with page objects). This example re-implements that suite as Gherkin features on playwright-yadda, running against Actual's own dev server, to see what the same coverage looks like in practice.

It is a comparison, so each test is ported from its original and the differences are recorded below.

## Running it

```bash
pnpm install
pnpm test     # the first run fetches and installs Actual: a few minutes, ~2 GB
```

`scripts/serve.mjs` fetches one pinned commit of Actual into `.cache/` (gitignored), installs its dependencies there with the Yarn version Actual pins (through corepack, nothing global), and runs its `yarn start` on port 3001. Delete `.cache/` to start over. Needs Node 22.18+ and git.

## Setup note

Actual's `yarn start` is several processes. `serve.mjs` keeps them in the process group Playwright started, because Playwright stops a web server by killing that group. Processes that left it outlived the run and held Playwright's output open, so teardown hung until its timeout.

## Structure

The step libraries are the layer the features talk to. Selectors and Playwright calls live in the steps. `steps/support/` holds only what two or more steps needed to share.

| Library        | Steps for                                                                   |
| -------------- | --------------------------------------------------------------------------- |
| `session`      | the demo budget, as a Playwright fixture (`fixtures` mode, `createBdd`)     |
| `common`       | navigation, filtering and right-clicking lists, assertions                  |
| `rules`        | the rule editor: conditions, actions, splits                                |
| `transactions` | adding a transaction and checking the transaction list                      |

`common` and `session` are defaults every scenario gets. `rules` and `transactions` are selected by path tags: `features/@rules/@transactions/` loads both for the features inside, and `features/@smoke/` tags the payees feature `@smoke` (`playwright test --grep @smoke`).

Other package features used: `Background:`, `Examples:` with `[name]` placeholders (payees), `createHooks` (`steps/hooks.ts`: an `AfterStep` that screenshots a failed step), `@slow` and `@fixme`.

## Pilot: rules and payees

| Original                   | Here                           |
| -------------------------- | ------------------------------ |
| `rules.test.ts` (4 tests)  | `rules.feature` (3 scenarios)  |
| `payees.test.ts` (5 tests) | `payees.feature` (4 scenarios) |

Not ported: the two "checks the page visuals" tests. They only compare screenshots, and in Actual's own suite the screenshot assertion is a no-op outside its visual-regression mode.

Also different from the originals: "is shown" checks use the first match instead of requiring exactly one, and Actual's animation-disabling trick is applied by the `budget` fixture.

### Numbers so far

Lines, pilot areas only. These are not a like-for-like comparison yet: the originals also lean on page objects (`navigation`, `account-page`, `configuration-page`) that are shared with the rest of the suite and not counted here, while these steps will be reused by every area still to come.

|                                      | Original | Here |
| ------------------------------------ | -------- | ---- |
| tests / scenarios                    | 7        | 7    |
| spec and feature lines               | 218      | 70   |
| page-object lines (3 files)          | 245      | n/a  |
| step and support lines               | n/a      | 334  |
| step definitions                     | n/a      | 17   |
| distinct step texts used by 45 steps | n/a      | 29   |

## Findings

Found while porting, about playwright-yadda 0.0.10 and the Yadda parser under it. Yadda's feature syntax is close to Gherkin but not the same, and the differences fail quietly:

1. **Tables need `---` fences.** Yadda joins a block into one step only between two `---` lines. Gherkin's bare `| … |` rows are not recognised: each becomes its own step, so no step can receive a table, and the failure is just `Undefined Step`. With the fences a step receives the whole table at run time as one string, which the step parses itself (`steps/support/blocks.ts`). WebStorm's own Gherkin parser underlines every fence as "Unexpected element", so the `ide/jetbrains` plugin parses these files as Yadda instead. `rules.feature` uses a table for the three transaction rows of the split rule, and the one-line `the first transaction shows …` step for the single-row case.
2. **Scenario Outline placeholders are `[name]`, not `<name>`.** Yadda expands `Examples:` itself, at parse time, but only for `[name]`. With Gherkin's `<name>` it still produces one scenario per row, with the placeholder left in the steps and identical titles, and nothing says so. Not used in this example yet.
3. **`given`, `when` and `then` are keyword-sensitive.** A step registered with `given` isn't found when a scenario writes it as `When` (`And` and `But` do match). Cucumber ignores the keyword. `define` isn't keyword-sensitive, but WebStorm's Cucumber support doesn't index it, so the IDE navigation in `ide/jetbrains` doesn't see it either.
4. **`Background:` works.** Yadda copies its steps into the start of every scenario before playwright-yadda sees them. Not used in this example yet.

These come from Yadda, not from playwright-yadda, which hands each parsed step to Yadda unchanged. They matter here because anyone arriving from Cucumber will hit them.
