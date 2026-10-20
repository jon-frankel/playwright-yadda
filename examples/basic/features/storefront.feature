@Libraries=storefront
Feature: Storefront Shopping
  As a customer
  I want to manage my shopping cart

  Scenario: Customer clears cart
    Given the test session is active
    And a customer is on the storefront
    When they remove the item
    Then the cart count is 0
