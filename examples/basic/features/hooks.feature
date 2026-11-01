# Tag-filtered hooks: steps/hooks.ts registers them, e2e/features.spec.ts passes them to the suite.
# The scenarios run in order (serial), because the last ones check what an earlier one left behind.
@Libraries=hooks
@worker-setup
@mode:serial
Feature: Hooks selected by tag

  @eur
  Scenario: A BeforeScenario hook sets up the scenarios with its tag
    Given the shop currency is EUR

  Scenario: and leaves the others alone
    Given the shop currency is not set

  @audit
  Scenario: AfterStep hooks see every step
    Given a step that passes
    And a step that passes
    Then the audit trail shows 2 steps before this one

  # Fails on purpose, which @fail expects: the scenario still ends green
  @cleanup
  @fail
  Scenario: AfterScenario runs although the scenario fails
    Given a step that fails on purpose

  Scenario: so the cleanup of the scenario before has happened
    Then the cleanup hook has run for "AfterScenario runs although the scenario fails"

  Scenario: A BeforeWorker hook ran once for the feature
    Then the worker hook has run once for this feature
