---
"playwright-yadda": minor
---

Special tags control how Playwright runs a scenario: `@fixme`, `@fail`, `@slow`, `@timeout:N`, `@retries:N` and `@mode:parallel|serial|default`, beside the existing `@only`, `@skip` and `@pending`. A scenario's tag overrides the feature's. Every tag is also passed to Playwright as the test's `tag` (Playwright 1.42 or later), so `--grep` and `--grep-invert` select scenarios by them.
