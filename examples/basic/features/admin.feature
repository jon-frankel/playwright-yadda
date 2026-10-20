@Libraries=admin
Feature: Admin Inventory
  As an administrator
  I want to manage products

  Scenario: Administrator deletes product
    Given the test session is active
    And an admin is logged in
    When they remove the item
    Then the product is deleted
