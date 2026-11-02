# Run by e2e/override.spec.ts with libraryInheritance: "override": a scenario's @Libraries replaces
# the feature's instead of adding to them. The other features use the default, "merge".
@Libraries=rules
Feature: Library inheritance

  Scenario: A scenario without @Libraries uses its feature's libraries
    Given the demo budget is open
    And the Rules page is open
    When the user filters the list for "Fast Internet"
    Then "Fast Internet" is shown

  # `rules` is replaced by `transactions` here: no rule step is available to this scenario.
  @Libraries=transactions
  Scenario: A scenario's @Libraries replaces its feature's
    Given the demo budget is open
    When the user opens the "HSBC" account
    And the user adds a debit of "12.34" paid to "Fast Internet"
    Then the first transaction shows payee "Fast Internet" and debit "12.34"
