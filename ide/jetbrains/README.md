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
  - typing a `|` in a table row pads the row so the pipe lines up under the one above, as it does in Gherkin (Gherkin's own handler only acts on files of its own type, and misses a table on the last line of a file). It does the same in a row without a leading pipe.

  Everything else in the plugin works on these files too.

- **Yadda's own way of writing a feature.** The parse tree is built by `YaddaParser`, a copy of Gherkin's with the places Yadda differs, so Gherkin's PSI (steps, tables, examples) is what the rest of the IDE reads:
  - **A line of text is a step.** `the customer checks out` needs no `Given`/`When`/`Then`. It is a step with no keyword, resolves to its definition (Cmd+B, Find Usages), and indents with the rest.
  - **Rows need no pipes.** Under `Examples:` or `Where:` (in any case) each line is a table row, whether it is `qty | total`, `| qty | total |`, or uses Yadda's `┆`. Reformat Code lines the columns up and keeps the rows under the heading, instead of moving them out to step level, and the cells are coloured as a table's. A row of one cell is a row too, and so is one that starts with a word like `Then`.
  - **A tag line inside the table annotates the row under it** and stays in the table. So do the dashed lines that group a row written over several lines; the line of such a row that starts with a pipe has an empty first cell, and lines up as one.
  - **The dashes around a table move with it.** Reformat Code puts a fenced table's opening and closing dashes at the same column as its rows (they used to stay where they were).
  - **Keywords are matched as Yadda matches them,** without regard to case, and `Where:` is `Examples:`. `scenario:` is a scenario.
  - **An outline's title needs a `[placeholder]`.** Yadda expands each `Examples:` row into a scenario with the outline's title, and Playwright rejects two tests with the same title, so write `Scenario Outline: A cart with [qty] keyboards`, not `Scenario Outline: A cart`. The IDE does not warn about it.
  - **A step with no keyword resolves in the IDE but will not match at run time** unless the library defines it with `define(…)`: Yadda's English library prefixes the definitions made with `given`, `when` and `then` with a keyword.

- **What Yadda would make of the file.** `YaddaGrammar` is Yadda's own feature parser (3.2) ported, and it is held to the real one by a corpus generated from it (`src/test/resources/yadda/generate-parser-cases.mjs`). From it:
  - **"Feature Yadda can't parse"** (error) puts Yadda's own message on the line that is wrong, before the tests load the file: `Scenario requires one or more steps` (on the empty scenario's heading), `Incorrect number of fields in example table`, `Invalid annotation: a @b`, `Indentation error`, `Feature is unexpected at this time`, and the rest. Yadda stops at its first error, and so does this.
  - **"Table rows without fences"** (warning) on a `| … |` row under a step with no `---` lines around its table. Gherkin attaches such rows to the step above, but Yadda reads each as a step of its own, which no definition matches, so the scenario fails as an undefined step.
  - **Headings only Gherkin has are text.** `Example:`, `Scenarios:` and `Business Need:` are not in Yadda's word list, so a line that starts with one is a step (or a row under `Examples:`), as Yadda reads it, and not a heading.
  - **"Rule whose scenarios are not run"** (warning) on a `Rule:`: Yadda parses rules, but playwright-yadda defines tests for the feature's own scenarios only.
  - **"Unused examples column"** (grey) replaces Gherkin's check, which looks for `<name>` and so reported every column. A column counts as used by `[name]`, `[ name ]`, `[name.index]`, `[name.start.line]` or `[name.start.column]` anywhere in the scenario, tables and blocks included.
  - Gherkin's "Scenario with Examples section", "Scenario outline without Examples" and "Unused table column" checks are silenced in Yadda files: `Scenario:` takes examples, and a `Scenario Outline:` without any is a scenario.
  - **Step definitions see what Yadda sees.** The Cucumber plugin matches a step by its first line with `<name>` filled in; Yadda matches all of it, fenced block included, with `[name]` filled in from the examples. A second text is offered for such steps, so `/^the rows are:\n([\s\S]+)$/` resolves for a step with a block and `/^the total is ([\d.]+)$/` for `the total is [total]`. Yadda matches an outline step for every row of its examples, so the step is defined when some row matches, Cmd+B offers the definitions of every row, and a warning, "Outline step with no definition for an examples row", names the rows that no definition matches while others do. Find Usages from a definition finds a `.feature` outline step through the row the Cucumber plugin was given (the first row that matches), so a definition that only a later row reaches may list no usage there; Markdown features find it for every row. Rename (see below) reads every row itself.

