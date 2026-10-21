# Playwright Yadda for JetBrains IDEs

IDE support for [playwright-yadda](../../README.md) in WebStorm and other JetBrains IDEs with JavaScript support.

WebStorm's bundled Cucumber.js support already jumps from a step in a `.feature` file to its definition, even for Yadda's `library.given(...)` style, because it indexes any call named `given`, `when`, `then`, `and` or `but`. It has no way back, and it knows nothing about `@Libraries`. This plugin adds both:

- **Cmd+B on a step definition** (on the `given` or on the step text) shows the feature steps that run it, instead of "Cannot find declaration to go to".
- **Find Usages** (Alt+F7) on a step definition lists them in the Find window, labelled "step definition".
- **`@Libraries` awareness, in both directions.** When two libraries define the same step text, as `examples/basic` does with "they remove the item", Cmd+B on a `.feature` step goes to the definition its scenario actually runs, and usages of a definition list only the scenarios that enable its library, the "Ambiguous step reference with N definitions" warning goes away once the scenario's libraries leave a single definition, and when a step really is ambiguous the Cmd+B chooser shows the file each definition is in.

- **Library names, both ways.** Cmd+B on a name in `@Libraries=storefront, admin` goes to the registry property that defines it (`storefront: storefrontSteps` in `createLibraryRegistry({ … })`). It goes to the property, not the library it points at, because the property is what the name means (`"wp-common": wpCommonSteps`); Cmd+B on its value reaches the library. A name no registry defines goes nowhere. From the property, Cmd+B and Find Usages list the feature and scenario tags that use the name, and renaming the property renames them too. Names don't say which registry they mean, so a name defined by two registries is a usage of both.
- **The green run icon** next to `Feature:` and `Scenario:` (and Run from the context menu on a step) runs the tests through Playwright, narrowed to that feature or scenario, with WebStorm's usual test tree, re-run and debugging. The Cucumber.js plugin only knows how to run `cucumber-js`, so before this it said "Nothing here".

Step navigation works for string steps and regex steps, in `.ts` and `.js` files.

## Requirements

- WebStorm, or another JetBrains IDE with JavaScript support, **2026.2 or newer** (build 262+).
- The bundled **Cucumber.js** and **Gherkin** plugins enabled (they are by default).

## Install

```bash
pnpm ide:jetbrains:build
```

Then in the IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and pick `ide/jetbrains/build/distributions/playwright-yadda-jetbrains-<version>.zip`.

## How `@Libraries` is worked out

The plugin reads the same three things the runtime does, from your source:

