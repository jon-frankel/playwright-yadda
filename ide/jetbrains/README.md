# Playwright Yadda for JetBrains IDEs

IDE support for [playwright-yadda](../../README.md) in WebStorm and other JetBrains IDEs with JavaScript support.

WebStorm's bundled Cucumber.js support already jumps from a step in a `.feature` file to its definition, even for Yadda's `library.given(...)` style, because it indexes any call named `given`, `when`, `then`, `and` or `but`. It has no way back, and it knows nothing about `@Libraries`. This plugin adds both:

- **Cmd+B on a step definition** (on the `given` or on the step text) shows the feature steps that run it, instead of "Cannot find declaration to go to".
- **Find Usages** (Alt+F7) on a step definition lists them in the Find window, labelled "step definition".
- **`@Libraries` awareness, in both directions.** When two libraries define the same step text, as `examples/basic` does with "they remove the item", Cmd+B on a `.feature` step goes to the definition its scenario actually runs, and usages of a definition list only the scenarios that enable its library, the "Ambiguous step reference with N definitions" warning goes away once the scenario's libraries leave a single definition, and when a step really is ambiguous the Cmd+B chooser shows the file each definition is in.

- **Library names, both ways.** Cmd+B on a name in `@Libraries=storefront, admin` goes to the registry property that defines it (`storefront: storefrontSteps` in `createLibraryRegistry({ … })`). It goes to the property, not the library it points at, because the property is what the name means (`"wp-common": wpCommonSteps`); Cmd+B on its value reaches the library. A name no registry defines goes nowhere. From the property, Cmd+B and Find Usages list the feature and scenario tags that use the name, and renaming the property renames them too. Names don't say which registry they mean, so a name defined by two registries is a usage of both.
- **The green run icon** next to `Feature:` and `Scenario:` (and Run from the context menu on a step) runs the tests through Playwright, narrowed to that feature or scenario, with WebStorm's usual test tree, re-run and debugging. The Cucumber.js plugin only knows how to run `cucumber-js`, so before this it said "Nothing here".

- **Yadda's multi-line steps.** WebStorm's Gherkin parser flags Yadda's `---` fences as "Unexpected element". In a project that depends on `playwright-yadda` (some `package.json` above the feature lists it), `.feature` files are parsed as Yadda instead, and so are files named `*.yadda.feature` anywhere. A block between two lines of dashes belongs to the step above it:
  - a block of `| … |` rows is that step's table, which Reformat Code aligns like any Gherkin table;
  - any other block (JSON, CSV, prose) is that step's doc string, closing fence optional on the last step. Yadda's `--- json` remark after the dashes is part of the fence;
  - the remark can also name a language: `--- json` (or `--- sql`, `--- xml`, any language the IDE knows by that name) injects it into the block, with its highlighting, error checking and Reformat. A remark that is not a language (`--- the response body`) injects nothing;
  - Enter at the end of an opening fence adds the closing one (with the same dashes), unless content for it is already under it, and "Edit JSON Fragment" writes back between the fences instead of turning them into `"""`;
  - `--- csv` and `--- tsv` blocks are checked for rows with more or fewer fields than the header, and for a quoted field left open (WebStorm itself has no CSV language, so the "Ragged CSV block" inspection does that checking; with a CSV plugin installed, `--- csv` is injected as well);
  - typing a `|` in a table row pads the row so the pipe lines up under the one above, as it does in Gherkin (Gherkin's own handler only acts on files of its own type, and misses a table on the last line of a file).

  Everything else in the plugin works on these files too.

- **What Yadda would make of the file.** `YaddaGrammar` is Yadda's own feature parser (3.2, English) ported, and it is held to the real one by a corpus generated from it (`src/test/resources/yadda/generate-parser-cases.mjs`). From it:
  - **"Feature Yadda can't parse"** (error) puts Yadda's own message on the line that is wrong, before the tests load the file: `Scenario requires one or more steps` (on the empty scenario's heading), `Incorrect number of fields in example table`, `Invalid annotation: a @b`, `Indentation error`, `Feature is unexpected at this time`, and the rest. Yadda stops at its first error, and so does this.
  - **"Rule whose scenarios are not run"** (warning) on a `Rule:`: Yadda parses rules, but playwright-yadda defines tests for the feature's own scenarios only.
  - **"Unused examples column"** (grey) replaces Gherkin's check, which looks for `<name>` and so reported every column. A column counts as used by `[name]`, `[ name ]`, `[name.index]`, `[name.start.line]` or `[name.start.column]` anywhere in the scenario, tables and blocks included.
  - Gherkin's "Scenario with Examples section", "Scenario outline without Examples" and "Unused table column" checks are silenced in Yadda files: `Scenario:` takes examples, and a `Scenario Outline:` without any is a scenario.
  - **Step definitions see what Yadda sees.** The Cucumber plugin matches a step by its first line with `<name>` filled in; Yadda matches all of it, fenced block included, with `[name]` filled in from the examples. A second text is offered for such steps, so `/^the rows are:\n([\s\S]+)$/` resolves for a step with a block and `/^the total is ([\d.]+)$/` for `the total is [total]`. An outline step is read for the first row of its examples; Yadda reads every row.

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

