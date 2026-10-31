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

      Scenario: Tail
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

  /** Examples in every spelling, then a step, so the lexer has to leave them. */
  private val examples = """
    Feature: F
      scenario: lower [a]
        the first step
        Where: the old name
          a | bb
          @skip
          1 | one
          ---
          Then | and
          ---

      @slow
      Scenario Outline: O [a]
        Given x
        Examples:
          | a | b |
          ┆
          3 ┆ 4 | 5
          6

      Scenario: Last
        the last step | with a pipe
  """.trimIndent() + "\n"

  private fun typesOf(source: String, vararg wanted: String): List<String> =
    tokens(source).filter { it.type in wanted }.map { text(it, source) }

  fun `test the rows of an Examples table are cells and pipes, whether or not they start with a pipe`() {
    val cell = GherkinTokenTypes.TABLE_CELL.toString()
    val pipe = GherkinTokenTypes.PIPE.toString()
    assertEquals(
      // A row with a ┆ in it is split on that alone, so "4 | 5" is one cell.
      listOf("a", "bb", "1", "one", "Then", "and", "a", "b", "3", "4 | 5", "6"),
      typesOf(examples, cell),
    )
    // The pipe in the last step is text, not a separator.
    assertEquals(listOf("|", "|", "|", "|", "|", "|", "┆", "┆"), typesOf(examples, pipe))
    assertEquals(listOf("the last step | with a pipe"), tokens(examples).filter { it.type == GherkinTokenTypes.TEXT.toString() }.map { text(it, examples) }.takeLast(1))
  }

  fun `test Yadda's keywords are keywords in any case, and Where is Examples`() {
    val keywords = tokens(examples).filter { it.type.endsWith("_KEYWORD") }.map { it.type to text(it, examples) }
    assertEquals(
      listOf(
        GherkinTokenTypes.FEATURE_KEYWORD.toString() to "Feature",
        GherkinTokenTypes.SCENARIO_KEYWORD.toString() to "scenario",
        GherkinTokenTypes.EXAMPLES_KEYWORD.toString() to "Where",
        GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD.toString() to "Scenario Outline",
        GherkinTokenTypes.STEP_KEYWORD.toString() to "Given",
        GherkinTokenTypes.EXAMPLES_KEYWORD.toString() to "Examples",
        GherkinTokenTypes.SCENARIO_KEYWORD.toString() to "Scenario",
      ),
      keywords,
    )
  }

  fun `test the tokens of rows and keywords cover the text without gaps`() {
    val all = tokens(examples)
    assertEquals(0, all.first().start)
    assertEquals(examples.length, all.last().end)
    all.zipWithNext { a, b -> assertEquals("gap after '${text(a, examples)}'", a.end, b.start) }
  }

  fun `test lexing restarts at every token in the initial state, with Examples in the text`() {
    assertRestartsAt(examples, tokens(examples)) { it.state == 0 }
  }

  fun `test a row is not in the initial state, so the editor never restarts in the middle of one`() {
    val all = tokens(examples)
    val rows = all.filter { it.type == GherkinTokenTypes.TABLE_CELL.toString() }
    assertTrue(rows.isNotEmpty() && rows.none { it.state == 0 })
    val last = all.first { it.start == examples.indexOf("the last step") }
    assertEquals("the lexer is out of the table by the last scenario", 0, last.state and 0xFF000000.toInt())
  }

  /** Headings Gherkin has and Yadda's word list does not, then a row under Where that starts with one. */
  private val gherkinOnly = """
    Feature: F
      Scenario: S
        Given a step
        Example: Yadda reads this as a step
        Scenarios: and this
        Business Need: and this
        Then done
      Where:
        a | b
        Example: x | y
        1 | 2
      Scenario: T
        Given more
        Then more
  """.trimIndent()

  fun `test a heading only Gherkin has is text, as a step or as a row under Where`() {
    val all = tokens(gherkinOnly)
    val headings = all.filter { it.type.endsWith("_KEYWORD") && it.type != GherkinTokenTypes.STEP_KEYWORD.toString() }
    assertEquals(
      listOf("Feature", "Scenario", "Where", "Scenario"),
      headings.map { text(it, gherkinOnly) },
    )
    assertEquals(
      listOf("Example: Yadda reads this as a step", "Scenarios: and this", "Business Need: and this"),
      typesOf(gherkinOnly, GherkinTokenTypes.TEXT.toString()).filter { it.contains(":") },
    )
    assertEquals(
      listOf("a", "b", "Example: x", "y", "1", "2"),
      typesOf(gherkinOnly, GherkinTokenTypes.TABLE_CELL.toString()),
    )
  }

  fun `test the lines of a Gherkin-only heading are covered and the lexer restarts after them`() {
    val all = tokens(gherkinOnly)
    assertEquals(0, all.first().start)
    assertEquals(gherkinOnly.length, all.last().end)
    all.zipWithNext { a, b -> assertEquals("gap after '${text(a, gherkinOnly)}'", a.end, b.start) }
    assertRestartsAt(gherkinOnly, all) { it.state == 0 }
  }

  fun `test the steps of a scenario include the lines of a Gherkin-only heading`() {
    val file = myFixture.configureByText("a.yadda.feature", gherkinOnly)
    val steps = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, org.jetbrains.plugins.cucumber.psi.GherkinStep::class.java)
    assertEquals(
      listOf("a step", "Example: Yadda reads this as a step", "Scenarios: and this", "Business Need: and this", "done", "more", "more"),
      steps.map { it.name },
    )
  }

  /** A comment between lines of `#`s, with everything Yadda would otherwise read in it, then a step. */
  private val commentBlock = """
    Feature: F
      Scenario: S
        Given a step
        ###
        Given hidden
        Scenario: Hidden
        ---
        ###
        # a single line
        Then done
        ### a note that opens a block
        Then hidden too
        And so is this
  """.trimIndent() + "\n"

  fun `test lines between lines of hashes are one comment, whatever they say`() {
    val all = tokens(commentBlock)
    assertEquals(
      listOf(
        "###\n    Given hidden\n    Scenario: Hidden\n    ---\n    ###",
        "# a single line",
        // The last block is never closed, so it runs to the end of the text.
        "### a note that opens a block\n    Then hidden too\n    And so is this\n",
      ),
      typesOf(commentBlock, GherkinTokenTypes.COMMENT.toString()),
    )
    assertEquals(
      listOf("Feature", "Scenario"),
      all.filter { it.type == GherkinTokenTypes.FEATURE_KEYWORD.toString() || it.type == GherkinTokenTypes.SCENARIO_KEYWORD.toString() }.map { text(it, commentBlock) },
    )
    assertEquals(listOf("Given", "Then"), typesOf(commentBlock, GherkinTokenTypes.STEP_KEYWORD.toString()))
    assertEquals(0, all.first().start)
    assertEquals(commentBlock.length, all.last().end)
    all.zipWithNext { a, b -> assertEquals("gap after '${text(a, commentBlock)}'", a.end, b.start) }
  }

  fun `test lexing restarts at every token in the initial state, with comment blocks in the text`() {
    assertRestartsAt(commentBlock, tokens(commentBlock)) { it.state == 0 }
  }

  fun `test a line of dashes in a comment does not close the block above it`() {
    val source = """
      Feature: F
        Scenario: S
          Given a payload
          ---
          [1]
          ###
          ---
          ###
          ---
          Then done
    """.trimIndent() + "\n"
    val all = tokens(source)
    val fences = all.filter { it.type == GherkinTokenTypes.PYSTRING.toString() }
    // The fences that count are the first and the last; the dashes in the comment are not one.
    assertEquals(listOf(source.indexOf("---"), source.lastIndexOf("---")), fences.map { it.start })
    val content = all.single { it.type == GherkinTokenTypes.PYSTRING_TEXT.toString() }
    assertTrue(text(content, source), "###" in text(content, source))
  }

  /** A line of triple quotes is a doc string to Gherkin, and a line of text to Yadda. */
  private val quotes = """
    Feature: F
      Scenario: S
        Given a step
        ${'"'}${'"'}${'"'}
        Then the line above is a step
        ${'"'}${'"'}${'"'}
        And so is this one
  """.trimIndent() + "\n"

  fun `test triple quotes are text, and the lexer carries on after them as if they were any line`() {
    val all = tokens(quotes)
    assertTrue(all.none { it.type == GherkinTokenTypes.PYSTRING.toString() || it.type == GherkinTokenTypes.PYSTRING_TEXT.toString() })
    assertEquals(
      // After the titles of the feature and the scenario.
      listOf("a step", "\"\"\"", "the line above is a step", "\"\"\"", "so is this one"),
      typesOf(quotes, GherkinTokenTypes.TEXT.toString()).drop(2),
    )
    assertEquals(listOf("Given", "Then", "And"), typesOf(quotes, GherkinTokenTypes.STEP_KEYWORD.toString()))
    assertEquals(quotes.length, all.last().end)
    all.zipWithNext { a, b -> assertEquals("gap after '${text(a, quotes)}'", a.end, b.start) }
    assertRestartsAt(quotes, all) { it.state == 0 }
  }

  /**
   * The editor re-lexes from the last token in the initial state before a change. What a block's opening fence is
   * depends on what follows it, so no token after the opening fence, up to the closing one, may be in the initial
   * state: a change anywhere in the block then re-lexes the fence that opens it too.
   */
  fun `test only the opening fence of a block is in the initial state, so editing the closing fence re-lexes the opening one`() {
    val source = """
      Feature: F
        Scenario: S
          Given a table
          ---
          | a |
          ---
          Given a payload
          ---
          [1]
          ---
          Given an unclosed block
          ---
          text
    """.trimIndent() + "\n"
    val all = tokens(source)
    fun check(open: Int, close: Int?) {
      val first = all.indexOfFirst { it.start == open }
      assertEquals("the opening fence at $open is in the initial state", 0, all[first].state)
      val stop = if (close == null) all.size else all.indexOfFirst { it.start > close }
      val inside = all.subList(first + 1, stop)
      assertTrue("tokens inside the block at $open", inside.isNotEmpty())
      assertEquals(
        "tokens inside the block at $open that are in the initial state",
        emptyList<String>(),
        inside.filter { it.state == 0 }.map { text(it, source) },
      )
    }
    val table = source.indexOf("---")
    check(table, source.indexOf("---", table + 3))
    val payload = source.indexOf("---", source.indexOf("a payload"))
    check(payload, source.indexOf("---", payload + 3))
    check(source.lastIndexOf("---"), null)
  }
}
