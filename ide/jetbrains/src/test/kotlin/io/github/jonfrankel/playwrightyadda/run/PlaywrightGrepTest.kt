package io.github.jonfrankel.playwrightyadda.run

import junit.framework.TestCase

class PlaywrightGrepTest : TestCase() {
  /** Playwright joins the describe and test titles with a space, and matches the pattern anywhere in it. */
  private fun matches(grep: String, vararg titles: String): Boolean =
    Regex(grep).containsMatchIn("[chromium] › features.spec.ts › " + titles.joinToString(" "))

  fun `test a scenario matches only itself`() {
    val grep = PlaywrightGrep.forScenario("Storefront Shopping", "Customer clears cart")
    assertTrue(matches(grep, "Feature: Storefront Shopping", "Scenario: Customer clears cart"))
    assertFalse(matches(grep, "Feature: Storefront Shopping", "Scenario: Customer clears cart twice"))
    assertFalse(matches(grep, "Feature: Admin", "Scenario: Customer clears cart"))
  }

  fun `test a feature matches all of its scenarios and no other feature`() {
    val grep = PlaywrightGrep.forFeature("Storefront")
    assertTrue(matches(grep, "Feature: Storefront", "Scenario: One"))
    assertTrue(matches(grep, "Feature: Storefront", "Scenario: Two"))
    assertFalse(matches(grep, "Feature: Storefront Admin", "Scenario: One"))
  }

  fun `test titles with regex characters are matched literally`() {
    val title = "Cart (empty) costs \$0.00 [a|b] a+b?"
    val grep = PlaywrightGrep.forScenario("F", title)
    assertTrue(matches(grep, "Feature: F", "Scenario: $title"))
    assertFalse(matches(grep, "Feature: F", "Scenario: Cart empty costs \$0x00 a|b aab"))
  }

  fun `test a scenario outline keeps its start open for the examples`() {
    val grep = PlaywrightGrep.forScenarioOutline("F", "Remove")
    assertTrue(matches(grep, "Feature: F", "Scenario: Remove <item>"))
  }
}
