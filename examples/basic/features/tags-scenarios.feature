# Special tags on scenarios. Every scenario here ends green: the ones tagged @fail are expected to
# fail, and the reporter counts that as a pass. Needs a playwright-yadda that has the special tags.
# Try:  pnpm test --grep @smoke    pnpm test --grep-invert @slow    pnpm test --grep @fail
@Libraries=tags
Feature: Special tags on scenarios

  @smoke
  Scenario: A plain tag is passed to Playwright as a tag
    Given a step that passes

  @fixme
  Scenario: Fixme skips the scenario and reports it as a fixme
    Given a step that fails

  @skip
  Scenario: Skip skips the scenario
    Given a step that fails

  @pending=not written yet
  Scenario: Pending is an alias of skip, and its value is a reason
    Given a step that fails

  @fail
  Scenario: Fail expects the scenario to fail
    Given a step that fails

  @slow
  Scenario: Slow triples the timeout
    Given a step that passes

  @timeout:5000
  Scenario: Timeout gives the scenario its own limit
    Given a step that takes 200 ms

  @retries:1
  Scenario: Retries runs a failed scenario again
    Given a step that passes on the second attempt

  @mode:serial
  @timeout:5000
  Scenario: Mode on a scenario is ignored with a warning, the rest still applies
    Given a step that passes
