# Expect: every step resolves (Cmd+B goes to steps/orders.ts) except the two marked, which are
# undefined on purpose and say "Undefined step reference".
#
# The definitions require a block, so the step without one at the bottom is undefined, as it is in Yadda.

@Libraries=orders
Feature: Block steps

  Background:
    Given the catalogue is imported from:
      --- csv
      sku,name,price
      KB-1,Ergonomic Keyboard,80.00
      ---

  Scenario: Outline steps are read with the first row filled in
    Given the cart holds:
      ---
      | sku  | qty   |
      | KB-1 | [qty] |
      ---
    When the customer checks out
    Then the order total is [total]

  Examples:
    | qty | total |
    | 1   | 80.00 |

  Scenario: Undefined on purpose
    Given the cart holds:
    When nobody has written this step