- **Features in another language.** Yadda's localisations (French, German, Spanish, Russian, Chinese and the rest, from `defineFeatureSuite({ language })`) change the words it reads as `Feature:`, `Scenario:`, `Examples:` and the like. Set the same language in **Settings | Tools | Playwright Yadda** (stored with the project, in `.idea/playwrightYadda.xml`), and the IDE reads headings, Yadda's own parse errors, `@Libraries` scopes and step keywords in it: `Fonctionnalité:`, `Scénario:`, `Exemples:` and `Soit`/`Quand`/`Alors`, a step is found by the definition written without its keyword, and "Create Yadda step definition" picks `given`, `when` or `then` from the keyword. It is a setting because the lexer must know the headings before any file is read, so it can't look for the option in your code, and the two have to agree. The word lists are `src/main/resources/yadda/vocabularies.json`, generated from the installed yadda by `scripts/generate-vocabularies.mjs`, and the parser port is held to the real one for French, Spanish, Russian, Chinese and German in a corpus.

- **Yadda's Markdown features.** A `.md` file is a feature to the IDE when it could be one to Yadda: it is in a project that depends on `playwright-yadda` and has a `# Feature:` heading, or it is named `*.yadda.md`. A README in the same project is not one (playwright-yadda would reject it, so keep it out of the features directory). The file stays the Markdown file it was, so Markdown's editing, preview and highlighting are untouched, and the plugin adds what a feature needs, read the way Yadda's `MarkdownFeatureParser` reads it (`YaddaGrammar.analyzeMarkdown`, held to the real parser by a second corpus):
  - **Cmd+B on a step** goes to the definition (the whole line is the step, bullet and all; the Markdown plugin binds the same key to Bold, so on a step, with nothing selected, the plugin sets Bold aside, and elsewhere in the file the key stays Bold); **Find Usages** on a definition lists the Markdown steps that run it; `@Libraries` narrows both, and Cmd+B, Find Usages and rename work on the names in `@Libraries=…` as they do in a `.feature` file. A step is matched with or without its `Given`/`When`/`Then` keyword, with its table or fenced block, and with `[name]` filled in from each examples row.
  - **Inspections,** the same ones as for a `.feature` file where they apply: "Markdown feature Yadda can't parse" (error, Yadda's message on its line), "Markdown feature rule whose scenarios are not run", "Unused examples column in a Markdown feature", and **"Undefined step in a Markdown feature"**, which a `.md` file has no other source for. Yadda reads any line of a scenario as a step, so a stray paragraph inside a scenario is reported too.
  - **The run icon** is next to the `# Feature:` and `## Scenario:` headings, and Run from the context menu inside a scenario runs it, as in a `.feature` file. A `Background:`, a `Rule:` and the scenarios under it have no run, because playwright-yadda defines no test for them.

- **Create Yadda step definition.** Alt+Enter on a step that has no definition (and "Create all Yadda step definitions" for every such step in the file) writes the definition the way this library's steps are written, `orderSteps.given(/^…$/, async function () {})`, at the end of the file that declares the `createStepLibrary()` variable the step's scenario runs. The scenario's own `@Libraries` come first, then the registry's defaults, and if several libraries qualify a popup asks which; if the registry can't be read it offers every `createStepLibrary()` in the project. The keyword picks `given`, `when` or `then` (`And` and `But` take the one before them, as Yadda's own prefix does). Text with regex characters becomes an escaped regex, a fenced block becomes a `block` parameter with `\n([\s\S]+)` in the pattern, and an outline's `[name]` becomes a `(.+)` group and a parameter called `name`. It works in `.feature` and Markdown features. It stands in for the Cucumber.js plugin's "Create step definition" there, which writes a `Given(…)` call with an import from `cucumber`, and throws on a step with no keyword.
- **Rename a step with its definition.** Shift+F6 on a step, in a `.feature` file or a Markdown feature, or on a step definition (the caret on its `given` or on its text, in a project that depends on `playwright-yadda`), asks for the definition's new pattern, shown without its `^` and `$` (`the total is ([\d.]+)`), and renames the definition and every step that runs it, in `.feature` and Markdown files, as one undoable command. Text outside the parentheses is what changes; the groups stay as written, and so does what they matched in each step: a number, a `[placeholder]`, a fenced block. A step is matched against the pattern as it is written and then as each examples row makes it, so an outline step is renamed too. `@Libraries` is honoured: only the steps of scenarios that enable the definition's library are renamed. Nothing is changed unless every step can be rewritten, and a definition that has regex syntax outside a group (`cukes?`, `\d+`, `a|b`, `$term`), or two definitions for one step, gets a message and no change. The groups of the pattern can't be changed in the dialog. It takes the place of the Cucumber plugin's rename for these steps, which refused a step with a block or a `[name]`, never rewrote a JavaScript definition, and threw on a step with no keyword.
- **Steps with no keyword** can't get a step definition created here. Yadda matches such a step only against a `define(…)`, which the IDE doesn't read as a step definition, so a created one would still show as undefined. They can be renamed, together with the definition they resolve to.

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

