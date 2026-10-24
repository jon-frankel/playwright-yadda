<!--
Expect: nothing marked, and the steps resolve (Cmd+B goes to steps/orders.ts). The green run icon is next to
the Feature and Scenario headings.

Yadda reads this as a feature because it is a .md file in a project that depends on playwright-yadda and has a
"Feature:" heading. A README in the same folder is not one.
-->

@Libraries=orders

# Feature: Markdown style

Prose under the feature heading is its description, and Yadda ignores it.

> A quote is a comment that GitHub shows.

<!-- An HTML comment is one that GitHub hides. -->

## Background:

- Given the catalogue is imported from:

  ```csv
  sku,name,price
  KB-1,Ergonomic Keyboard,80.00
  ```

## Scenario: Any line is a step, and any bullet will do

Given the cart holds:

  | sku  | qty |
  | ---- | --- |
  | KB-1 | 1   |

- When the customer checks out
* Then the order total is 80.00
+ And the order total is 80.00

## Scenario: A heading that is no keyword is a step too

### Given the cart holds:

  | sku  | qty |
  | ---- | --- |
  | KB-1 | 2   |

- When the customer checks out
- Then the order total is 160.00

## Scenario: A cart with [qty] keyboards

- Given the cart holds:

  | sku  | qty   |
  | ---- | ----- |
  | KB-1 | [qty] |

- When the customer checks out
- Then the order total is [total]

### Where:

| qty | total  |
| --- | ------ |
| 1   | 80.00  |
| 3   | 216.00 |
