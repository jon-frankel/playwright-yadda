Feature: Settings on a phone

  Scenario: The budget can be exported
    Given the demo budget is open on a phone
    When the user goes to the Settings page
    And the user exports the data
    Then a zip named after today's date is downloaded
