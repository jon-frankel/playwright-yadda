<!--
Expect an error on the "| 1 | 2 |" row:
  Yadda can't parse this: Incorrect number of fields in example table. Expected 1 but found 2
(The outer pipes of a Markdown row are dropped before it is counted, unlike those of a .feature row.)
-->

@Libraries=orders

# Feature: Markdown error

## Scenario: A cart with [qty] keyboards

- When the customer checks out

### Examples:

| qty   |
| ----- |
| 1 | 2 |
