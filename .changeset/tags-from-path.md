---
"playwright-yadda": minor
---

Tags from path: `@`-prefixed directory names below the `features` path, and `@name.feature` file names, tag the features inside them, including special tags such as `@slow/` and `@mode:serial/`. A path tag that names a registered library loads it without `@Libraries`. A tag written in the file wins over a path tag of the same name. `loadFeatureFile` takes a `root` as a fourth argument, and `pathTagsOf` is exported.
