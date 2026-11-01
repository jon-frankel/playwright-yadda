# @admin loads the `admin` library, and the file name @smoke.feature adds the tag @smoke:
# `playwright test --grep @smoke` runs it
Feature: Admin smoke test

  Scenario: Administrator deletes product
    Given the test session is active
    And an admin is logged in
    When they remove the item
    Then the product is deleted
