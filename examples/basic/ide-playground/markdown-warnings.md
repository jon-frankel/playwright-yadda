<!--
Expect three warnings, and nothing red:
  - "price" is greyed: "Nothing in the scenario uses this column". "qty" is used by [qty] in the title and a step.
  - "Then the moon is full" says "Undefined step".
  - "## Rule: Discounts" says "playwright-yadda doesn't run the scenarios under a Rule".
-->

@Libraries=orders

# Feature: Markdown warnings

## Scenario: A cart with [qty] keyboards

- Given the cart holds:

  | sku  | qty   |
  | ---- | ----- |
  | KB-1 | [qty] |

- When the customer checks out
- Then the moon is full

### Examples:

| qty | price |
| --- | ----- |
| 1   | 80.00 |

## Rule: Discounts

### Scenario: Three or more earn a discount

- Given the cart holds:

  | sku  | qty |
  | ---- | --- |
  | KB-1 | 3   |

- When the customer checks out