- **Yadda syntax is covered for multi-line steps, and checked as a whole, but the parse tree is still Gherkin's.** `Examples:` rows without outer pipes (`qty | total`), steps without a keyword, `Where:` followed by such rows, `┆` separators and the Markdown parser are still read the way Gherkin would read them, so those rows are not a table to Reformat. Write `| qty | total |` for now. Reformat Code doesn't move a fence with its step, a quick fix inside an injected block still goes through Gherkin's `updateText` (which assumes `"""`; untested), and `--- csv` is highlighted only if a CSV plugin (such as Rainbow CSV) is installed.
- **A definition with a block matches the block, not only the step.** `/^the rows are:\n([\s\S]+)$/` resolves for a step with a block under it. Because the first line alone is offered too, `/^the rows are:(?:\n([\s\S]+))?$/` and `/^the rows are:$/` also resolve for one (Yadda would not match the last).
- **Usages of a regex step definition are not found in a `*.yadda.feature` file,** only in `.feature` files (the Cucumber plugin's search leaves out other file types). String definitions are found in both.
- **Step definitions are what the Cucumber.js plugin indexes,** so a string pattern is a Cucumber expression there, where Yadda reads `$term` and its dictionary. Yadda's `define` and `$term` are not understood.
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

| Class                                           | Role                                                                                                                                    |
| ----------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `YaddaStepPomDeclarationSearcher`               | Makes a step-definition call a declaration, so Cmd+B shows usages instead of failing.                                                   |
| `YaddaStepReferencesSearch`                     | Finds the Gherkin steps whose reference resolves to the definition, using the Cucumber plugin's own matching.                           |
| `YaddaFindUsagesHandlerFactory`                 | Enables Find Usages on the declaration and drops steps from scenarios that don't enable its library, for every consumer of the usages.  |
| `YaddaStepGotoDeclarationHandler`               | Narrows Cmd+B on a `.feature` step to the definitions its scenario enables. Handlers run before reference resolution.                   |
| `YaddaStepTarget`                               | A definition as a row in the Cmd+B chooser, labelled with its project-relative file path.                                               |
| `YaddaAmbiguousStepSuppressor`                  | Silences the Cucumber plugin's "Ambiguous step reference" warning when narrowing leaves one definition. Undefined steps still warn.     |
| `YaddaLibraryNameReferencesSearch`              | Reports the names in `@Libraries=…` as references to the registry property that defines them: usages, and rename.                       |
| `YaddaLibraryNameReference`                     | One such name. Rename rewrites every use of it in the file at once.                                                                     |
| `LibraryNames`                                  | Reads the names, and where they are, from the `@Libraries=` lines of a feature file.                                                    |
| `YaddaLibraryNameGotoDeclarationHandler`        | Cmd+B on a name in `@Libraries=…` goes to the registry property that defines it.                                                        |
| `YaddaFeatureRunConfigurationProducer`          | The run icon on a feature or scenario: a Playwright run configuration for the spec, narrowed with `--grep`.                             |
| `YaddaFeatureSpecs`                             | Finds the spec file that calls `defineFeatureSuite` for a feature file.                                                                 |
| `PlaywrightGrep`                                | Builds the `--grep` pattern from the generated `Feature:` and `Scenario:` titles, escaped and anchored.                                 |
| `YaddaLexer`                                    | Gherkin's lexer, plus Yadda's `---` blocks: a table's fences become comments, any other block becomes a doc string. Restartable.        |
| `YaddaParserDefinition`, `YaddaFile`            | The Yadda language: Gherkin's parser and PSI over `YaddaLexer`, with Gherkin as its base language so its features apply.                |
| `YaddaLanguageInjector`, `FencedBlock`          | Injects the language a block's remark names (`--- json`) into its content. `FencedBlock` reads the remark and content range of a block. |
| `YaddaEnterHandler`, `YaddaPystringManipulator` | Enter on an opening fence adds a closing fence, and a fragment edit keeps the fences. Both run before Gherkin's, which assume `"""`.    |
| `YaddaCsvBlockInspection`, `CsvRecords`         | The "Ragged CSV block" warning: rows whose field count differs from the header, and unclosed quotes.                                    |
| `YaddaGrammar`                                  | Yadda's feature parser, ported. Says where and why Yadda throws, which `Rule:`s there are, and each step's text as Yadda sees it.       |
| `YaddaParseInspection`, `YaddaRuleInspection`   | Yadda's own error on its line, and the warning that a `Rule:`'s scenarios are not run. Both read `YaddaFile.grammar`.                   |
| `YaddaUnusedColumnInspection`                   | An examples column nothing refers to as `[column]`. `YaddaInspectionSuppressor` silences Gherkin's checks that say otherwise.           |
| `YaddaCucumberExtension`                        | Offers the Cucumber plugin a second text for a step: the whole step, block included, with `[name]` filled in. Defines no steps itself.  |
| `YaddaTypedHandler`                             | Typing a `                                                                                                                              | ` in a table row pads it to line up under the pipe above. |
| `YaddaLanguageSubstitutor`                      | Parses a `.feature` file as Yadda when the project depends on `playwright-yadda` (`PlaywrightYaddaProjects`).                           |
| `YaddaStepDescriptionProvider`                  | Labels the declaration "step definition" in the Find window.                                                                            |
| `YaddaSteps`                                    | What counts as a step definition: a cheap syntax check, confirmed against the Cucumber.js plugin's own list.                            |
| `LibraryScope`                                  | The one question both directions ask: is this definition enabled for this step's scenario?                                              |
| `LibraryRegistries`                             | Reads `createLibraryRegistry(…)` calls and `defineFeatureSuite` overrides from source.                                                  |
| `FeatureTags`                                   | Reads `@…` lines of a feature file like Yadda, and mirrors `resolveLibraryNames` from `src/annotations.ts`.                             |
