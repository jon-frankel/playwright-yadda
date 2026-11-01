# A scenario's =false overrides the feature's tag: only the second scenario runs.
@Libraries=tags
@skip
Feature: Skipped feature with one scenario that runs

  Scenario: Stays skipped with its feature
    Given a step that fails

  @skip=false
  Scenario: Runs although its feature is skipped
    Given a step that passes
