# @mode:serial on a feature: the scenarios run in order in one worker, and @retries on the feature
# retries all of them from the first. A @retries on a scenario here is ignored with a warning.
@Libraries=tags
@mode:serial
@retries:1
Feature: A serial feature

  Scenario: Fails on its first attempt, then the whole feature runs again
    Given a step that passes on the second attempt

  Scenario: Runs after the first
    Given a step that passes
