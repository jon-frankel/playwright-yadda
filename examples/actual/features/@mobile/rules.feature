Feature: Rules on a phone

  Background:
    Given the demo budget is open on a phone
    When the user goes to the Settings page
    And the user opens the Rules settings

  Scenario: Unrelated rules are filtered out
    When the user searches for "NonExistentRule123456789"
    Then "No rules found" is shown

  Scenario: A rule opens its edit form
    When the user opens the first rule
    Then "Edit Rule" is shown
