---
"playwright-yadda": minor
---

Tags from path. `@`-prefixed directory names under the features directory, and `@name.feature` file names, tag the features inside: the tags are added to the feature's annotations, passed to Playwright as test tags, and a tag that matches a registry key loads that library without `@Libraries`. `@skip/`, `@pending/` and `@only/` directories act like the explicit annotations. `loadFeatureFile` takes an optional `root`, and `tagsFromPath` is exported.
