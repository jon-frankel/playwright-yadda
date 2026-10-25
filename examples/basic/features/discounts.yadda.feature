# A ".yadda.feature" file runs exactly like a ".feature" one: playwright-yadda picks files by their last
# extension. The IDE plugin reads the longer name as "this is Yadda's dialect", even in a project that does
# not depend on playwright-yadda. Here, where it does, a plain ".feature" would parse the same way.

@Libraries=orders
Feature: Volume discounts
  Three or more of a product earn 10% off that line.

  Background:
    Given the catalogue is imported from:
      --- csv
      sku,name,price
      KB-1,Ergonomic Keyboard,80.00
      MS-2,Vertical Mouse,45.50
      ---

  Scenario: [qty] mice cost [total]
    Given the cart holds:
      ---
      | sku  | qty   |
      | MS-2 | [qty] |
      ---
    When the customer checks out
    Then the order total is [total]

    Where:
      qty | total
      1   | 45.50
      2   | 91.00
      3   | 122.85