1. **The registry:** a `createLibraryRegistry({ key: library, … }, options)` call (or `new LibraryRegistryManager(…)`) with an object literal. Each value is followed through imports to its `createStepLibrary()` variable. Keys are matched case-insensitively, and quoted keys such as `"wp-common"` work.
2. **A definition's library:** the variable it is called on, so `adminSteps` in `adminSteps.when(…)`.
3. **The libraries a scenario enables:** `defaultLibraries` (the registry's option, or a `defineFeatureSuite({ defaultLibraries })` override), plus the feature's `@Libraries=…` and the scenario's, honouring `inheritance: "override"`. Tags are read per line, the way Yadda reads them, so `@Libraries=wp-common, wp-auth, wp-posts` works even though the IDE's Gherkin parser stops a tag at the first space.

**It only ever narrows, and only when it is sure.** A definition is left out only if all of the above are readable and none of its library's keys is enabled. Otherwise everything is shown, as it was before. That includes:

- a library that is not in a registry written as an object literal;
- `defaultLibraries` or `inheritance` set to something other than a literal, or a custom `annotationKey`;
- a `defineFeatureSuite` override that can't be tied to a registry;
- steps in a `Background:` (they run in every scenario) or under a `Rule:` (playwright-yadda doesn't run those scenarios);
- definitions called without a receiver, such as a bare `Given("…", fn)`.

If every candidate would be ruled out, because the step is defined but not for this scenario, Cmd+B shows them all rather than guessing.

## How running works

The icon creates a Playwright run configuration for the spec file that calls `defineFeatureSuite`, with `--grep` set from the test names playwright-yadda generates (`Feature: <title>` and `Scenario: <title>`, see `src/feature.ts`):

| Icon on          | Runs                                           |
| ---------------- | ---------------------------------------------- |
| `Scenario:` line | that scenario only (`Feature: … Scenario: …$`) |
| `Feature:` line  | every scenario of the feature                  |
| a step           | the scenario it is in                          |

It runs from the nearest directory above the spec that has a `playwright.config.*`, or else a `package.json`, so a monorepo with one Playwright project per package works. If the project has several specs that call `defineFeatureSuite`, the one whose path is nearest to the feature file is used, because the features directory a spec reads is computed at runtime and can't be read reliably. A scenario outline runs by name without the end anchor, so its examples are included.

## Limitations

- **Running relies on a JetBrains-internal module.** The Playwright run configuration (`intellij.aqua.runners.playwright.js`) is part of WebStorm's JavaScript plugin but not public API. It is only available in WebStorm and the other IDEs that bundle it, and `verifyPlugin` plus the run tests are what to check on a new IDE version.

- **No warning yet for a step whose library isn't enabled,** and no completion or validation of the names in `@Libraries=`. Those would be separate additions.
- **Which registry a feature runs under isn't known.** A definition is shown if any registry containing its library enables it.
- **Only what the Cucumber.js plugin indexes.** A step definition is a call whose method name is a Gherkin keyword and whose first argument is a string or regex literal. Anything else, such as `lib.define(...)` or a step text held in a variable, is not recognised, in the same way the IDE doesn't recognise it going forward.
- **`.feature` files only.** Yadda's Markdown features aren't Gherkin files to the IDE.
- **Tied to the Cucumber.js plugin's internals.** That plugin has no public API. The plugin is compiled and tested against build 262 only; run `./gradlew verifyPlugin` against a newer IDE before widening `sinceBuild`/`untilBuild`.

## Development

Needs a JDK 25. The runtime bundled with any 2026.2 IDE works: set `JAVA_HOME` to `<IDE>/Contents/jbr/Contents/Home` on macOS.

```bash
cd ide/jetbrains

./gradlew test          # headless platform tests
./gradlew buildPlugin   # build/distributions/*.zip
./gradlew verifyPlugin  # JetBrains Plugin Verifier against the target IDE
./gradlew runIde        # sandbox IDE with the plugin loaded
```

The first run downloads WebStorm (about 1 GB). To use an installed IDE instead, add `-PlocalIdePath=/path/to/WebStorm.app` to any of the commands above. `platformVersion` and `sinceBuild` live in `gradle.properties`.

The tests drive the same platform code paths the editor uses, including the exact decision Cmd+B makes (`GotoDeclarationOrUsageHandler2`), against a real WebStorm 2026.2 test sandbox. They also run against the real `examples/basic` and `examples/wordpress` files. They are not a substitute for clicking through it once in the real IDE.

## How it works

The Java Cucumber plugin supports the same round trip, so reverse navigation follows its design. It is also where the Cucumber.js plugin stops.

| Class                                    | Role                                                                                                                                   |
| ---------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| `YaddaStepPomDeclarationSearcher`        | Makes a step-definition call a declaration, so Cmd+B shows usages instead of failing.                                                  |
| `YaddaStepReferencesSearch`              | Finds the Gherkin steps whose reference resolves to the definition, using the Cucumber plugin's own matching.                          |
| `YaddaFindUsagesHandlerFactory`          | Enables Find Usages on the declaration and drops steps from scenarios that don't enable its library, for every consumer of the usages. |
| `YaddaStepGotoDeclarationHandler`        | Narrows Cmd+B on a `.feature` step to the definitions its scenario enables. Handlers run before reference resolution.                  |
| `YaddaStepTarget`                        | A definition as a row in the Cmd+B chooser, labelled with its project-relative file path.                                              |
| `YaddaAmbiguousStepSuppressor`           | Silences the Cucumber plugin's "Ambiguous step reference" warning when narrowing leaves one definition. Undefined steps still warn.    |
| `YaddaLibraryNameReferencesSearch`       | Reports the names in `@Libraries=…` as references to the registry property that defines them: usages, and rename.                      |
| `YaddaLibraryNameReference`              | One such name. Rename rewrites every use of it in the file at once.                                                                    |
| `LibraryNames`                           | Reads the names, and where they are, from the `@Libraries=` lines of a feature file.                                                   |
| `YaddaLibraryNameGotoDeclarationHandler` | Cmd+B on a name in `@Libraries=…` goes to the registry property that defines it.                                                       |
| `YaddaFeatureRunConfigurationProducer`   | The run icon on a feature or scenario: a Playwright run configuration for the spec, narrowed with `--grep`.                            |
| `YaddaFeatureSpecs`                      | Finds the spec file that calls `defineFeatureSuite` for a feature file.                                                                |
| `PlaywrightGrep`                         | Builds the `--grep` pattern from the generated `Feature:` and `Scenario:` titles, escaped and anchored.                                |
| `YaddaStepDescriptionProvider`           | Labels the declaration "step definition" in the Find window.                                                                           |
| `YaddaSteps`                             | What counts as a step definition: a cheap syntax check, confirmed against the Cucumber.js plugin's own list.                           |
| `LibraryScope`                           | The one question both directions ask: is this definition enabled for this step's scenario?                                             |
| `LibraryRegistries`                      | Reads `createLibraryRegistry(…)` calls and `defineFeatureSuite` overrides from source.                                                 |
| `FeatureTags`                            | Reads `@…` lines of a feature file like Yadda, and mirrors `resolveLibraryNames` from `src/annotations.ts`.                            |
