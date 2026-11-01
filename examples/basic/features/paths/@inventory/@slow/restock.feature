# Two directories: @inventory loads the library, and @slow triples the timeout of every scenario
Feature: Restocking

  Scenario: A purchase reduces stock
    Given the shop stocks 10 "Mechanical Keyboard"
    When a customer buys 4 "Mechanical Keyboard"
    Then "Mechanical Keyboard" has 6 left
