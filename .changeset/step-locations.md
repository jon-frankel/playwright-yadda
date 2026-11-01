---
"playwright-yadda": minor
---

Each step is reported at the line of the feature file it is written on, which is where Playwright shows it in its reports and trace viewer, instead of a line of this package. A step of a `Background:` is at its line in the background, and the examples of an outline are at the outline's lines. This needs Playwright 1.48 or later (`test.step`'s `location` option); older versions are unchanged. `parseFeature` now returns `line` on the feature and on each scenario, and `stepLines` on each scenario (the line each step starts on, a block under a step counting as part of it), and `runScenarioSteps` takes the steps' locations as an extra last argument.
