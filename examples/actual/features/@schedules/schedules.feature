Feature: Schedules
  A schedule posts a transaction on its dates, and a rule links the two.

  Background:
    Given the demo budget is open
    And the Schedules page is open

  # The rules and transactions libraries are only needed here, so this scenario adds them to the
  # schedules library the path tag already selects.
  @Libraries=rules, transactions
  Scenario: A schedule is created, posted and later completed
    When the user schedules 25 for "Home Depot" from the "HSBC" account
    Then the first "Home Depot" schedule shows account "HSBC", amount "~25.00" and status "Due"
    When the user posts the first "Home Depot" schedule
    Then the first "Home Depot" schedule shows status "Paid"
    When the user opens the "HSBC" account
    Then the first transaction shows payee "Home Depot", category "Categorize", debit "25.00" and credit ""
    Given the Rules page is open
    When the user filters the list for "Home Depot"
    Then the first rule's actions are "link schedule Home Depot (2017-01-01)"
    And the first rule's conditions are:
      ---
      | condition                                 |
      | payee is Home Depot                       |
      | and account is HSBC                       |
      | and date is approx Every month on the 1st |
      | and amount is approx -25.00               |
      ---
    Given the Schedules page is open
    When the user completes the first "Home Depot" schedule
    Then "Show completed schedules" is shown

  @Libraries=transactions
  Scenario: Two schedules for the same payee are posted one at a time
    When the user schedules 5 for "Apple" from the "HSBC" account
    And the user schedules 5 for "Apple" from the "HSBC" account
    Then the first "Apple" schedule shows account "HSBC", amount "~5.00" and status "Due"
    And the second "Apple" schedule shows account "HSBC", amount "~5.00" and status "Due"
    When the user posts the first "Apple" schedule
    Then the first "Apple" schedule shows status "Paid"
    And the second "Apple" schedule shows status "Due"
    When the user posts the second "Apple" schedule
    Then the second "Apple" schedule shows status "Paid"
    And the first "Apple" schedule shows status "Paid"
    When the user opens the "HSBC" account
    Then the first transaction shows payee "Apple", category "Categorize", debit "5.00" and credit ""
    And the second transaction shows payee "Apple", category "Categorize", debit "5.00" and credit ""

  Scenario: A scheduled split transfer is shown in its destination account
    Given the "Phone bills" schedule sends 70 of each payment on to the "Ally Savings" account
    When the user opens the "Ally Savings" account
    Then a scheduled transfer from "Bank of America" of 50.00 is listed as due

  Scenario: A schedule has a context menu
    When the user schedules 25 for "Home Depot" from the "HSBC" account
    And the user right-clicks the "Home Depot" schedule
    Then a context menu offers "Complete"
