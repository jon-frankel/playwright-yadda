# Expect: an error on the "@a @b" line: Yadda can't parse this: Invalid annotation: a @b
# (Yadda reads a tag per line. The IDE's Gherkin parser would have accepted it.)

@Libraries=orders
Feature: Tags

  @a @b
  Scenario: Two tags on one line
    When the customer checks out
