package io.github.jonfrankel.playwrightyadda.run

/**
 * The `--grep` pattern that selects tests of a feature file.
 *
 * playwright-yadda names its tests `Feature: <title>` (a `describe`) and `Scenario: <title>`
 * (`src/feature.ts`), and Playwright matches the pattern against the whole title path, so a pattern
 * is just those titles joined by a space. They are matched as a regular expression, so titles are
 * escaped, and anchored where a prefix of another title would otherwise match as well.
 */
object PlaywrightGrep {
  /** Every scenario of the feature. The trailing `Scenario:` stops "Cart" from also matching "Cart Admin". */
  fun forFeature(featureTitle: String): String = "Feature: ${escape(featureTitle)} Scenario: "

  /** One scenario. Anchored at the end so "Clears cart" doesn't also run "Clears cart twice". */
  fun forScenario(featureTitle: String, scenarioTitle: String): String =
    "Feature: ${escape(featureTitle)} Scenario: ${escape(scenarioTitle)}$"

  /** A scenario outline: its examples may be named after the outline, so only the start is fixed. */
  fun forScenarioOutline(featureTitle: String, scenarioTitle: String): String =
    "Feature: ${escape(featureTitle)} Scenario: ${escape(scenarioTitle)}"

  private val SPECIAL = Regex("""[.*+?^${'$'}{}()|\[\]\\/]""")

  private fun escape(text: String): String = SPECIAL.replace(text.trim()) { "\\${it.value}" }
}
