# Expect: two warnings, on the two rows of the first scenario's table: "Yadda reads a row without --- lines around
# its table as a step of its own". Gherkin would make them that step's table, but Yadda makes each a step, so no
# definition matches it and the scenario fails as an undefined step. The second scenario puts the same rows
# between two lines of dashes and has no warning.

@Libraries=orders
Feature: Tables

  Scenario: Rows with no fences
    Given the cart holds:
    | sku  | qty |
    | KB-1 | 1   |
    Then the order total is 80.00

  Scenario: Rows between fences
    Given the cart holds:
      ---
      | sku  | qty |
      | KB-1 | 1   |
      ---
    Then the order total is 80.00
