@Libraries=inventory
Feature: Inventory
  Steps in this feature take Playwright fixtures, including the custom "stock" fixture.
  The fixture starts every scenario with 3 "Mechanical Keyboard" in stock.

  Scenario: A purchase reduces stock
    Given the shop stocks 10 "Mechanical Keyboard"
    When a customer buys 4 "Mechanical Keyboard"
    Then "Mechanical Keyboard" has 6 left

  Scenario: Each scenario gets a fresh fixture
    Then "Mechanical Keyboard" has 3 left

  Scenario: Built-in fixtures are available
    Then the scenario runs in a browser project
