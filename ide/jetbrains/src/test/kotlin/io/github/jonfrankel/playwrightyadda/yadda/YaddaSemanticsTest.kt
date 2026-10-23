package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.cucumber.inspections.CucumberExamplesColonInspection
import org.jetbrains.plugins.cucumber.inspections.CucumberMissedExamplesInspection
import org.jetbrains.plugins.cucumber.inspections.CucumberStepInspection
import org.jetbrains.plugins.cucumber.inspections.CucumberTableInspection
import org.jetbrains.plugins.cucumber.inspections.GherkinScenarioToScenarioOutlineInspection

/** What the plugin says about a Yadda feature that Gherkin's own checks don't, or get wrong. */
class YaddaSemanticsTest : BasePlatformTestCase() {
  private fun all() = myFixture.enableInspections(
    YaddaParseInspection::class.java,
    YaddaRuleInspection::class.java,
    YaddaUnusedColumnInspection::class.java,
    CucumberStepInspection::class.java,
    CucumberTableInspection::class.java,
    CucumberMissedExamplesInspection::class.java,
    CucumberExamplesColonInspection::class.java,
    GherkinScenarioToScenarioOutlineInspection::class.java,
  )

  /** Each problem as `severity description :: text it covers`. The steps here have no definitions, which is not what these test. */
  private fun problems(name: String, text: String): List<String> {
    all()
    myFixture.configureByText(name, text.trimIndent())
    return myFixture.doHighlighting()
      .filter { it.description != null && it.severity.myVal >= 200 && "Undefined step" !in it.description }
      .map { describe(it) }
      .sorted()
  }

  private fun describe(info: HighlightInfo) =
    "${info.severity.name} ${info.description} :: ${myFixture.file.text.substring(info.startOffset, info.endOffset).replace("\n", "\\n")}"

  private fun yadda(text: String) = problems("a.yadda.feature", text)

  fun `test a feature Yadda accepts has no problems`() {
    assertEquals(emptyList<String>(), yadda("Feature: F\n  Scenario: S\n    Given a\n"))
  }

  fun `test what Yadda would throw about is an error on the line that is wrong`() {
    assertEquals(
      listOf("ERROR Yadda can't parse this: Invalid annotation: a @b :: @a @b"),
      yadda("Feature: F\n  @a @b\n  Scenario: S\n    Given a\n").filter { "Yadda" in it },
    )
  }

  fun `test an incomplete scenario is flagged at its heading, not at the line after it`() {
    assertEquals(
      listOf("ERROR Yadda can't parse this: Scenario requires one or more steps :: Scenario: A"),
      yadda("Feature: F\n  Scenario: A\n  Scenario: B\n    Given x\n").filter { "Yadda" in it },
    )
  }

  fun `test an examples row of the wrong width is the row`() {
    val problems = yadda("Feature: F\n  Scenario: S [n]\n    Given [n]\n  Examples:\n    n | m\n    1\n").filter { "Yadda" in it }
    assertEquals(listOf("ERROR Yadda can't parse this: Incorrect number of fields in example table. Expected 2 but found 1 :: 1"), problems)
  }

  fun `test an empty file is left alone`() {
    assertEquals(emptyList<String>(), yadda(""))
  }

  fun `test a rule is a warning that its scenarios are not run`() {
    assertEquals(
      listOf("WARNING playwright-yadda doesn't run the scenarios under a Rule :: Rule: R"),
      yadda("Feature: F\n  Rule: R\n    Scenario: S\n      Given a\n"),
    )
  }

  fun `test the same file in a plain Gherkin project is not checked`() {
    assertEquals(
      emptyList<String>(),
      problems("plain.feature", "Feature: F\n  @a @b\n  Scenario: S\n    Given a\n").filter { "Yadda" in it },
    )
  }

  private val outline = """
    Feature: F
      Scenario: S [qty]
        Given [qty] things
      Examples:
        | qty | price |
        | 1   | 80.00 |
  """

  fun `test a column used as a placeholder is used, and one nothing refers to is not`() {
    assertEquals(
      listOf("WARNING Nothing in the scenario uses this column :: price"),
      yadda(outline).filter { "column" in it },
    )
  }

  fun `test every spelling of a placeholder counts as a use`() {
    val text = "Feature: F\n  Scenario: S [ a ]\n    Given [b.index] and [c.start.line] and [d.start.column]\n  Examples:\n    | a | b | c | d |\n    | 1 | 2 | 3 | 4 |\n"
    assertEquals(emptyList<String>(), yadda(text).filter { "column" in it })
  }

