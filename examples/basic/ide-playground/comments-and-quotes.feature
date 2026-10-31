# Expect: one problem, "Undefined step reference" on the line of triple quotes in the second scenario.
# In the first scenario the lines between the two lines of hashes are one comment: nothing in it is a step, a
# heading or a fence, so it has no problem though its step is undefined. In the second, triple quotes are not a
# doc string to Yadda but a line of text, which is a step, and nobody has written it.
# Reformat Code moves the JSON block with its step: put the step or the block in another column and try it.

@Libraries=orders
Feature: Comments and quotes

  Scenario: A comment hides what is in it
    Given the cart holds:
      ---
      | sku  | qty |
      | KB-1 | 1   |
      ---
    ###
    Then nobody has written this step either
    Scenario: Nor is this a scenario
    ---
    ###
    Then the order total is 80.00

  Scenario: Triple quotes are a step
    Given the cart holds:
      ---
      | sku  | qty |
      | KB-1 | 1   |
      ---
    """
    Then the order total is 80.00
