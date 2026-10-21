package io.github.jonfrankel.playwrightyadda.libraries

import junit.framework.TestCase

class FeatureTagsTest : TestCase() {
  private fun String.librariesOn(needle: String): FeatureTags.Libraries? {
    val tags = FeatureTags.parse(this)
    return tags.librariesAt(tags.lineOf(indexOf(needle)))
  }

  fun `test a feature and a scenario each name libraries on one comma separated line`() {
    val text =
      """
      @Libraries=wp-common, wp-auth, wp-posts
      Feature: WordPress

        @Libraries=extra
        Scenario: One
          Given something
      """
        .trimIndent()

    val libraries = text.librariesOn("Given something")!!
    assertEquals(listOf("wp-common", "wp-auth", "wp-posts"), libraries.feature)
    assertEquals(listOf("extra"), libraries.scenario)
  }

  fun `test keys and names are case insensitive and trimmed`() {
    val text = "@LIBRARIES =  Admin ,Common \nFeature: F\n  Scenario: S\n    Given x"

    assertEquals(listOf("admin", "common"), text.librariesOn("Given x")!!.feature)
  }

  fun `test the last occurrence of a key wins`() {
    val text = "Feature: F\n  @Libraries=a\n  @libraries=b\n  Scenario: S\n    Given x"

    assertEquals(listOf("b"), text.librariesOn("Given x")!!.scenario)
  }

  fun `test a tag without a value names no libraries`() {
    val text = "@Libraries\nFeature: F\n  Scenario: S\n    Given x"

    assertEquals(emptyList<String>(), text.librariesOn("Given x")!!.feature)
  }

  fun `test tags belong to the scenario that follows them only`() {
    val text =
      """
      Feature: F
        @Libraries=a
        Scenario: First
          Given one

        Scenario: Second
          Given two
      """
        .trimIndent()

    assertEquals(listOf("a"), text.librariesOn("Given one")!!.scenario)
    assertEquals(emptyList<String>(), text.librariesOn("Given two")!!.scenario)
  }

  fun `test scenario outline steps belong to the outline and examples tags do not leak`() {
    val text =
      """
      Feature: F
        @Libraries=a
        Scenario Outline: Outline
          Given <thing>

          @Libraries=ignored
          Examples:
            | thing |
            | x     |

        Scenario: Next
          Given two
      """
        .trimIndent()

    assertEquals(listOf("a"), text.librariesOn("Given <thing>")!!.scenario)
    assertEquals(emptyList<String>(), text.librariesOn("Given two")!!.scenario)
  }

  fun `test background steps have no known scenario`() {
    val text = "Feature: F\n  Background:\n    Given shared\n  Scenario: S\n    Given x"

    assertNull(text.librariesOn("Given shared"))
    assertNotNull(text.librariesOn("Given x"))
  }

  fun `test scenarios inside a rule are not known because playwright-yadda does not run them`() {
    val text = "Feature: F\n  Rule: R\n    Scenario: S\n      Given x"

    assertNull(text.librariesOn("Given x"))
  }

  fun `test enabled libraries merge the defaults, the feature and the scenario`() {
    assertEquals(
      setOf("common", "a", "b"),
      enabledLibraries(listOf("common"), "merge", listOf("a"), listOf("b")),
    )
  }

  fun `test override replaces the feature libraries only when the scenario names some`() {
    assertEquals(
      setOf("common", "b"),
      enabledLibraries(listOf("common"), "override", listOf("a"), listOf("b")),
    )
    assertEquals(
      setOf("common", "a"),
      enabledLibraries(listOf("common"), "override", listOf("a"), emptyList()),
    )
  }

  fun `test default libraries are normalised`() {
    assertEquals(setOf("wp-common"), enabledLibraries(listOf(" WP-Common ", ""), "merge", emptyList(), emptyList()))
  }
}
