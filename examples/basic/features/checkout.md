@Libraries=orders

# Feature: Markdown checkout

The same specification as `orders.feature`, written as GitHub-flavoured Markdown, so GitHub renders it as a
document. A heading is a keyword line, a list item is a step, a table or a fenced block under a step is the
step's data, and the paragraphs up here are the feature's description, which Yadda ignores.

> A quote like this one is a comment: GitHub shows it, and Yadda skips it.

<!-- So is this, which GitHub hides. -->

## Background:

- Given the catalogue is imported from:

  ```csv
  sku,name,price
  KB-1,Ergonomic Keyboard,80.00
  MS-2,Vertical Mouse,45.50
  LP-3,"Laptop stand, aluminium",35.00
  ```

## Scenario: A cart of lines in a table

- Given the cart holds:

  | sku  | qty |
  | ---- | --- |
  | KB-1 | 1   |
  | MS-2 | 2   |

- When the customer checks out
- Then the receipt reads:

  | sku  | qty | amount |
  | ---- | --- | ------ |
  | KB-1 | 1   | 80.00  |
  | MS-2 | 2   | 91.00  |

- And the order total is 171.00

## Scenario: An order placed as JSON

- When the customer places an order with:

  ```json
  {
    "customer": { "name": "Ada Lovelace", "email": "ada@example.com" },
    "lines": [
      { "sku": "LP-3", "qty": 4 },
      { "sku": "MS-2", "qty": 1 }
    ]
  }
  ```

- Then the order total is 171.50

## Scenario Outline: A cart with [qty] keyboards

- Given the cart holds:

  | sku  | qty   |
  | ---- | ----- |
  | KB-1 | [qty] |

- When the customer checks out
- Then the order total is [total]

### Examples:

| qty | total  |
| --- | ------ |
| 1   | 80.00  |
| 2   | 160.00 |
| 3   | 216.00 |
| 5   | 360.00 |
