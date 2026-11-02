Feature: Tags that change how a scenario runs
  Every scenario here ends green: a @fail scenario is expected to fail.

  Background:
    Given the demo budget is open
    And the Payees page is open

  # The demo budget has no payee called "Unicorn Ltd", so this assertion is false on purpose and
  # @fail expects it to fail. If Actual ever adds such a payee, the scenario starts failing: remove it then.
  @fail
  Scenario: A payee that does not exist is claimed to be listed
    When the user filters the list for "Unicorn Ltd"
    # The page loaded and the filter ran: only the claim below can be the failure.
    Then "No payees" is shown
    And "Unicorn Ltd" is shown

  # Half the default minute: this scenario takes about ten seconds, so a hang fails sooner.
  @timeout:30000
  Scenario: A payee is found within a shorter time limit
    When the user filters the list for "Deposit"
    Then "Deposit" is shown

  # Opening the demo budget is the flakiest part of a run on a loaded machine; one retry absorbs that.
  @retries:1
  Scenario: A payee is found, with a retry for a slow start
    When the user filters the list for "Fast Internet"
    Then "Fast Internet" is shown
