Feature: Tags that change how a scenario runs
  Every scenario here ends green: a @fail scenario is expected to fail.

  Background:
    Given the demo budget is open
    And the Payees page is open

  # A @fail scenario passes on any failure, so this one shows the page loaded and the filter ran
  # (an empty list), leaving only the false claim to fail in the @fail scenario below.
  Scenario: A payee that does not exist leaves the list empty
    When the user filters the list for "Unicorn Ltd"
    Then "No payees" is shown

  # The demo budget has no payee called "Unicorn Ltd", so the assertion below is false on purpose and
  # @fail expects it to fail. If Actual ever adds such a payee, the scenario starts failing: remove it then.
  @fail
  Scenario: A payee that does not exist is claimed to be listed
    When the user filters the list for "Unicorn Ltd"
    Then the payee list shows "Unicorn Ltd"

  # Half the default minute: this scenario takes about ten seconds, so a hang fails sooner.
  @timeout:30000
  Scenario: A payee is found within a shorter time limit
    When the user filters the list for "Deposit"
    Then the payee list shows "Deposit"

  # Opening the demo budget is the flakiest part of a run on a loaded machine; one retry absorbs that.
  @retries:1
  Scenario: A payee is found, with a retry for a slow start
    When the user filters the list for "Fast Internet"
    Then the payee list shows "Fast Internet"
