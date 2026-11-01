@Libraries=inventory
Feature: Inventory
  Steps in this feature take Playwright fixtures, including the custom "stock" fixture.

  Scenario: A purchase reduces stock
    Given the shop stocks 5 "Mechanical Keyboard"
    When a customer buys 2 "Mechanical Keyboard"
    Then "Mechanical Keyboard" has 3 left

  Scenario: Built-in fixtures are available
    Then the scenario runs in a browser project
