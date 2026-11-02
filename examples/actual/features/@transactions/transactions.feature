Feature: Transactions
  An account lists its transactions, which can be added, filtered and laid out in columns.

  Background:
    Given the demo budget is open
    And the user opens the "Ally Savings" account

  Scenario: Transactions are filtered by category
    When the user filters the transactions where the category is "Clothing"
    Then the first 5 transactions all show category "Clothing"

  Scenario: Transactions are filtered by category group
    When the user opens the "Capital One Checking" account
    And the user filters the transactions where the category group is "Usual Expenses"
    Then the first 5 transactions all show a category among "Savings, Medical, Gift, General, Clothing, Entertainment, Restaurants, Food"

  Scenario: Transactions are filtered by payee
    When the user opens the "Capital One Checking" account
    And the user filters the transactions where the payee is "Kroger"
    Then the first 10 transactions all show payee "Kroger"

  Scenario: Transactions are filtered by a payee that [operator] "[text]"
    When the user opens the "Capital One Checking" account
    And the user filters the transactions where the payee [operator] "[text]"
    Then [expectation]

    Examples:
      operator | text | expectation
      contains | De | the first 9 transactions all show payee "Deposit"
      contains | l | the first 2 transactions all show a payee containing "l"
      does not contain | l | none of the first 5 transactions show a payee containing "l"

  Scenario: A transaction is created
    When the user adds a transaction:
      ---
      | payee      | notes       | category | debit |
      | Home Depot | Notes field | Food     | 12.34 |
      ---
    Then the first transaction shows payee "Home Depot", notes "Notes field", category "Food", debit "12.34" and credit ""

  Scenario: A split transaction is created
    When the user adds a split transaction:
      ---
      | payee   | notes | category | debit  |
      | Krogger | Notes |          | 333.33 |
      |         |       | General  | 222.22 |
      |         |       |          | 111.11 |
      ---
    Then the transaction list starts with:
      ---
      | payee   | notes | category   | debit  |
      | Krogger | Notes | Split      | 333.33 |
      | Krogger |       | General    | 222.22 |
      | Krogger |       | Categorize | 111.11 |
      ---

  Scenario: A transfer is created by paying another account
    When the user fills in a transaction without adding it:
      ---
      | payee           | notes       | debit |
      | Bank of America | Notes field | 12.34 |
      ---
    Then the filled-in transaction's category is "Transfer"
    Given the user notes the balances
    When the user adds the filled-in transaction
    Then the first transaction shows payee "Bank of America", notes "Notes field", category "Transfer", debit "12.34" and credit ""
    And the account balance has changed
    And the total and on-budget balances are unchanged

  Scenario: A long note is shown in full in a tooltip
    When the user adds a transaction:
      ---
      | payee      | notes                                                                                                                                                                      |
      | Home Depot | This is a deliberately long note about a grocery run that should overflow the notes column and get truncated with an ellipsis #groceries at the very end of the note text. |
      ---
    And the user adds a transaction:
      ---
      | payee  | notes      |
      | Kroger | short note |
      ---
    And the user hovers over the notes of the first transaction
    Then no tooltip appears
    When the user hovers over the notes of the second transaction
    Then a tooltip shows "deliberately long note about a grocery run" with the tag "#groceries"

  Scenario: Columns are hidden and reordered
    Then the transaction table has a "Notes" column
    When the user opens the table columns dialog
    And the user hides the "Notes" column
    And the user moves the "Category" column up 2 places
    And the user saves the table columns
    Then the transaction columns are "Date, Category, Payee, Payment, Deposit, Cleared"
    When the user opens the table columns dialog
    Then the "Notes" column is hidden in the table columns dialog
    When the user resets the columns to default
    And the user saves the table columns
    Then the transaction columns are "Date, Payee, Notes, Category, Payment, Deposit, Cleared"

  Scenario: Columns are applied to all transaction tables
    When the user opens the table columns dialog
    And the user hides the "Notes" column
    And the user applies the columns to all transaction tables
    And the user saves the table columns
    Then the transaction table has no "Notes" column
    When the user opens the "Bank of America" account
    Then the transaction table has no "Notes" column
