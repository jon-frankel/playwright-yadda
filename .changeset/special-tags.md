---
"playwright-yadda": minor
---

Special tags control how Playwright runs a scenario: `@fixme`, `@fail`, `@slow`, `@timeout:N`, `@retries:N` and `@mode:parallel|serial|default` (on a feature), beside the existing `@only`, `@skip` and `@pending`. A scenario's tag overrides the feature's. Every tag is also passed to Playwright as the test's `tag` (Playwright 1.42 or later), so `--grep` and `--grep-invert` select scenarios by them.

Two behaviours change for the tags that already existed: a scenario's `@skip=false` or `@only=false` now overrides the feature's tag (and the tag with `=false` no longer counts as set), and `@skip:reason`, `@pending:reason` and `@only:reason` now act like their plain forms. `isScenarioPending` and `isScenarioOnly` follow the same rules, and `isScenarioPending` also counts `@fixme`.

The reporter (`playwright-yadda/reporter`) now follows each test's expected status: a scenario that is expected to fail (`@fail`, or `test.fail()` in your own steps) counts as passed when it fails, and as failed, with "Expected to fail, but passed.", when it passes.

Warnings about how scenarios are declared (an ignored `@mode`, an invalid `@timeout`, a scenario whose fixtures could not be worked out) are printed once per run. Playwright loads the spec in its main process and in every worker, and each used to print them. Now only the main process does, and `playwright-yadda/reporter` prints them itself, before the first scenario, for the scenarios in the run only. Each is also on its scenarios as a hidden `_playwright-yadda-warning` annotation.
