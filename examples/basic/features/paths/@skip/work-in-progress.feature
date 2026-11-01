# Everything under @skip is skipped, unless a scenario says @skip=false.
# This file loads the library itself, as @skip is not a library.
@Libraries=tags
Feature: Work in progress

  Scenario: Not ready yet
    Given a step that fails

  @skip=false
  Scenario: Runs although its directory is @skip
    Given a step that passes
