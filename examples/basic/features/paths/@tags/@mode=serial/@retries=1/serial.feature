# Special tags as directories. `=` is used in place of `:` because Windows does not allow `:` in a
# file name. This is the same as writing `@mode:serial` and `@retries:1` above the feature, and
# @tags loads the `tags` library.
Feature: A serial feature, set by its path

  Scenario: Fails on its first attempt, then the whole feature runs again
    Given a step that passes on the second attempt

  Scenario: Runs after the first
    Given a step that passes
