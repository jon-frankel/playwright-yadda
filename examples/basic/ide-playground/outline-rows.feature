# Expect: one warning, on "Then the order total is [total]": No step definition matches "the order total is many",
# which Yadda runs for example row 2. The definition wants a number (/^the order total is ([\d.]+)$/), and Yadda
# runs the outline once per row, so it fails the "many" row at run time. No "Undefined step reference" appears,
# because the other rows match. Change "many" to a number and the warning goes.

@Libraries=orders
Feature: Examples rows

  Scenario: A cart totals [total]
    Given the cart holds:
      ---
      | sku  | qty |
      | KB-1 | 1   |
      ---
    Then the order total is [total]

  Examples:
    | total  |
    | 80.00  |
    | many   |
    | 160.00 |
