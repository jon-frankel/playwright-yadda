@Libraries=rules, transactions
Feature: Rules
  Rules change transactions as they are created.

  Scenario: A rule is applied to a new transaction
    Given the demo budget is open
    And the Rules page is open
    When the user filters the list for "Fast Internet"
    And the user starts a new rule
    And the rule applies when the payee is "Fast Internet"
    And the rule sets the category to "General"
    And the user saves the rule
    Then the first rule's conditions are "payee is Fast Internet"
    And the first rule's actions are "set category to General"
    When the user opens the "HSBC" account
    And the user adds a debit of "12.34" paid to "Fast Internet"
    Then the first transaction shows payee "Fast Internet", category "General" and debit "12.34"

  Scenario: A rule splits a new transaction
    Given the demo budget is open
    And the Rules page is open
    When the user starts a new rule
    And the rule applies when the payee is "Ikea"
    And the rule sets the notes to "food / entertainment"
    And the rule splits 90% of the remainder to "Entertainment"
    And the rule splits what is left equally to "Food"
    And the user saves the rule
    And the user opens the "Capital One Checking" account
    And the user adds a debit of "100.00" paid to "Ikea"
    Then the first transaction shows payee "Ikea", notes "food / entertainment", category "Split" and debit "100.00"
    And the second transaction shows payee "Ikea", category "Entertainment" and debit "90.00"
    And the third transaction shows payee "Ikea", category "Food" and debit "10.00"

  Scenario: A rule has a context menu
    Given the demo budget is open
    And the Rules page is open
    When the user filters the list for "Fast Internet"
    And the user right-clicks the first row
    Then a context menu offers "Edit"
