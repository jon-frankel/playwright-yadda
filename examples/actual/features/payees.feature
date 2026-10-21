Feature: Payees
  Payees are listed in a filterable table.

  Scenario: A payee is listed
    Given the demo budget is open
    And the Payees page is open
    When the user filters the list for "Fast Internet"
    Then "Fast Internet" is shown

  Scenario: A payee offers to create a rule
    Given the demo budget is open
    And the Payees page is open
    When the user filters the list for "Deposit"
    Then "Create rule" is shown

  Scenario: Unrelated payees are filtered out
    Given the demo budget is open
    And the Payees page is open
    When the user filters the list for "asdfasdf-nonsense"
    Then "No payees" is shown

  Scenario: A payee has a context menu
    Given the demo budget is open
    And the Payees page is open
    When the user filters the list for "Fast Internet"
    And the user right-clicks the first row
    Then a context menu offers "Create rule"
