package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Typing in a Yadda file: what the editor does with each character, read back from the document. */
class YaddaTypingTest : BasePlatformTestCase() {
  private fun typed(name: String, text: String, chars: String = "|"): String {
    myFixture.configureByText(name, text.trimIndent())
    myFixture.type(chars)
    return myFixture.editor.document.text
  }

  private fun yaddaProject() = myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "1" } }""")

  private val fenced = """
    Feature: F
      Scenario: S
        Then the rows:
        ---
        | payee | debit  |
        | Ikea  | 100.00 |
        | Aldi <caret>
        ---
  """

  fun `test a pipe typed after a space lines up under the pipe above, in a yadda dot feature file`() {
    assertTrue(typed("a.yadda.feature", fenced).contains("| Aldi  |\n"))
  }

  fun `test the same in a feature file of a playwright-yadda project`() {
    yaddaProject()
    assertTrue(typed("a.feature", fenced).contains("| Aldi  |\n"))
  }

  fun `test the pipe after the last cell lines up too`() {
    val text = """
      Feature: F
        Scenario: S
          Then the rows:
          ---
          | payee | debit  |
          | Ikea  | 100.00 |
          | Aldi  | 3 <caret>
          ---
    """
    assertTrue(typed("a.yadda.feature", text).contains("| Aldi  | 3      |\n"))
  }

  fun `test a table at the end of the file`() {
    val text = """
      Feature: F
        Scenario Outline: S [qty]
          Then x
          Examples:
            | qty | total |
            | 1   | 80.00 |
            | 2 <caret>
    """
    yaddaProject()
    assertTrue(typed("a.feature", text).endsWith("| 2   |"))
  }

  fun `test a pipe typed where the row is already wide enough adds nothing`() {
    val text = """
      Feature: F
        Scenario: S
          Then the rows:
          ---
          | a | b |
          | long cell <caret>
          ---
    """
    assertTrue(typed("a.yadda.feature", text).contains("| long cell |\n"))
  }

  fun `test the first row of a table has nothing above it to line up with`() {
    val text = """
      Feature: F
        Scenario: S
          Then the rows:
          ---
          | a <caret>
          ---
    """
    assertTrue(typed("a.yadda.feature", text).contains("| a |\n"))
  }

  fun `test text in a block is not a row`() {
    val text = """
      Feature: F
        Scenario: S
          Then the note:
          ---
          | first line of prose
          | second <caret>
          ---
    """
    assertTrue(typed("a.yadda.feature", text).contains("| second |\n"))
  }

  fun `test other characters and plain gherkin files are left to the editor`() {
    assertTrue(typed("a.yadda.feature", fenced, "x").contains("| Aldi x\n"))
    myFixture.configureByText("plain.txt", "| a | b |\n| c <caret>".trimIndent())
    myFixture.type("|")
    assertEquals("| a | b |\n| c |", myFixture.editor.document.text)
  }
}

/** Rows of an Examples table without pipes at the start. */
class YaddaPipelessTypingTest : BasePlatformTestCase() {
  private fun typed(text: String, chars: String = "|"): String {
    myFixture.configureByText("a.yadda.feature", text.trimIndent())
    myFixture.type(chars)
    return myFixture.editor.document.text
  }

  private val rows = """
    Feature: F
      Scenario: S [qty]
        Given x
        Examples:
          qty | total
          1   | 80.00
          2 <caret>
  """

  fun `test a pipe typed after a space lines up under the pipe above`() {
    assertTrue(typed(rows).contains("  2   |"))
  }

  fun `test the same under Where, and for the cell after the pipe`() {
    val text = rows.replace("Examples:", "Where:").replace("2 <caret>", "2   | 3<caret>")
    assertTrue(typed(text).contains("2   | 3"))
    val third = """
      Feature: F
        Scenario: S [a]
          Given x
          Where:
            a | bbb | c
            1 | 2   | 3
            4 | 5 <caret>
    """
    assertTrue(typed(third).contains("4 | 5   |"))
  }

  fun `test a line of text that is not in a table is left alone`() {
    val text = """
      Feature: F
        Scenario: S
          the cart | one
          the cart <caret>
    """
    assertTrue(typed(text).contains("the cart |"))
    assertFalse(typed(text).contains("the cart  "))
  }
}
