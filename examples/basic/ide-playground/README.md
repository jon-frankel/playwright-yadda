# IDE playground

Feature files to open in WebStorm with the Playwright Yadda plugin (0.9.0 or later) installed.

**Nothing here runs.** The spec reads `../features` only, and several of these files are wrong on purpose.
Each file says at the top what to expect. A file that should be clean has no markers; a file with a mistake has
one mistake, since Yadda stops at its first error.

| File                     | What to check                                                                                                                                 |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `typing.feature`         | Typing a `\|` in a table row pads it so the pipe lands under the one above.                                                                   |
| `unused-column.feature`  | Only the `price` column is greyed ("Nothing in the scenario uses this column").                                                               |
| `rule.feature`           | Warning on the `Rule:` line: playwright-yadda doesn't run the scenarios under it.                                                             |
| `empty-scenario.feature` | Error on the empty scenario's heading, with Yadda's message.                                                                                  |
| `two-tags.feature`       | Error on `@a @b`: `Invalid annotation: a @b`.                                                                                                 |
| `row-width.feature`      | Error on the example row with too few fields.                                                                                                 |
| `block-steps.feature`    | The steps resolve (Cmd+B goes to `steps/orders.ts`) and two steps are undefined on purpose.                                                   |
| `yadda-style.feature`    | Steps with no keyword, `Where:` rows without pipes, an annotated row, a row over two lines. Nothing is marked; Reformat Code changes nothing. |

In `features/orders.feature`, which does run, the definitions in `steps/orders.ts` insist on their block and use
`([\d.]+)` for `[total]`. Before 0.8.0 those steps were all "Undefined step reference". They should be clean now.