  fun `test a placeholder in a table or block under the step is a use`() {
    val text = "Feature: F\n  Scenario: S\n    Given rows:\n    ---\n    | x | [a] |\n    ---\n  Examples:\n    | a |\n    | 1 |\n"
    assertEquals(emptyList<String>(), yadda(text).filter { "column" in it })
  }

  fun `test Gherkin's own checks of examples do not fire on a Yadda feature`() {
    val findings = yadda(outline)
    assertTrue(findings.toString(), findings.none { "Unused table column" in it || "Examples" in it && "Yadda" !in it })
    // A Scenario with examples is legal Yadda; a Scenario Outline without any is just a scenario.
    assertEquals(emptyList<String>(), yadda("Feature: F\n  Scenario Outline: S\n    Given a\n").filter { "Yadda" !in it })
  }

  fun `test the same checks still run on a plain Gherkin file`() {
    val findings = problems("plain.feature", outline.replace("[qty]", "<qty>"))
    assertTrue(findings.toString(), findings.any { "Unused table column" in it || "Scenario with Examples" in it || "Examples" in it })
  }
}

/** Step definitions written for what Yadda matches, which is more than the first line of the step. */
class YaddaStepTextTest : BasePlatformTestCase() {
  private fun steps() {
    myFixture.addFileToProject(
      "steps.ts",
      """
      export const s = createStepLibrary();
      s.then(/^the rows are:\n([\s\S]+)$/, function () {});
      s.then(/^the total is ([\d.]+)$/, function () {});
      s.then(/^the name is ([a-z]+)$/, function () {});
      s.then("the cart is empty", function () {});
      """.trimIndent(),
    )
  }

  private fun undefined(name: String, text: String): List<String> {
    steps()
    myFixture.enableInspections(CucumberStepInspection::class.java)
    myFixture.configureByText(name, text.trimIndent())
    return myFixture.doHighlighting().filter { it.description != null && it.severity.myVal >= 200 }
      .map { "${it.description} :: ${myFixture.file.text.substring(it.startOffset, it.endOffset)}" }
  }

  private val fencedTable = "Feature: F\n  Scenario: S\n    Then the rows are:\n    ---\n    | a | b |\n    ---\n    And the cart is empty\n"

  fun `test a definition that requires the block matches a step with one`() {
    assertEquals(emptyList<String>(), undefined("a.yadda.feature", fencedTable))
  }

  fun `test it matches a block that is not a table`() {
    assertEquals(emptyList<String>(), undefined("a.yadda.feature", "Feature: F\n  Scenario: S\n    Then the rows are:\n    --- json\n    {\"a\": 1}\n    ---\n"))
  }

  fun `test a step without its block is not that definition, as in Yadda`() {
    assertEquals(
      listOf("Undefined step reference: the rows are: :: the rows are:"),
      undefined("a.yadda.feature", "Feature: F\n  Scenario: S\n    Then the rows are:\n"),
    )
  }

  fun `test an outline step is read with its first row filled in`() {
    val outline = "Feature: F\n  Scenario: S [total]\n    Then the total is [total]\n  Examples:\n    | total |\n    | 80.00 |\n    | 160.00 |\n"
    assertEquals(emptyList<String>(), undefined("a.yadda.feature", outline))
  }

  fun `test a row that does not fit the definition leaves the step undefined`() {
    val outline = "Feature: F\n  Scenario: S [name]\n    Then the name is [name]\n  Examples:\n    | name |\n    | 42 |\n"
    assertEquals(listOf("Undefined step reference: the name is [name] :: the name is [name]"), undefined("a.yadda.feature", outline))
  }

  fun `test a step that is undefined stays undefined`() {
    assertEquals(
      listOf("Undefined step reference: nothing like this :: nothing like this"),
      undefined("a.yadda.feature", "Feature: F\n  Scenario: S\n    Then nothing like this\n"),
    )
  }

  fun `test a plain feature file is read as before`() {
    assertTrue(undefined("plain.feature", "Feature: F\n  Scenario: S [total]\n    Then the total is [total]\n  Examples:\n    | total |\n    | 80.00 |\n").isNotEmpty())
  }

  fun `test the step is found from the definition too`() {
    steps()
    myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "1" } }""")
    myFixture.addFileToProject("features/a.feature", fencedTable)
    myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("steps.ts")!!)
    myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("then(") + 1)
    assertEquals(listOf("a.feature"), myFixture.findUsages(myFixture.elementAtCaret).map { it.file!!.name })
  }
}
