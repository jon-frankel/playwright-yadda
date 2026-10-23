package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.cucumber.inspections.CucumberStepInspection
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinScenario
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTableRow

/** Yadda's own way of writing examples and steps, read the way Yadda reads it. */
class YaddaExamplesTest : BasePlatformTestCase() {
  private fun yadda(text: String): PsiFile = myFixture.configureByText("a.yadda.feature", text.trimIndent())

  private fun reformatted(text: String): String {
    val file = yadda(text)
    WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(file) }
    return file.text
  }

  private fun tables(file: PsiFile) = PsiTreeUtil.findChildrenOfType(file, GherkinTable::class.java).toList()

  private fun cells(row: GherkinTableRow) = row.psiCells.map { it.text }

  private fun errors(): List<String> =
    myFixture.doHighlighting().filter { it.description != null && it.severity.myVal >= 400 }
      .map { "${it.description} :: '${myFixture.file.text.substring(it.startOffset, it.endOffset)}'" }

  private val pipeless = """
    Feature: F
      Scenario: A cart with [qty] keyboards
        Given the cart is empty

        Examples:
          qty | total
          1   | 80.00
          2   | 160.00
  """

  fun `test rows without pipes are the rows of a table`() {
    val file = yadda(pipeless)
    val table = tables(file).single()
    assertEquals(listOf("qty", "total"), cells(table.headerRow!!))
    assertEquals(listOf(listOf("1", "80.00"), listOf("2", "160.00")), table.dataRows.map { cells(it) })
    assertSame(table, PsiTreeUtil.findChildOfType(file, GherkinExamplesBlock::class.java)!!.table)
    assertEquals(emptyList<String>(), errors())
  }

  fun `test Where is Examples, in any case`() {
    for (keyword in listOf("Where", "where", "WHERE", "Examples", "examples")) {
      val file = yadda(pipeless.replace("Examples:", "$keyword:"))
      assertEquals(keyword, listOf("qty", "total"), cells(tables(file).single().headerRow!!))
      assertEquals(keyword, 1, PsiTreeUtil.findChildrenOfType(file, GherkinExamplesBlock::class.java).size)
    }
  }

  fun `test rows with outer pipes and rows without are both rows`() {
    val file = yadda(
      """
      Feature: F
        Scenario: S [a]
          Given x
          Where:
            | a | b |
            1 | 2
            3 | 4 |
      """,
    )
    assertEquals(listOf(listOf("a", "b"), listOf("1", "2"), listOf("3", "4")), tables(file).single().let { listOf(it.headerRow!!) + it.dataRows }.map { cells(it) })
  }

  fun `test a box-drawing separator is a separator`() {
    val file = yadda(pipeless.replace("qty | total", "qty ┆ total").replace("1   | 80.00", "1   ┆ 8|0.00").replace("2   | 160.00", "2   ┆ 160.00"))
    assertEquals(listOf(listOf("1", "8|0.00"), listOf("2", "160.00")), tables(file).single().dataRows.map { cells(it) })
  }

  fun `test a one-column table and an empty cell`() {
    val file = yadda(
      """
      Feature: F
        Scenario: S [a]
          Given x
          Examples:
            a | b | c
            1 |   | 3
            4
      """,
    )
    val rows = tables(file).single().dataRows
    assertEquals(listOf("1", "", "3"), cells(rows[0]))
    assertEquals(listOf("4"), cells(rows[1]))
  }

  fun `test a row that starts with a word Gherkin reads as a step keyword is a row`() {
    val file = yadda(
      """
      Feature: F
        Scenario: S [when]
          Given x
          Examples:
            when | who
            Then | Ann
            And  | Bo
      """,
    )
    assertEquals(listOf(listOf("Then", "Ann"), listOf("And", "Bo")), tables(file).single().dataRows.map { cells(it) })
    assertEquals(1, PsiTreeUtil.findChildrenOfType(file, GherkinStep::class.java).size)
  }

  fun `test the fences that group a row over several lines are comments, and the lines are rows`() {
    val file = yadda(
      """
      Feature: F
        Scenario: S [a]
          Given x
          Where:
            a | b
            ---
            1 | one
            2 | two
            ---
            3 | three
            ---
      """,
    )
    val table = tables(file).single()
    assertEquals(listOf(listOf("1", "one"), listOf("2", "two"), listOf("3", "three")), table.dataRows.map { cells(it) })
    assertEquals(emptyList<String>(), errors())
  }

  fun `test a tag line between rows annotates the row under it and stays in the table`() {
    val file = yadda(
      """
      Feature: F
        Scenario: S [a]
          Given x
          Examples:
            a | b
            @skip
            1 | 2
            3 | 4

        Scenario: Next
          Given y
      """,
    )
    val table = tables(file).single()
    assertEquals(2, table.dataRows.size)
    assertEquals(listOf("@skip"), PsiTreeUtil.findChildrenOfType(table, org.jetbrains.plugins.cucumber.psi.GherkinTag::class.java).map { it.text })
    assertEquals(listOf("S [a]", "Next"), PsiTreeUtil.findChildrenOfType(file, GherkinScenario::class.java).map { it.scenarioName })
  }

  fun `test tags before the next scenario end the table`() {
    val file = yadda(
      """
      Feature: F
        Scenario: S [a]
          Given x
          Examples:
            a | b
            1 | 2

        @slow
        Scenario: Next
          Given y
          1 | not a row
      """,
    )
    assertEquals(1, tables(file).single().dataRows.size)
    val next = PsiTreeUtil.findChildrenOfType(file, GherkinScenario::class.java).last()
    assertEquals(listOf("y", "1 | not a row"), PsiTreeUtil.findChildrenOfType(next, GherkinStep::class.java).map { it.name })
  }

  fun `test a line of text in a scenario is a step with no keyword`() {
    val file = yadda(
      """
      Feature: F
        Scenario: S
          the cart is empty
          Given the customer logs in
          then they see the shop
      """,
    )
    val steps = PsiTreeUtil.findChildrenOfType(file, GherkinStep::class.java).toList()
    assertEquals(listOf("the cart is empty", "the customer logs in", "then they see the shop"), steps.map { it.name })
    assertNull(steps[0].keyword)
    assertEquals("Given", steps[1].keyword?.text)
    assertNull("lower-case then is not Gherkin's keyword, so it's text", steps[2].keyword)
    assertEquals(emptyList<String>(), errors())
  }

  fun `test a step with no keyword takes the table or text block under it`() {
    val file = yadda(
      """
      Feature: F
        Scenario: S
          the cart holds:
            ---
            | sku | qty |
            | KB  | 1   |
            ---
          the note reads:
            ---
            plain text
            ---
          And the last one
      """,
    )
    val steps = PsiTreeUtil.findChildrenOfType(file, GherkinStep::class.java).toList()
    assertEquals(listOf("the cart holds:", "the note reads:", "the last one"), steps.map { it.name })
    assertNotNull(steps[0].table)
    assertNotNull(steps[1].pystring)
    assertEquals(emptyList<String>(), errors())
  }

  fun `test the title is not a step, and Scenario Outline and a lowercase scenario keep their kinds`() {
    val file = yadda(
      """
      Feature: F
        scenario: lower case
          Given x
        Scenario Outline: An outline [a]
          Given y
          Examples:
            a
            1
      """,
    )
    val scenarios = PsiTreeUtil.findChildrenOfType(file, GherkinStepsHolder::class.java).toList()
    assertEquals(listOf("lower case", "An outline [a]"), scenarios.map { it.scenarioName })
    assertTrue(scenarios[1] is GherkinScenarioOutline)
    assertEquals(listOf("x"), PsiTreeUtil.findChildrenOfType(scenarios[0], GherkinStep::class.java).map { it.name })
    assertEquals(listOf("y"), PsiTreeUtil.findChildrenOfType(scenarios[1], GherkinStep::class.java).map { it.name })
  }

  fun `test a step with no keyword resolves to its definition`() {
    myFixture.addFileToProject("steps.ts", "export const s = createStepLibrary();\ns.given(\"the cart is empty\", function () {});")
    yadda("Feature: F\n  Scenario: S\n    the cart is empty\n    the cart is full")
    myFixture.enableInspections(CucumberStepInspection::class.java)
    val undefined = myFixture.doHighlighting().filter { it.description?.startsWith("Undefined step") == true }
      .map { myFixture.file.text.substring(it.startOffset, it.endOffset) }
    assertEquals(listOf("the cart is full"), undefined)
  }

  fun `test reformatting aligns rows without pipes and keeps them under Examples`() {
    assertEquals(
      """
      Feature: F

        Scenario: A cart with [qty] keyboards
          Given the cart is empty

          Examples:
            qty | total
            1   | 80.00
            2   | 160.00
      """.trimIndent(),
      reformatted(
        """
        Feature: F
          Scenario: A cart with [qty] keyboards
            Given the cart is empty

            Examples:
            qty|total
              1 | 80.00
                 2| 160.00
        """,
      ),
    )
  }

  fun `test reformatting leaves rows that are already tidy alone, with pipes or without`() {
    val tidy = pipeless.replace("Feature: F\n", "Feature: F\n\n")
    assertEquals(tidy.trimIndent(), reformatted(tidy))
    val piped = tidy.replace("qty | total", "| qty | total  |").replace("1   | 80.00", "| 1   | 80.00  |").replace("2   | 160.00", "| 2   | 160.00 |")
    assertEquals(piped.trimIndent(), reformatted(piped))
  }

  fun `test reformatting a row group with fences and an annotation`() {
    val text = """
      Feature: F
        Scenario: S [a]
          Given x
          Where:
            a | bb
            @skip
            1 | one
            ---
            22 | two
            ---
    """
    assertEquals(
      """
      Feature: F

        Scenario: S [a]
          Given x
          Where:
            a  | bb
            @skip
            1  | one
            ---
            22 | two
            ---
      """.trimIndent(),
      reformatted(text),
    )
  }

  fun `test the second line of a row written over two lines starts with an empty cell`() {
    val file = yadda(
      """
      Feature: F
        Scenario: S [sku]
          Given x
          Where:
            sku  | note
            ---
            KB-1 | first
                 | second
            ---
      """,
    )
    val rows = tables(file).single().dataRows
    assertEquals(listOf(listOf("KB-1", "first"), listOf("", "second")), rows.map { cells(it) })
  }

  fun `test reformatting a row written over several lines aligns its lines with the rest`() {
    assertEquals(
      """
      Feature: F

        Scenario: S [sku] [note]
          Given x
          Where:
            sku    | note
            ---
            KB-1   | first
                   | second
            ---
            MS-200 | one
            ---
      """.trimIndent(),
      reformatted(
        """
        Feature: F
          Scenario: S [sku] [note]
            Given x
            Where:
            sku|note
                ---
              KB-1|first
              |second
                    ---
            MS-200|one
          ---
        """,
      ),
    )
  }

  fun `test reformatting a fenced table moves its fences with its rows`() {
    assertEquals(
      """
      Feature: F

        Scenario: S
          Then the rows:
            ---
            | a | b |
            | 1 | 2 |
            ---
          And done
      """.trimIndent(),
      reformatted(
        """
        Feature: F
          Scenario: S
            Then the rows:
            ---
            |a|b|
              |1|2|
                  ---
            And done
        """,
      ),
    )
  }

  fun `test the tree of a pipe-less table`() {
    val tree = DebugUtil.psiToString(yadda(pipeless), true, false)
    assertTrue(tree, "GherkinTableHeaderRow\n" in tree && "GherkinTableCell:qty" in tree)
  }
}

