# Expect: every step is "Undefined step reference". On each, Alt+Enter, "Create Yadda step definition"
# (or "Create all Yadda step definitions" for the whole file), then check the stub it writes:
#
#   Scenario 1, steps/playground/fixtures.ts (a createBdd library, so fixtures mode):
#     playgroundFixtureSteps.given("an empty cart", async ({}) => { … })
#     playgroundFixtureSteps.when("they add 3 keyboards", async ({}) => { … })
#     playgroundFixtureSteps.then(/^the total is 240\.00 \(approx\)$/, async ({}) => { … })
#   Scenario 2, same file, the captures come after the empty pattern:
#     playgroundFixtureSteps.given(/^the shopper buys (.+) keyboards$/, async ({}, qty: string) => { … })
#     playgroundFixtureSteps.then(/^the order total for (.+) keyboards is (.+)$/, async ({}, qty: string, total: string) => { … })
#   Scenario 3, steps/playground/bound.ts (bound mode): no `{}`, the function is as before:
#     playgroundBoundSteps.given("the shopper buys keyboards", async function () { … })
#
# The `{}` is where you destructure the fixtures a step needs, e.g. async ({ page, stock }) => …
# The wording avoids steps that begin with "a ": the unit tests in src/ define `/^a (.*)$/`, which, when the whole repo is
# the project, makes such a step ambiguous instead of undefined and the fix is not offered.
# Delete what is written afterwards to run it again. Nothing here runs: the spec reads ../features only.

@Libraries=fixtures
Feature: Create step definitions

  Scenario: Steps with no captures
    Given an empty cart
    When they add 3 keyboards
    Then the total is 240.00 (approx)

  Scenario: Steps with captures [qty]
    Given the shopper buys [qty] keyboards
    Then the order total for [qty] keyboards is [total]

    Examples:
      | qty | total |
      | 1   | 80    |
      | 2   | 160   |

  @Libraries=bound
  Scenario: A bound library keeps the plain function
    Given the shopper buys keyboards