- **A Markdown feature is not a language of its own.** There is no Yadda-aware highlighting, completion or reformatting for it, only what is listed above; the Markdown plugin does the rest. A paragraph in a Markdown scenario that is meant as prose will be reported as an undefined step. For `.feature` files: a `|` row directly under a step with no dashes around it is shown as a table of that step, but Yadda reads each such line as a step of its own, so the "Table rows without fences" warning flags it (write the dashes); `###` comment blocks are not understood, so a line between two of them is read as a step or a row; and Gherkin's own `"""` strings are Gherkin's, not Yadda's.
- **One language per project.** The setting applies to every feature in the project. A project that runs features in two languages (two `defineFeatureSuite` calls with different `language`) gets only one right. The headings of the other language, and English ones in a French project, are read as text, so a feature file that mixes them shows what Yadda would make of it, an error about a missing scenario. Gherkin's lexer still colours the English `Feature:` and `Scenario:` of a file in another language.
- **Dutch can't be chosen,** because Yadda 3.2's Dutch scenario word (`(?:[Ss]cenario|[Gg|eval)`) is not a valid regular expression: the real parser throws when built for it. Yadda's German word lists give a scenario no title, so a German scenario outline fails inside Yadda; the IDE does not report that.
- **A quick fix inside an injected block** still goes through Gherkin's `updateText`, which assumes `"""` (untested), and `--- csv` is highlighted only if a CSV plugin (such as Rainbow CSV) is installed.
- **A step with no keyword gets no "Create Yadda step definition".** Yadda matches such a step only against `define(…)`, which the IDE doesn't index, so a definition created for it would still look undefined.
- **A definition with a block matches the block, not only the step.** `/^the rows are:\n([\s\S]+)$/` resolves for a step with a block under it. Because the first line alone is offered too, `/^the rows are:(?:\n([\s\S]+))?$/` and `/^the rows are:$/` also resolve for one (Yadda would not match the last).
- **Step definitions are what the Cucumber.js plugin indexes,** so a string pattern is a Cucumber expression there, where Yadda reads `$term` and its dictionary. Yadda's `define` and `$term` are not understood.
- **No warning yet for a step whose library isn't enabled,** and no completion or validation of the names in `@Libraries=`. Those would be separate additions.
- **Which registry a feature runs under isn't known.** A definition is shown if any registry containing its library enables it.
- **Only what the Cucumber.js plugin indexes.** A step definition is a call whose method name is a Gherkin keyword and whose first argument is a string or regex literal. Anything else, such as `lib.define(...)` or a step text held in a variable, is not recognised, in the same way the IDE doesn't recognise it going forward.
- **A `.md` file counts as a feature on a guess.** The directory playwright-yadda reads is computed at runtime, so the IDE can't tell, and goes by the dependency and a `Feature:` heading. A `Feature:` heading in a README of such a project makes it a feature to the IDE, and a feature without one (or in a project that doesn't list the package, unless it is named `*.yadda.md`) is not.
- **A Markdown feature needs the Markdown plugin,** which is bundled and on by default. The plugin names none of its classes, so with it off a `.md` file is plain text and nothing here applies.
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

