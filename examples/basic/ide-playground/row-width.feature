# Expect: an error on the "| 1 |" row:
#   Yadda can't parse this: Incorrect number of fields in example table. Expected 4 but found 3
# (Yadda splits a row on every pipe, so the outer pipes count as two empty fields.)

@Libraries=orders
Feature: Examples

  Scenario: A cart with [qty] keyboards
    When the customer checks out

  Examples:
    | qty | total |
    | 1 |
