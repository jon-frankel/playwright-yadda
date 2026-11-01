# No @Libraries line: the @storefront directory loads the `storefront` library and tags the feature
Feature: Cart, tagged by its directory

  Scenario: Customer clears cart
    Given the test session is active
    And a customer is on the storefront
    When they remove the item
    Then the cart count is 0
