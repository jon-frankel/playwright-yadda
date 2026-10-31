package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.Random

/**
 * Files made of lines picked at random from everything Yadda has a line for, in any order. Whatever
 * they mean, lexing must cover the text without gaps, restart where the editor restarts, and the
 * parser and formatter must get through them.
 */
class YaddaFuzzTest : BasePlatformTestCase() {
  private val lines = listOf(
    "Feature: F", "feature: lower", "Rule: R", "Background:", "Scenario: S [a]", "scenario: s", "Scenario Outline: O [a]",
    "Examples:", "Where:", "where: old", "  Examples: with a title", "Given a step", "when something | pipe", "then it", "And more", "a step with no keyword",
    "| a | b |", "|", "||", "| a ||", "a | b", "a | b |", "1 | 2", "x", "a ┆ b", "┆", "a|b|c", "Given | When", "When | Who",
    "---", "--- json", "-----", "|---|", "┆---", "{\"a\": 1}", "id,name", "@tag", "@a=b", "@Libraries=x, y", "@a @b",
    "# a comment", "###", "### a note", "\"\"\"", "\"\"\" json", "<param>", "Scenario: <x>", "", "", "  ", "\ttabbed | cell",
  )

  private fun fuzz(seed: Long): String {
    val random = Random(seed)
    return (0 until 4 + random.nextInt(30)).joinToString("\n") { " ".repeat(random.nextInt(3) * 2) + lines[random.nextInt(lines.size)] } +
      if (random.nextBoolean()) "\n" else ""
  }

  private class Token(val type: String, val start: Int, val end: Int, val state: Int)

  private fun tokens(text: String, from: Int = 0, state: Int = 0): List<Token> {
    val lexer = YaddaParserDefinition().createLexer(project)
    lexer.start(text, from, text.length, state)
    val result = mutableListOf<Token>()
    while (lexer.tokenType != null) {
      result += Token(lexer.tokenType.toString(), lexer.tokenStart, lexer.tokenEnd, lexer.state)
      lexer.advance()
    }
    return result
  }

  fun `test lexing covers the text and restarts in the initial state, for random files`() {
    for (seed in 1L..400L) {
      val text = fuzz(seed)
      val all = tokens(text)
      val note = "seed $seed:\n$text"
      if (text.isEmpty()) continue
      assertEquals(note, 0, all.first().start)
      assertEquals(note, text.length, all.last().end)
      all.zipWithNext { a, b ->
        assertEquals("gap after '${text.substring(a.start, a.end)}' ($note)", a.end, b.start)
        assertTrue("empty token at ${a.start} ($note)", a.end > a.start)
      }
      all.forEachIndexed { index, token ->
        if (token.state != 0) return@forEachIndexed
        val rest = tokens(text, token.start, token.state)
        assertEquals(
          "restart at ${token.start} ($note)",
          all.drop(index).map { "${it.type} ${it.start}-${it.end} ${it.state}" },
          rest.map { "${it.type} ${it.start}-${it.end} ${it.state}" },
        )
      }
    }
  }

  fun `test parsing and reformatting random files does not throw, and keeps the text but for space`() {
    for (seed in 1L..250L) {
      val text = fuzz(seed)
      val file = myFixture.configureByText("a.yadda.feature", text)
      assertEquals(text, file.text)
      WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(file) }
      assertEquals("seed $seed:\n$text", text.filterNot { it.isWhitespace() }, file.text.filterNot { it.isWhitespace() })
      myFixture.doHighlighting()
    }
  }
}
