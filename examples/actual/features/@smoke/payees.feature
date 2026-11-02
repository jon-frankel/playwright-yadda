Feature: Payees
  Payees are listed in a filterable table.

  Background:
    Given the demo budget is open
    And the Payees page is open

  Scenario: [payee] is listed
    When the user filters the list for "[payee]"
    Then the payee list shows "[payee]"

    Examples:
      payee
      Fast Internet
      Starting Balance
      Deposit

  Scenario: A payee offers to create a rule
    When the user filters the list for "Deposit"
    Then "Create rule" is shown

  Scenario: Unrelated payees are filtered out
    When the user filters the list for "asdfasdf-nonsense"
    Then "No payees" is shown

  Scenario: A payee has a context menu
    When the user filters the list for "Fast Internet"
    And the user right-clicks the first row
    Then a context menu offers "Create rule"
