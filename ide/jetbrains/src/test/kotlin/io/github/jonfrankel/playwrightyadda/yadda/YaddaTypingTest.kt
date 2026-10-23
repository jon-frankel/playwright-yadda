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
