Feature: Rules
  Rules change transactions as they are created.

  Background:
    Given the demo budget is open
    And the Rules page is open

  Scenario: A rule is applied to a new transaction
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

  @slow
  Scenario: A rule splits a new transaction
    When the user starts a new rule
    And the rule applies when the payee is "Ikea"
    And the rule sets the notes to "food / entertainment"
    And the rule splits 90% of the remainder to "Entertainment"
    And the rule splits what is left equally to "Food"
    And the user saves the rule
    And the user opens the "Capital One Checking" account
    And the user adds a debit of "100.00" paid to "Ikea"
    Then the transaction list starts with:
      ---
      | payee | notes                | category      | debit  |
      | Ikea  | food / entertainment | Split         | 100.00 |
      | Ikea  |                      | Entertainment | 90.00  |
      | Ikea  |                      | Food          | 10.00  |
      ---

  Scenario: A rule has a context menu
    When the user filters the list for "Fast Internet"
    And the user right-clicks the first row
    Then a context menu offers "Edit"

  Scenario: A rule can be deleted
    When the user starts a new rule
    And the rule applies when the payee is "Ikea"
    And the rule sets the category to "General"
    And the user saves the rule
    And the user deletes the rule "payee is Ikea"
    And the user filters the list for "Ikea"
    Then "No rules" is shown
