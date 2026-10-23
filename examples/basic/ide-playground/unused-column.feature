# Expect: "price" is greyed. "qty" is used by [qty]; "total" by [ total ]; "name" by [name.index].
# Gherkin's own "Unused table column" / "Scenario with Examples" must not appear anywhere.

@Libraries=orders
Feature: Examples columns

  Scenario: A cart with [qty] keyboards
    Given the cart holds:
      ---
      | sku  | qty   |
      | KB-1 | [qty] |
      ---
    When the customer checks out
    Then the order total is [ total ]
    And the order is rejected with:
      ---
      row [name.index]
      ---

  Examples:
    | name | qty | price | total  |
    | a    | 1   | 80.00 | 80.00  |
    | b    | 2   | 80.00 | 160.00 |
