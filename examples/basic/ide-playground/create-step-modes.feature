# Expect: every step is "Undefined step reference". On each, Alt+Enter, "Create Yadda step definition",
# then check the stub it writes. The libraries are in steps/playground/.
#
#   Scenario 1, namespaced.ts (yadda.createStepLibrary(undefined, "fixtures")): fixtures mode.
#     playgroundNamespacedSteps.given("the shopper adds 2 mugs", async ({}) => { … })
#   Scenario 2, shared.ts (createStepLibrary from createBdd(test) made in shared-bdd.ts): fixtures mode.
#     playgroundSharedSteps.given("the shopper adds 3 plates", async ({}) => { … })
#   Scenario 3, param.ts (mode "param"): the plain function, no `{}`.
#     playgroundParamSteps.given("the shopper adds 4 bowls", async function () { … })
#
# Delete what is written afterwards to run it again. Nothing here runs: the spec reads ../features only.

@Libraries=namespaced
Feature: Create step definitions in each way a library can be made

  Scenario: A fixtures library made through a namespace import
    Given the shopper adds 2 mugs

  @Libraries=shared
  Scenario: A fixtures library from a createBdd in another file
    Given the shopper adds 3 plates

  @Libraries=param
  Scenario: A param library keeps the plain function
    Given the shopper adds 4 bowls
