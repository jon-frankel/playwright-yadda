package io.github.jonfrankel.playwrightyadda.yadda

import com.google.gson.JsonParser
import junit.framework.TestCase

/** The Kotlin port of Yadda's parser against the real one: `parser-cases.json` is what Yadda said. */
class YaddaGrammarTest : TestCase() {
  private class Case(val name: String, val input: String, val ok: Boolean, val line: Int?, val message: String?, val steps: List<List<String>>, val rules: Int)

  private val cases: List<Case> by lazy {
    val json = javaClass.getResourceAsStream("/yadda/parser-cases.json")!!.reader().use { JsonParser.parseReader(it) }
    json.asJsonArray.map {
      val case = it.asJsonObject
      val ok = case["ok"].asBoolean
      Case(
        case["name"].asString,
        case["input"].asString,
        ok,
        case["line"]?.takeIf { line -> !line.isJsonNull }?.asInt,
        case["message"]?.asString,
        if (ok) case["steps"].asJsonArray.map { steps -> steps.asJsonArray.map { step -> step.asString } } else emptyList(),
        if (ok) case["rules"].asInt else 0,
      )
    }
  }

  fun `test the corpus is there`() {
    assertTrue(cases.size > 50)
    assertTrue(cases.any { it.ok } && cases.any { !it.ok })
  }

  fun `test every input fails or parses as it does in Yadda, with the same message and line`() {
    for (case in cases) {
      val result = YaddaGrammar.analyze(case.input)
      val issue = result.issue
      if (case.ok) {
        assertNull("${case.name}: Yadda parses it, the port says ${issue?.message}", issue)
        assertEquals("${case.name}: rules", case.rules, result.ruleLines.size)
      } else {
        assertNotNull("${case.name}: Yadda says '${case.message}', the port found nothing", issue)
        assertEquals("${case.name}: message", case.message, issue!!.message)
        assertEquals("${case.name}: the line Yadda reports", case.line, issue.reported)
      }
    }
  }

  fun `test the text of each step, fenced blocks included, is what Yadda makes of it`() {
    var compared = 0
    for (case in cases.filter { it.ok }) {
      val plain = case.steps.size == 1 && case.rules == 0 && !Regex("""(?i)background|examples|where""").containsMatchIn(case.input)
      if (!plain) continue
      assertEquals(case.name, case.steps.single(), YaddaGrammar.analyze(case.input).steps.values.map { it.text })
      compared++
    }
    assertTrue("compared only $compared", compared > 15)
  }

  private fun step(input: String, line: Int) = YaddaGrammar.analyze(input).steps.getValue(line)

  fun `test a step is expanded for the first row of its examples`() {
    val outline = "Feature: F\n  Scenario: S [n]\n    Given [n] things\n  Examples:\n    n | m\n    1 | 2\n    3 | 4\n"
    assertEquals("Given 1 things", step(outline, 3).expanded("Given [n] things"))
    assertEquals("Given 1 and 2", step(outline, 3).expanded("Given [ n ] and [m]"))
    assertEquals("Given 2", step(outline.replace("Given [n] things", "Given [m]"), 3).expanded("Given [m]"))
  }

  fun `test multi-line rows and meta placeholders expand as Yadda does`() {
    val multi = "Feature: F\n  Scenario: S [a]\n    Given [a]\n  Examples:\n    a | b\n    ---\n    1 | 2\n    3 | 4\n    ---\n    5 | 6\n"
    assertEquals("Given 1\n3", step(multi, 3).expanded("Given [a]"))
    val meta = "Feature: F\n  Scenario: S [n.index]\n    Given [n.start.line] [n]\n  Examples:\n    n\n    a\n    b\n"
    assertEquals("Given 6 a", step(meta, 3).expanded("Given [n.start.line] [n]"))
    assertEquals("Given 1", step(meta, 3).expanded("Given [n.index]"))
  }

  fun `test a step with a block has the block apart from its first line`() {
    val fenced = "Feature: F\n  Scenario: S\n    Given rows:\n    ---\n      | a |\n    | 1 |\n    ---\n    Then done\n"
    val result = YaddaGrammar.analyze(fenced)
    assertEquals("  | a |\n| 1 |", result.steps.getValue(3).block)
    assertNull(result.steps.getValue(8).block)
  }

  fun `test steps up to the error are still known, and an issue flags the construct that is incomplete`() {
    val result = YaddaGrammar.analyze("Feature: F\n  Scenario: A\n  Scenario: B\n    Given x\n")
    assertEquals(2, result.issue!!.line)
    assertEquals(3, result.issue!!.reported)
    val tagged = YaddaGrammar.analyze("Feature: F\n  Scenario: B\n    Given x\n  @a @b\n")
    assertEquals(4, tagged.issue!!.line)
    assertEquals(setOf(3), tagged.steps.keys)
  }

  fun `test rules are listed`() {
    val result = YaddaGrammar.analyze("Feature: F\n  Rule: R\n    Scenario: S\n      Given a\n  Rule: Q\n    Scenario: T\n      Given b\n")
    assertNull(result.issue)
    assertEquals(listOf(2, 5), result.ruleLines)
  }

  fun `test every feature the examples run parses (the playground ones are wrong on purpose)`() {
    val features = java.io.File("../../examples").walkTopDown()
      .filter { it.extension == "feature" && "node_modules" !in it.path && "ide-playground" !in it.path }
      .toList()
    assertTrue("found ${features.size}", features.size >= 5)
    for (feature in features) assertNull("${feature.path}: ${YaddaGrammar.analyze(feature.readText()).issue?.message}", YaddaGrammar.analyze(feature.readText()).issue)
  }
}
