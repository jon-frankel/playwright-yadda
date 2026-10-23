# Expect: a warning on the "Rule:" line. Yadda accepts it; playwright-yadda never runs what is under it.

@Libraries=orders
Feature: Rules

  Scenario: Outside any rule
    When the customer checks out

  Rule: Large carts get a discount
    Scenario: Three keyboards
      When the customer checks out
