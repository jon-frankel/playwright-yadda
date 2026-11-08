# playwright-yadda

## 0.1.2

### Patch Changes

- Release v0.1.2

## 0.1.1

### Patch Changes

- Release v0.1.1

## 0.1.0

### Minor Changes

- f5ec4b4: Steps can use Playwright fixtures. `createStepLibrary(dictionary, "fixtures")` passes the scenario's fixtures as a step's first argument, and `createBdd(test)` types them from your `test.extend({...})`. Each scenario's test function destructures only `page`, `context` and the fixtures its matching steps ask for, so other fixtures are not set up. `createContext` now also receives those fixtures.
- db407ed: `grepForFeature`, `grepForScenario` and `grepForScenarioOutline` build the `--grep` pattern that runs a feature, a scenario or the examples of an outline, and `featureTestTitle` and `scenarioTestTitle` give the titles the tests are declared with (so a pattern and the tests cannot disagree). The JetBrains plugin's patterns are checked against the same cases. Titles are now trimmed when the tests are declared: Yadda keeps a space left after a title in the feature file, which no pattern could match. A scenario outline whose title has a placeholder (`A cart with [n] keyboards`) is now selected by the run icon in WebStorm; it matched nothing before.
- 146b08b: The reporter prints the tags in effect, on one line where they apply: a feature's tags after its title, and the other tags of a scenario after the scenario's title. This needs Playwright 1.42 or later, and `{ tags: false }` turns it off.
- 29ef982: Special tags control how Playwright runs a scenario: `@fixme`, `@fail`, `@slow`, `@timeout:N`, `@retries:N` and `@mode:parallel|serial|default` (on a feature), beside the existing `@only`, `@skip` and `@pending`. A scenario's tag overrides the feature's. Every tag is also passed to Playwright as the test's `tag` (Playwright 1.42 or later), so `--grep` and `--grep-invert` select scenarios by them.
  
  Two behaviours change for the tags that already existed: a scenario's `@skip=false` or `@only=false` now overrides the feature's tag (and the tag with `=false` no longer counts as set), and `@skip:reason`, `@pending:reason` and `@only:reason` now act like their plain forms. `isScenarioPending` and `isScenarioOnly` follow the same rules, and `isScenarioPending` also counts `@fixme`.
  
  The reporter (`playwright-yadda/reporter`) now follows each test's expected status: a scenario that is expected to fail (`@fail`, or `test.fail()` in your own steps) counts as passed when it fails, and as failed, with "Expected to fail, but passed.", when it passes.
  
  Warnings about how scenarios are declared (an ignored `@mode`, an invalid `@timeout`, a scenario whose fixtures could not be worked out) are printed once per run. Playwright loads the spec in its main process and in every worker, and each used to print them. Now only the main process does, and `playwright-yadda/reporter` prints them itself, before the first scenario, for the scenarios in the run only. Each is also on its scenarios as a hidden `_playwright-yadda-warning` annotation.
- db407ed: Each step is reported at the line of the feature file it is written on, which is where Playwright shows it in its reports and trace viewer, instead of a line of this package. A step of a `Background:` is at its line in the background, and the examples of an outline are at the outline's lines. This needs Playwright 1.48 or later (`test.step`'s `location` option); older versions are unchanged. `parseFeature` now returns `line` on the feature and on each scenario, and `stepLines` on each scenario (the line each step starts on, a block under a step counting as part of it), and `runScenarioSteps` takes the steps' locations as an extra last argument.
- c6e6aca: Tags from path: `@`-prefixed directory names below the `features` path, and `@name.feature` file names, tag the features inside them, including special tags such as `@slow/` and `@mode:serial/`. A path tag that names a registered library loads it without `@Libraries`. A tag written in the file wins over a path tag of the same name. `loadFeatureFile` takes a `root` as a fourth argument, and `pathTagsOf` is exported.

### Patch Changes

- de2be51: `playwright test --list` with the playwright-yadda reporter lists the scenarios by feature file, with their tags and a total, instead of printing "Running N scenarios" and "0 passed" for a run that never happened.