| Class                                                      | Role                                                                                                                                                                                                                   |
| ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `YaddaStepPomDeclarationSearcher`                          | Makes a step-definition call a declaration, so Cmd+B shows usages instead of failing.                                                                                                                                  |
| `YaddaStepReferencesSearch`                                | Finds the Gherkin steps whose reference resolves to the definition, using the Cucumber plugin's own matching.                                                                                                          |
| `YaddaFindUsagesHandlerFactory`                            | Enables Find Usages on the declaration and drops steps from scenarios that don't enable its library, for every consumer of the usages.                                                                                 |
| `YaddaStepGotoDeclarationHandler`                          | Narrows Cmd+B on a `.feature` step to the definitions its scenario enables. Handlers run before reference resolution.                                                                                                  |
| `YaddaStepTarget`                                          | A definition as a row in the Cmd+B chooser, labelled with its project-relative file path.                                                                                                                              |
| `YaddaAmbiguousStepSuppressor`                             | Silences the Cucumber plugin's "Ambiguous step reference" warning when narrowing leaves one definition. Undefined steps still warn.                                                                                    |
| `YaddaLibraryNameReferencesSearch`                         | Reports the names in `@Libraries=…` as references to the registry property that defines them: usages, and rename.                                                                                                      |
| `YaddaLibraryNameReference`                                | One such name. Rename rewrites every use of it in the file at once.                                                                                                                                                    |
| `LibraryNames`                                             | Reads the names, and where they are, from the `@Libraries=` lines of a feature file.                                                                                                                                   |
| `YaddaLibraryNameGotoDeclarationHandler`                   | Cmd+B on a name in `@Libraries=…` goes to the registry property that defines it.                                                                                                                                       |
| `YaddaFeatureRunConfigurationProducer`                     | The run icon on a feature or scenario: a Playwright run configuration for the spec, narrowed with `--grep`.                                                                                                            |
| `YaddaFeatureSpecs`                                        | Finds the spec file that calls `defineFeatureSuite` for a feature file.                                                                                                                                                |
| `PlaywrightGrep`                                           | Builds the `--grep` pattern from the generated `Feature:` and `Scenario:` titles, escaped and anchored.                                                                                                                |
| `YaddaLexer`                                               | Gherkin's lexer, plus Yadda's `---` blocks (a table's fences become comments, any other block a doc string), the rows under `Examples:` cut into cells and pipes, and the keywords Gherkin reads as text. Restartable. |
| `YaddaParser`                                              | Gherkin's parser with steps that need no keyword, tables whose rows start with a cell, and tags between rows. It builds Gherkin's PSI.                                                                                 |
| `YaddaBlock`, `YaddaFormattingModelBuilder`                | Gherkin's formatting with the column of a cell found for rows that start with one, and the dashes of a table placed with its rows.                                                                                     |
| `Vocabulary`                                               | The words of one of Yadda's localisations for headings and step keywords, matched as Yadda matches them. Read from `vocabularies.json`.                                                                                |
| `YaddaSettings`, `YaddaSettingsConfigurable`               | The project's language (Settings                                                                                                                                                                                       | Tools | Playwright Yadda), and what is kept per file that depends on it. |
| `YaddaSyntaxHighlighter`                                   | Gherkin's colours over `YaddaLexer`'s tokens.                                                                                                                                                                          |
| `YaddaParserDefinition`, `YaddaFile`                       | The Yadda language: `YaddaParser` and Gherkin's PSI over `YaddaLexer`, with Gherkin as its base language so its features apply.                                                                                        |
| `YaddaLanguageInjector`, `FencedBlock`                     | Injects the language a block's remark names (`--- json`) into its content. `FencedBlock` reads the remark and content range of a block.                                                                                |
| `YaddaEnterHandler`, `YaddaPystringManipulator`            | Enter on an opening fence adds a closing fence, and a fragment edit keeps the fences. Both run before Gherkin's, which assume `"""`.                                                                                   |
| `YaddaCsvBlockInspection`, `CsvRecords`                    | The "Ragged CSV block" warning: rows whose field count differs from the header, and unclosed quotes.                                                                                                                   |
| `YaddaGrammar`                                             | Yadda's feature parser, ported. Says where and why Yadda throws, which `Rule:`s there are, and each step's text as Yadda sees it.                                                                                      |
| `YaddaParseInspection`, `YaddaRuleInspection`              | Yadda's own error on its line, and the warning that a `Rule:`'s scenarios are not run. Both read `YaddaFile.grammar`.                                                                                                  |
| `YaddaUnusedColumnInspection`                              | An examples column nothing refers to as `[column]`. `YaddaInspectionSuppressor` silences Gherkin's checks that say otherwise.                                                                                          |
| `YaddaCucumberExtension`                                   | Offers the Cucumber plugin a second text for a step: the whole step, block included, with `[name]` filled in. Defines no steps itself.                                                                                 |
| `YaddaTypedHandler`                                        | Typing a `\|` in a table row, with or without a leading pipe, pads it to line up under the pipe above.                                                                                                                 |
| `YaddaLanguageSubstitutor`                                 | Parses a `.feature` file as Yadda when the project depends on `playwright-yadda` (`PlaywrightYaddaProjects`).                                                                                                          |
| `YaddaCreateStepIntention`, `YaddaCreateAllStepsIntention` | "Create Yadda step definition": writes `library.given(/^…$/, …)` into the library the scenario runs (`YaddaStepCreator`, `StepSkeleton`).                                                                              |
| `YaddaCucumberFixFilter`                                   | Hides the Cucumber.js plugin's "Create step definition" fixes in Yadda files, which write a `Given(…)` call and fail without a keyword.                                                                                |
| `YaddaStepRenameHandler`, `YaddaStepRenameDialog`          | Shift+F6 on a step of a Yadda feature: asks for the definition's new pattern and renames it with the steps that run it (`StepRenamer`).                                                                                |
| `StepPattern`, `StepRenamer`                               | A definition's pattern split into the text a rename changes and the groups it keeps, and the plan that rewrites the definition and each step, or nothing.                                                              |
| `YaddaRenameHandlerRegistry`                               | Replaces the platform's rename handler registry so that the handler above is the only one offered for such a step, and the IDE doesn't ask which to use.                                                               |
| `YaddaStepDescriptionProvider`                             | Labels the declaration "step definition" in the Find window.                                                                                                                                                           |
| `YaddaSteps`                                               | What counts as a step definition: a cheap syntax check, confirmed against the Cucumber.js plugin's own list.                                                                                                           |
| `LibraryScope`                                             | The one question both directions ask: is this definition enabled for this step's scenario?                                                                                                                             |
| `LibraryRegistries`                                        | Reads `createLibraryRegistry(…)` calls and `defineFeatureSuite` overrides from source.                                                                                                                                 |
| `FeatureTags`                                              | Reads `@…` lines of a feature file like Yadda, and mirrors `resolveLibraryNames` from `src/annotations.ts`.                                                                                                            |
