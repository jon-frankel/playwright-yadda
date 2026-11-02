Feature: Payees on a phone

  Background:
    Given the demo budget is open on a phone
    When the user goes to the Settings page
    And the user opens the Payees settings

  Scenario: Unrelated payees are filtered out
    When the user searches for "asdfasdf-nonsense"
    Then "No payees found." is shown

  Scenario: Clearing the filter brings the other payees back
    Given the payees list has loaded
    When the user searches for "Fast Internet"
    Then only some of the payees are listed
    And "Starting Balance" is not shown
    When the user clears the search
    Then "Starting Balance" is shown

  Scenario: A payee opens its edit page
    When the user opens the first payee
    Then "Edit Payee" is shown