/** What the editor colours: the tokens its highlighter lexes a Yadda file into. */
class YaddaHighlightingTest : BasePlatformTestCase() {
  private val text = "Feature: F\n  Scenario: S [a]\n    the step\n    where:\n      a | b\n      1 | 2\n"

  private fun tokens(name: String): List<String> {
    val file = myFixture.addFileToProject(name, text)
    val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file.virtualFile)
    highlighter.setText(text)
    val iterator = highlighter.createIterator(0)
    val result = mutableListOf<String>()
    while (!iterator.atEnd()) {
      result += "${iterator.tokenType}:${text.substring(iterator.start, iterator.end).trim()}"
      iterator.advance()
    }
    return result
  }

  fun `test a yadda dot feature file is highlighted with its rows and keywords as what they are`() {
    val tokens = tokens("a.yadda.feature")
    assertTrue(tokens.toString(), "EXAMPLES_KEYWORD:where" in tokens && "TABLE_CELL:1" in tokens && "PIPE:|" in tokens)
  }

  fun `test so is a feature file of a project that depends on playwright-yadda`() {
    myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "1" } }""")
    val tokens = tokens("features/a.feature")
    assertTrue(tokens.toString(), "EXAMPLES_KEYWORD:where" in tokens && "TABLE_CELL:1" in tokens)
  }

  fun `test a feature file of any other project is Gherkin's`() {
    val tokens = tokens("features/a.feature")
    assertTrue(tokens.toString(), "TABLE_CELL:1" !in tokens)
  }
}
