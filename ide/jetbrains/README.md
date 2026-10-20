# Playwright Yadda for JetBrains IDEs

IDE support for [playwright-yadda](../../README.md) in WebStorm and other JetBrains IDEs with JavaScript support.

WebStorm's bundled Cucumber.js support already jumps from a step in a `.feature` file to its definition, even for Yadda's `library.given(...)` style, because it indexes any call named `given`, `when`, `then`, `and` or `but`. It has no way back. This plugin adds that:

- **Cmd+B on a step definition** (on the `given` or on the step text) shows the feature steps that run it, instead of "Cannot find declaration to go to".
- **Find Usages** (Alt+F7) on a step definition lists them in the Find window, labelled "step definition".

It works for string steps and regex steps, in `.ts` and `.js` files.

## Requirements

- WebStorm, or another JetBrains IDE with JavaScript support, **2026.2 or newer** (build 262+).
- The bundled **Cucumber.js** and **Gherkin** plugins enabled (they are by default).

## Install

```bash
pnpm ide:jetbrains:build
```

Then in the IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and pick `ide/jetbrains/build/distributions/playwright-yadda-jetbrains-<version>.zip`.

## Limitations

- **Usages are not scoped by `@Libraries`.** If two libraries define the same step text, as `examples/basic` does with "they remove the item", Find Usages on either definition lists the steps of both. This mirrors what the IDE already does going the other way: Cmd+B on that step in a `.feature` file offers both definitions. Neither direction knows which library a scenario enables.
- **Only what the Cucumber.js plugin indexes.** A step definition is a call whose method name is a Gherkin keyword and whose first argument is a string or regex literal. Anything else, such as `lib.define(...)` or a step text held in a variable, is not recognised, in the same way the IDE doesn't recognise it going forward.
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

The tests drive the same platform code paths the editor uses, including the exact decision Cmd+B makes (`GotoDeclarationOrUsageHandler2`), against a real WebStorm 2026.2 test sandbox. They are not a substitute for clicking through it once in the real IDE.

## How it works

The Java Cucumber plugin supports the same round trip, so this follows its design. It is also where the Cucumber.js plugin stops.

| Class                             | Role                                                                                                          |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| `YaddaStepPomDeclarationSearcher` | Makes a step-definition call a declaration, so Cmd+B shows usages instead of failing.                         |
| `YaddaStepReferencesSearch`       | Finds the Gherkin steps whose reference resolves to the definition, using the Cucumber plugin's own matching. |
| `YaddaFindUsagesHandlerFactory`   | Enables Find Usages on the declaration.                                                                       |
| `YaddaStepDescriptionProvider`    | Labels it "step definition" in the Find window.                                                               |
| `YaddaSteps`                      | What counts as a step definition: a cheap syntax check, confirmed against the Cucumber.js plugin's own list.  |
