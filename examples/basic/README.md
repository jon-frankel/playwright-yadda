# Basic example

Two small features that share step text across libraries, and a third that shows what Yadda adds to Gherkin: steps whose data is too big for one line. Everything here runs against a few lines of Node, with no browser page.

```bash
pnpm install
pnpm test
```

## What is in it

| Path                               | What it shows                                                                                                                                                                                                                               |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `features/storefront.feature`      | `@Libraries=storefront`: a scenario that enables one library.                                                                                                                                                                               |
| `features/admin.feature`           | `@Libraries=admin`: "they remove the item" means something else here. Two libraries define the same step text, and each scenario runs the one it enables.                                                                                   |
| `features/orders.feature`          | Multi-line steps. A block between two `---` lines belongs to the step above it: a CSV catalogue in the `Background:`, fenced tables, a JSON order (`--- json`), a plain-text block, and a scenario outline with a fenced table.             |
| `features/checkout.md`             | The same specification as `orders.feature`, written as GitHub-flavoured Markdown: headings for keywords, list items for steps, tables and code fences for the step's data. GitHub renders it as a document, and Yadda runs it.              |
| `features/discounts.yadda.feature` | A `.yadda.feature` file. Playwright runs it like any `.feature`. The extension makes the IDE plugin read it as Yadda even in a project that doesn't depend on `playwright-yadda`, which this one does, so a plain `.feature` already works. |
| `steps/`                           | One step library per feature, and `registry.ts`, which names them. `steps/support/blocks.ts` has the small parsers (`parseTable`, `parseCsv`, `requireBlock`) that turn a step's block into data.                                           |
| `e2e/features.spec.ts`             | The one spec: `defineFeatureSuite` with the registry and the `features/` directory.                                                                                                                                                         |
| `ide-playground/` (nothing runs)   | Files to open in WebStorm with the [Playwright Yadda plugin](../../ide/jetbrains/README.md): clean ones, and ones with one deliberate mistake each. See its [README](ide-playground/README.md). The spec does not read this folder.         |

## Things to know when writing blocks

- **Yadda hands a block to the definition as one string:** the step's first line, a newline, then the lines between the fences. A definition for such a step ends in `\n([\s\S]+)$` (as in `steps/orders.ts`), or `(?:\n([\s\S]+))?$` when the block is optional.
- **Yadda has no JSON or CSV converters.** The definition parses the block itself; `steps/support/blocks.ts` is one way to do it.
- **A scenario outline's title needs a `[placeholder]`** (`Scenario Outline: A cart with [qty] keyboards`). Yadda turns each `Examples:` row into a scenario with the outline's title, and Playwright rejects two tests with the same title.
- **`Examples:` rows need no pipes.** `qty | total` is a row, and so is `| qty | total |`.
