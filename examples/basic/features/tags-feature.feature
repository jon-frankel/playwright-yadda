# Special tags on a feature, and the scenarios that override them. A feature's @retries and
# @timeout cover every scenario; a scenario's own tag wins over the feature's.
#
# Expected output: the first scenario prints a red step, then passes on "retry #1" and counts as
# flaky. The second is not retried, so its red step is the failure @fail expects, and the scenario
# shows green.
@Libraries=tags
@retries:1
@timeout:4000
Feature: Special tags on a feature

  Scenario: Retried once, then passes (reported flaky)
    Given a step that passes on the second attempt

  @retries:0
  @fail
  Scenario: Not retried, and its one failure is expected (passes)
    Given a step that passes on the second attempt

  @timeout:200
  Scenario: Own timeout of 200 ms replaces the feature's 4000 ms (passes)
    Given a step that takes 100 ms
