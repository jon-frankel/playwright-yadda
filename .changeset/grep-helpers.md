---
"playwright-yadda": minor
---

`grepForFeature`, `grepForScenario` and `grepForScenarioOutline` build the `--grep` pattern that runs a feature, a scenario or the examples of an outline, and `featureTestTitle` and `scenarioTestTitle` give the titles the tests are declared with (so a pattern and the tests cannot disagree). The JetBrains plugin's patterns are checked against the same cases. Titles are now trimmed when the tests are declared: Yadda keeps a space left after a title in the feature file, which no pattern could match. A scenario outline whose title has a placeholder (`A cart with [n] keyboards`) is now selected by the run icon in WebStorm; it matched nothing before.
