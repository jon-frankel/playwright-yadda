Feature: Accounts on a phone
  The accounts page lists every account with its balance, and each account can be searched.

  Background:
    Given the demo budget is open on a phone
    When the user goes to the Accounts page

  Scenario: The accounts page lists balances
    Then the second account is "Ally Savings" with a balance of 7,653.00

  Scenario: Searching an account's transactions
    When the user opens the first account
    Then the account shown is "Bank of America"
    And the account lists transactions
    When the user searches for "nothing should be found"
    Then "No transactions" is shown
    When the user clears the search
    And the user searches for "Kroger"
    Then the account lists transactions mentioning "Kroger"
