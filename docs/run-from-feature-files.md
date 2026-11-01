# Running and debugging from `.feature` files in Playwright tooling

Research for [#11](https://github.com/jon-frankel/playwright-yadda/issues/11). Everything below was run against Playwright 1.64.1 and `examples/basic` unless it says "from source", which means read in the code but not run. The VS Code extension was not run; its behaviour is from `microsoft/playwright-vscode` v1.1.20.

## Summary

- Scenarios are ordinary Playwright tests, so `--list`, `--ui`, `--grep`, `--debug`, traces and the VS Code Test Explorer all work today. They are grouped by spec file, `Feature:` and `Scenario:`.
- What is missing is the link to the `.feature` file: no gutter icons or CodeLens on `Scenario:` lines, and "go to test" or the Source tab opens playwright-yadda's own `feature.ts` instead.
- Running one scenario from its line with `--grep` is reliable. The title (`Feature: X` plus `Scenario: Y`) is already a unique key, because Playwright rejects duplicates, so no per-scenario id is needed.
- Playwright has no supported way to set a test's location. Faking it works for display but breaks running from UI mode, so it is not recommended.
- Recommendation: a small VS Code CodeLens extension that runs `playwright test <spec> -g <pattern>`, plus an optional step-location improvement. JetBrains already does the equivalent.

## 1. What works today with dynamically registered tests

| Tool                                       | Result                                                                                                                                                                                                                                     |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `playwright test --list`                   | Lists every scenario as `Feature: X › Scenario: Y`.                                                                                                                                                                                        |
| `--list --reporter=json`                   | Each test has a distinct `id`; all 44 in `examples/basic` share one `file:line`.                                                                                                                                                           |
| `--ui`                                     | Tests appear under `features.spec.ts` › `Feature:` › `Scenario:`. Running a single scenario works (1/1 passed). Steps show as actions, and their Source tab shows the step definition. A scenario's own Source tab shows `feature.ts:258`. |
| `--grep` / `-g`, `--grep-invert`           | Match the title path joined by spaces, plus tags.                                                                                                                                                                                          |
| VS Code extension (from source)            | Tree items use `test.location` as their file and line. Run and Debug go through the spec file plus test ids, so they work. There are no gutter icons in `.feature` files, and "go to test" opens `feature.ts` in `node_modules`.           |
| `--test-list`, `playwright test file:line` | Do not match (see below).                                                                                                                                                                                                                  |

**Why every test is at `feature.ts:258`.** Playwright takes a test's location from the call-stack frame that called `test()` (`wrapFunctionWithLocation` in `playwright/lib/common`, `stackFrames[1]`). That frame is `defineFeature` in this package, not the spec file and not the `.feature` file. So the ticket's premise ("points at that spec file") is slightly off. It points into playwright-yadda's own source, via source maps.

**Why `file:line` and `--test-list` do nothing.** Both need `test.location.file` to equal a test file Playwright loads. `playwright test e2e/features.spec.ts:258` and a `--test-list` entry `e2e/features.spec.ts › Feature: … › Scenario: …` both found 0 tests, because the location is `feature.ts`.

## 2. Running a scenario from its `.feature` line with `--grep`

It works. The JetBrains plugin already relies on it (`PlaywrightGrep.kt`), and I confirmed the patterns on 1.64:

```
Feature: Order checkout Scenario: A product that is not in the catalogue(?: @\S+)*$   → 1 test
Feature: Order checkout Scenario:                                                     → all 7 of the feature
```

- **Scenario titles repeat across features** (`Customer clears cart` is in two), so the `Feature:` part is needed.
- **Within one spec, the full title is unique or Playwright fails the load.** Verified: two feature files with the same Feature and Scenario title in one spec give `Error: duplicate test title … first declared in …`.
- **So a stable id (path plus line) is not needed.** A line number in the title would also change whenever the file is edited above it. The editor maps cursor line to titles by parsing the file; `feature-file` stays useful for reporters, since `--grep` cannot match annotations.
- Details to keep in the pattern: escape regex characters in titles, allow trailing tags (Playwright 1.42+ appends them to the matched title), and leave scenario outlines unanchored because their example titles extend the outline's.
- **Residual risk:** duplicates are only an error within one spec file. With several specs or projects, pass the spec path as well as `-g`. `-g` is also case-insensitive.

## 3. Editor integration

- **JetBrains: already done.** `ide/jetbrains` has a run icon on `Feature:`, `Scenario:` and steps, which builds a Playwright run configuration with `--grep`. Debug and re-run come with it. No follow-up needed except that the pattern code is a Kotlin copy.
- **VS Code: viable and small.** A CodeLens or gutter action on `Feature:` and `Scenario:` lines that runs `playwright test <spec> -g "<pattern>"`, with Debug via the extension's debug command or `--debug`. It needs:
  1. Find the spec that calls `defineFeatureSuite` (the JetBrains plugin uses the one nearest the feature file).
  2. Map cursor line to Feature and Scenario titles. This needs a parse, since `ParsedScenario` has no line numbers today.
  3. Build the pattern. Worth sharing as one tested helper (for example a `playwright-yadda/grep` export) so the Kotlin and TypeScript versions can be checked against each other.
- The Test Explorer would still show results by spec file rather than `.feature` file, which is acceptable.

## 4. Can the reported location be set?

- **For a test: no supported way.** the details argument of `test(title, details, body)` takes `tag` and `annotation` (this package passes both); there is no `location`.
- **For a step: yes.** `test.step(title, body, { location })` is public since Playwright 1.48 ("custom location for the step to be shown in test reports and trace viewer"). Verified: a reporter's `onStepBegin` receives `step.location` as `features/orders.feature:4`. `runStep` does not pass it today, and the steps would need line numbers from the parser. Source maps do not apply, as expected.
- **Unsupported hack, tried and rejected.** Registering each test from a `vm` script whose `filename` is the `.feature` file, with the call on the scenario's line, makes `--list` report `orders.feature:6:3`. In UI mode the Source tab then opens the `.feature` file with the scenario line highlighted, and VS Code would get gutter icons for free. But clicking Run in UI mode ran 0 of 0 tests, and `playwright test features/orders.feature` says "No tests found". Playwright groups tests by `location.file`, and the run request sends that path as a file filter, which the `.feature` file does not satisfy. It also depends on a Playwright internal. A `location` option on `TestDetails` would be needed upstream, and the file filter would have to keep matching the spec; worth an upstream issue, not a dependency.

## Recommendation and follow-ups

1. **Document** what already works (section 1) in the README, with the `-g` recipe for running one scenario.
2. **VS Code CodeLens extension** on `Scenario:` and `Feature:` lines (section 3). Depends on a line-aware parse.
3. **Line numbers in `ParsedScenario`/steps**, then pass `location` to `test.step` so traces, the HTML report and UI mode point at `.feature` lines. Playwright 1.48+; keep the old behaviour on older versions (the package supports `>=1.30`).
4. **Shared grep helper** exported from the package, with a test that the JetBrains Kotlin patterns match it.
5. **Upstream issue** asking for `location` in `TestDetails` (as steps have), noting that the file filter must keep using the spec file. Do not ship the `vm` hack.
