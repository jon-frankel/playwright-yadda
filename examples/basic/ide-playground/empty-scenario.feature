# Expect: an error on "Scenario: Nothing yet" (not on the line after it):
#   Yadda can't parse this: Scenario requires one or more steps

@Libraries=orders
Feature: Incomplete

  Scenario: Nothing yet

  Scenario: Something
    When the customer checks out
