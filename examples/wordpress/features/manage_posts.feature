@Libraries=wp-common, wp-auth, wp-posts
Feature: WordPress Post Management in Browser WASM

  Scenario: Admin logs in and updates a blog post
    Given WordPress WASM is loaded in the browser
    When the user logs in as "admin" with password "password"
    Then the WordPress admin bar should be visible
    And the admin navigates to the posts list
    When they update the first post title to "Dynamic Yadda Rocks in WASM!"
    Then the posts list should display "Dynamic Yadda Rocks in WASM!"
