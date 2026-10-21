package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.lexer.Lexer
import com.intellij.psi.TokenType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

class YaddaLexerTest : BasePlatformTestCase() {
  private class Token(val type: String, val start: Int, val end: Int, val state: Int)

  private fun lexer(): Lexer = YaddaParserDefinition().createLexer(project)

  private fun tokens(text: String, from: Int = 0, state: Int = 0): List<Token> {
    val lexer = lexer()
    lexer.start(text, from, text.length, state)
    val result = mutableListOf<Token>()
    while (lexer.tokenType != null) {
      result += Token(lexer.tokenType.toString(), lexer.tokenStart, lexer.tokenEnd, lexer.state)
      lexer.advance()
    }
    return result
  }

  /** Every kind of block, in one feature. */
  private val everything = """
    @Libraries=a, b
    Feature: F
      Scenario: S
        Given a payload
        ---
        {"a": 1,
         "b": [1, 2]}
        ---
        When the rows
          ---
          id,name
          1,Ann
          ---
        Then a table
        ---
        | a | b |
        | 1 | 2 |
        ---
        And an empty block
        ---
        ---
        And an indented table
          | a |
          | 1 |
        And a block with a remark
        --- json
        [1]
        ---

      Scenario Outline: O
        Given [x]
        Examples:
          ---
          | x |
          | 1 |
          ---
        When the last step has no closing fence
        ---
        plain text
        to the end
  """.trimIndent() + "\n"

  private fun text(token: Token, source: String = everything) = source.substring(token.start, token.end)

  fun `test the tokens cover the text without gaps`() {
    val all = tokens(everything)
    assertEquals(0, all.first().start)
    assertEquals(everything.length, all.last().end)
    all.zipWithNext { a, b -> assertEquals("gap after '${text(a)}'", a.end, b.start) }
  }

  fun `test a block that is not a table is a doc string`() {
    val all = tokens(everything)
    val quotes = all.filter { it.type == GherkinTokenTypes.PYSTRING.toString() }
    val content = all.filter { it.type == GherkinTokenTypes.PYSTRING_TEXT.toString() }.map { text(it).trim() }

    assertTrue(content.toString(), "{\"a\": 1,\n     \"b\": [1, 2]}" in content)
    assertTrue(content.toString(), "id,name\n      1,Ann" in content)
    assertTrue(content.toString(), "plain text\n    to the end" in content)
    assertTrue("the remark after the dashes belongs to the fence", quotes.any { text(it) == "--- json" })
  }

  fun `test a block of rows is a table whose fences are comments`() {
    val all = tokens(everything)
    val comments = all.filter { it.type == GherkinTokenTypes.COMMENT.toString() }.map { text(it) }
    assertTrue(comments.toString(), comments.size >= 4)
    assertTrue(all.any { it.type == GherkinTokenTypes.TABLE_CELL.toString() && text(it) == "1" })
  }

  /** The state of the doc-string and table modes this lexer adds, above Gherkin's own small states. */
  private fun Token.inBlock() = state ushr 24 != 0

  private fun assertRestartsAt(source: String, all: List<Token>, only: (Token) -> Boolean) {
    var restarted = 0
    all.forEachIndexed { index, token ->
      if (!only(token)) return@forEachIndexed
      restarted++
      val rest = tokens(source, token.start, token.state)
      assertEquals(
        "restart at '${text(token, source)}' (${token.type}, state ${token.state}) at ${token.start}",
        all.drop(index).map { "${it.type} ${it.start}-${it.end} ${it.state}" },
        rest.map { "${it.type} ${it.start}-${it.end} ${it.state}" },
      )
    }
    assertTrue("nothing was restarted", restarted > 10)
  }

  // The editor restarts only at tokens in the initial state.
  fun `test lexing restarts at every token in the initial state`() {
    assertRestartsAt(everything, tokens(everything)) { it.state == 0 }
  }

  // Gherkin's lexer can't resume in its own mid-line states, but a block can be picked up anywhere.
  fun `test lexing resumes at every token inside a block`() {
    assertRestartsAt(everything, tokens(everything)) { it.inBlock() }
  }

  fun `test restarting works the same when the text ends inside a block`() {
    val cut = everything.substring(0, everything.indexOf("[1, 2]"))
    assertRestartsAt(cut, tokens(cut)) { it.state == 0 || it.inBlock() }
  }

  fun `test only the lexer's own tokens and Gherkin's appear`() {
    val known = tokens(everything).map { it.type }.toSet()
    assertTrue(known.toString(), TokenType.WHITE_SPACE.toString() in known)
  }
}
