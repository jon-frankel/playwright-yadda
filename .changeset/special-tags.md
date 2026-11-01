---
"playwright-yadda": minor
---

Special tags control how Playwright runs a scenario: `@fixme`, `@fail`, `@slow`, `@timeout:N`, `@retries:N` and `@mode:parallel|serial|default` (on a feature), beside the existing `@only`, `@skip` and `@pending`. A scenario's tag overrides the feature's. Every tag is also passed to Playwright as the test's `tag` (Playwright 1.42 or later), so `--grep` and `--grep-invert` select scenarios by them.

Two behaviours change for the tags that already existed: a scenario's `@skip=false` or `@only=false` now overrides the feature's tag (and the tag with `=false` no longer counts as set), and `@skip:reason`, `@pending:reason` and `@only:reason` now act like their plain forms. `isScenarioPending` and `isScenarioOnly` follow the same rules, and also count `@fixme`.
