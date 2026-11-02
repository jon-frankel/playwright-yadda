# Run by e2e/override.spec.ts with libraryInheritance: "override": a scenario's @Libraries replaces
# the feature's instead of adding to them. The other features use the default, "merge".
# inheritanceA and inheritanceB define the same steps and report which of them ran: with "merge" the
# second scenario would get both, so its step would be ambiguous or run A's.
@Libraries=inheritanceA
Feature: Library inheritance

  Scenario: A scenario without @Libraries uses its feature's libraries
    Given the library in use is reported
    Then the reported library is "A"

  @Libraries=inheritanceB
  Scenario: A scenario's @Libraries replaces its feature's
    Given the library in use is reported
    Then the reported library is "B"
