package io.github.jonfrankel.playwrightyadda.run

/**
 * The `--grep` pattern that selects tests of a feature file.
 *
 * playwright-yadda names its tests `Feature: <title>` (a `describe`) and `Scenario: <title>`
 * (`src/feature.ts`), and Playwright matches the pattern against the whole title path, so a pattern
 * is just those titles joined by a space. They are matched as a regular expression, so titles are
 * escaped, and anchored where a prefix of another title would otherwise match as well.
 *
 * `src/grep.ts` does the same in TypeScript. Both are checked against `src/grep.cases.json`
 * (see `PlaywrightGrepTest`), so a change to a pattern is made in both places.
 */
object PlaywrightGrep {
  /** Every scenario of the feature. The trailing `Scenario:` stops "Cart" from also matching "Cart Admin". */
  fun forFeature(featureTitle: String): String = "Feature: ${escape(featureTitle.trim())} Scenario: "

  /** Matches any scenario belonging to any of the given features. */
  fun forFeatures(featureTitles: Collection<String>): String {
    val clean = featureTitles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return when (clean.size) {
      0 -> ""
      1 -> forFeature(clean.first())
      else -> "(?:" + clean.joinToString("|") { forFeature(it) } + ")"
    }
  }

  /**
   * One scenario. Anchored at the end so "Clears cart" doesn't also run "Clears cart twice", but
   * Playwright (1.42 and later) appends a test's tags (`@smoke @timeout:5000`) to the title it
   * matches, and playwright-yadda passes every tag of the feature and the scenario, so any number
   * of those may follow the title.
   */
  fun forScenario(featureTitle: String, scenarioTitle: String): String =
    "Feature: ${escape(featureTitle.trim())} Scenario: ${escape(scenarioTitle.trim())}(?: @\\S+)*$"

  /**
   * A scenario outline. Its examples are named after it with each placeholder (`[n]`) replaced by the
   * example's value, so a placeholder matches anything and the rest of the title is literal.
   */
  fun forScenarioOutline(featureTitle: String, scenarioTitle: String): String {
    val title = PLACEHOLDER.split("Scenario: ${scenarioTitle.trim()}").joinToString(".*") { escape(it) }
    return "Feature: ${escape(featureTitle.trim())} $title(?: @\\S+)*$"
  }

  private val PLACEHOLDER = Regex("""\[[^\]]*]""")

  private val SPECIAL = Regex("""[.*+?^${'$'}{}()|\[\]\\/]""")

  private fun escape(text: String): String = SPECIAL.replace(text) { "\\${it.value}" }
}
