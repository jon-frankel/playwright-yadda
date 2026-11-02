package io.github.jonfrankel.playwrightyadda.run

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import junit.framework.TestCase
import java.io.File

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

  fun `test a scenario still matches itself when Playwright appends its tags`() {
    val grep = PlaywrightGrep.forScenario("Storefront Shopping", "Customer clears cart")
    assertTrue(matches(grep, "Feature: Storefront Shopping", "Scenario: Customer clears cart @libraries"))
    assertTrue(
      matches(grep, "Feature: Storefront Shopping", "Scenario: Customer clears cart @libraries @smoke @timeout:5000"),
    )
    assertFalse(
      matches(grep, "Feature: Storefront Shopping", "Scenario: Customer clears cart twice @smoke"),
    )
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

  fun `test a scenario outline matches each example, a placeholder standing for its value`() {
    val grep = PlaywrightGrep.forScenarioOutline("F", "Remove [item] from [place]")
    assertTrue(matches(grep, "Feature: F", "Scenario: Remove mug from cart"))
    assertTrue(matches(grep, "Feature: F", "Scenario: Remove mug from cart @smoke"))
    assertFalse(matches(grep, "Feature: F", "Scenario: Remove mug"))
  }

  /**
   * `src/grep.cases.json` is the contract with `src/grep.ts`, which has a test of its own against the
   * same file: each pattern is the one it builds, and selects the titles it should the way Playwright
   * applies `--grep` (a case-insensitive regular expression over the titles and tags joined by spaces).
   */
  private val cases: List<JsonObject> =
    JsonParser.parseString(File("../../src/grep.cases.json").readText())
      .asJsonObject.getAsJsonArray("cases").map { it.asJsonObject }

  private fun patternFor(case: JsonObject): String {
    val feature = case["feature"].asString
    return when (case["kind"].asString) {
      "feature" -> PlaywrightGrep.forFeature(feature)
      "outline" -> PlaywrightGrep.forScenarioOutline(feature, case["scenario"].asString)
      "scenario" -> PlaywrightGrep.forScenario(feature, case["scenario"].asString)
      else -> error("unknown kind in grep.cases.json: ${case["kind"]}")
    }
  }

  private fun matchesLikePlaywright(grep: String, titles: String): Boolean =
    Regex(grep, RegexOption.IGNORE_CASE).containsMatchIn("chromium features.spec.ts $titles")

  fun `test the cases are there`() {
    assertTrue("grep.cases.json has cases", cases.size >= 8)
  }

  fun `test every pattern is the one the TypeScript helper builds`() {
    for (case in cases) {
      assertEquals(case["name"].asString, case["grep"].asString, patternFor(case))
    }
  }

  fun `test every pattern selects what it should and nothing else`() {
    for (case in cases) {
      val grep = case["grep"].asString
      for (title in case.getAsJsonArray("selects")) {
        assertTrue("${case["name"].asString}: selects $title", matchesLikePlaywright(grep, title.asString))
      }
      for (title in case.getAsJsonArray("rejects")) {
        assertFalse("${case["name"].asString}: rejects $title", matchesLikePlaywright(grep, title.asString))
      }
    }
  }
}
