package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** A JSON, CSV or text block goes where its step goes when Reformat Code moves the step. */
class YaddaFenceIndentTest : BasePlatformTestCase() {
  private fun reformatted(text: String, name: String = "a.yadda.feature"): String {
    val file: PsiFile = myFixture.configureByText(name, text)
    WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(file) }
    return file.text
  }

  private val head = "Feature: F\n\n  Scenario: S\n"

  fun `test a block moves in with its step, a column at a time, and keeps the text's own indent`() {
    val before = head + "        Given a\n   ---\n   {\"a\": 1,\n    \"b\": 2}\n   ---\n    Then b\n"
    val after = head + "    Given a\n      ---\n      {\"a\": 1,\n       \"b\": 2}\n      ---\n    Then b\n"
    assertEquals(after, reformatted(before))
  }

  fun `test a block that is too far in moves out`() {
    val before = head + "    Given a\n            --- json\n            [1,\n             2]\n            ---\n"
    val after = head + "    Given a\n      --- json\n      [1,\n       2]\n      ---\n"
    assertEquals(after, reformatted(before))
  }

  fun `test a block that is where it should be is left exactly as it is`() {
    val text = head + "    Given a\n      ---\n      id,name\n\n      1,Ann\n      ---\n    Then b\n      ---\n      text\n"
    assertEquals(text, reformatted(text))
  }

  fun `test the last block moves though it has no closing fence`() {
    val before = head + "  Given a\n---\ntext\n  more\n"
    val after = head + "    Given a\n      ---\n      text\n        more\n"
    // The step moves in by two and the block by the six it takes to be two in from it.
    assertEquals(after, reformatted(before))
  }

  fun `test blank lines in a block are not given trailing spaces`() {
    val before = head + "        Given a\n   ---\n   a\n\n   b\n   ---\n"
    val after = head + "    Given a\n      ---\n      a\n\n      b\n      ---\n"
    assertEquals(after, reformatted(before))
  }

  fun `test a block is left alone if moving it out would cut into a line of its text`() {
    // The fence is ten in and wants to be six, but a line of the block starts at two.
    val text = head + "    Given a\n          ---\n  short\n          ---\n"
    assertEquals(text, reformatted(text))
  }

  fun `test a block with a tab in its indent is left alone`() {
    val text = head + "    Given a\n  ---\n\tx\n  ---\n"
    assertEquals(text, reformatted(text))
  }

  fun `test every block moves, in a file with several`() {
    val before = head + "    Given a\n---\none\n---\n  When b\n---\ntwo\n---\n"
    val after = head + "    Given a\n      ---\n      one\n      ---\n    When b\n      ---\n      two\n      ---\n"
    assertEquals(after, reformatted(before))
  }

  fun `test a table's fences still go with its rows`() {
    val before = head + "        Given a\n   ---\n   | a | b |\n   | 1 | 2 |\n   ---\n"
    val after = head + "    Given a\n      ---\n      | a | b |\n      | 1 | 2 |\n      ---\n"
    assertEquals(after, reformatted(before))
  }

  fun `test a doc string in a plain Gherkin file is not touched`() {
    val text = head + "    Given a\n  \"\"\"\n  x\n  \"\"\"\n"
    assertEquals(text, reformatted(text, "plain.feature"))
  }

  fun `test the real example files are already where Reformat Code would put them`() {
    myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "1" } }""")
    for (name in listOf("features/orders.feature", "features/discounts.yadda.feature")) {
      val source = java.io.File("../../examples/basic/$name").readText()
      assertEquals(name, source, reformatted(source, name.substringAfterLast('/')))
    }
  }
}
