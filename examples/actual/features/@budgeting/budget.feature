Feature: Budget
  The budget page shows what is planned and spent for the month.

  Background:
    Given the demo budget is open

  Scenario: The summary shows each figure of the month
    Then the budget summary shows "Available funds"
    And the budget summary shows "Overspent in"
    And the budget summary shows "Budgeted"
    And the budget summary shows "For next month"

  Scenario: Funds are transferred to another category
    When the user transfers the whole balance of the second category to the third
    Then the third category's balance is what both categories held

  Scenario: The budget table has totals
    Then the budget table totals are numbers

  Scenario: Clicking a spent amount opens its transactions
    When the user clicks the spent amount of the second category
    Then the transactions of "All Accounts" are shown
    When the user goes back to the budget
    Then the budget summary shows "Budgeted"

  Scenario Outline: Right-clicking [place] opens a context menu
    When the user opens the context menu of [place]
    Then a context menu offers "[option]"

    Examples:
      | place                                     | option      |
      | the second category                       | Rename      |
      | the "Usual Expenses" category group       | Rename      |
      | the budget name                           | Switch file |

  Scenario: Scroll position is restored after viewing spent transactions
    Given the budget has 20 extra categories
    When the user scrolls to the bottom of the budget
    And the user clicks the last spent amount in view
    And the user goes back to the budget
    Then the budget is scrolled to where it was
