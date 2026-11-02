@Libraries=transactions
Feature: Accounts
  Accounts are created, closed and worked in from the sidebar.

  Background:
    Given the demo budget is open

  Scenario: Creating the account [name] shows its starting balance
    When the user creates an on-budget account "[name]" with a balance of "[balance]"
    Then the first transaction shows payee "Starting Balance", notes "", category "Starting Balances", debit "" and credit "[shown]"

    Examples:
      name | balance | shown
      New Account | 100 | 100.00
      Large Balance Account | 12345678.9 | 12,345,678.90

  Scenario: An account register widens amount columns for large balances
    When the user creates an on-budget account "Large Balance Account" with a balance of "12345678.9"
    Then the first transaction's credit column is wider than 100 pixels

  Scenario: An account can be closed
    When the user opens the "Roth IRA" account
    Then the account is named "Roth IRA"
    When the user closes the account and moves its balance to "Vanguard 401k"
    Then the account is named "Closed: Roth IRA"

  Scenario: An account in the sidebar has a context menu
    When the user right-clicks the "Roth IRA" account in the sidebar
    Then a context menu offers "Rename"

  Scenario: A transaction has a context menu
    When the user creates an on-budget account "New Account" with a balance of "100"
    And the user right-clicks the first transaction
    Then a context menu offers "Delete"

  Scenario: The running balance follows an edited amount
    When the user creates an on-budget account "Running balance" with a balance of "0"
    And the user adds a transaction:
      ---
      | notes                | credit |
      | editable transaction | 10.00  |
      ---
    And the user opens the table columns dialog
    And the user shows the "Balance" column
    And the user saves the table columns
    Then the first transaction shows balance "10.00"
    When the user changes the first transaction's credit to "25.00"
    Then the first transaction shows balance "25.00"

  Scenario: Bulk editing the date shows a centred date picker
    When the user opens the "Ally Savings" account
    And the user selects the first transaction
    And the user chooses "Date" for the selection
    Then a date picker is centred in the dialog
    And every month of the date picker keeps its grid inside the dialog for the next 12 months
    And the date picker dialog is at most 700 pixels wide on a 2560 pixel wide screen

  @slow
  Scenario: Shift-click selection skips hidden reconciled transactions
    When the user creates an on-budget account "Range Select" with a balance of "0"
    And the user adds a transaction:
      ---
      | notes     | debit |
      | range-one | 10.00 |
      ---
    And the user adds a transaction:
      ---
      | notes     | debit |
      | range-two | 10.00 |
      ---
    And the user adds a transaction:
      ---
      | notes       | debit |
      | range-three | 10.00 |
      ---
    And the user marks the transaction with notes "range-two" as cleared
    And the user reconciles the account and locks the transactions
    And the user opens the table columns dialog
    And the user shows the "Balance" column
    And the user saves the table columns
    And the user hides the reconciled transactions
    Then the transaction with notes "range-two" is hidden
    When the user selects the transaction with notes "range-three"
    And the user shift-selects the transaction with notes "range-one"
    Then the selection is "2 transactions"

  # Under parallel load a row added a moment ago can swallow the selection click, as Actual's own test notes.
  # Still needed: 12 runs on 4 workers, with the tag's retries, ended 5 flaky and 2 failed.
  @retries:2
  Scenario: Two transactions are made into a transfer
    When the user opens the "On budget" account
    And the user filters the transactions to the note "Test Acc Transfer"
    And the user adds a transaction:
      ---
      | account      | notes             | category | debit |
      | Ally Savings | Test Acc Transfer | Food     | 34.56 |
      ---
    And the user adds a transaction:
      ---
      | account | notes             | category | credit |
      | HSBC    | Test Acc Transfer | Food     | 34.56  |
      ---
    And the user selects the first transaction
    And the user selects the second transaction
    And the user chooses "Make transfer" for the selection
    Then the transaction list starts with:
      ---
      | payee        | category | credit | account      |
      | Ally Savings | Transfer | 34.56  | HSBC         |
      | HSBC         | Transfer |        | Ally Savings |
      ---
    And the second transaction shows debit "34.56"

  Scenario: Transactions are imported from a CSV file
    When the user creates an on-budget account "CSV import" with a balance of "0"
    And the user opens a CSV file for import:
      ---
      Date,Payee,Notes,Category,Amount
      2024-08-02,Deposit,test 1,Income,1787.76
      2024-07-02,Deposit,test 2,Income,1787.76
      ---
    Then the import does not offer to import notes
    When the user imports the transactions
    Then the first transaction shows payee "Deposit"

  Scenario: Importing the same CSV file twice finds nothing new
    When the user creates an on-budget account "CSV import" with a balance of "0"
    And the user opens a CSV file for import:
      ---
      Date,Payee,Notes,Category,Amount
      2024-08-02,Deposit,test 1,Income,1787.76
      ---
    And the user imports the transactions
    And the user opens a CSV file for import:
      ---
      Date,Payee,Notes,Category,Amount
      2024-08-02,Deposit,test 1,Income,1787.76
      ---
    Then the import offers 0 transactions and cannot be started
    When the user closes the import
    Then the first transaction shows payee "Deposit"

  Scenario: QIF categories are kept when importing
    When the user creates an on-budget account "QIF import" with a balance of "0"
    And the user opens a QIF file for import:
      ---
      !Type:Bank
      D8/18/2026
      PQIF Cafe
      T-9.40
      LUsual Expenses:Food
      ^
      D8/19/2026
      PQIF Salary
      T50.00
      LIncome:Income
      ^
      ---
    Then the import preview lists the categories "Food" and "Income"
    When the user imports the transactions
    Then the transaction "QIF Cafe" is listed with category "Food"
    And the transaction "QIF Salary" is listed with category "Income"
