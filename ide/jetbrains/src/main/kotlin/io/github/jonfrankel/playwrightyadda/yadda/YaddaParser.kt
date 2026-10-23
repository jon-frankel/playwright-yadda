package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lang.WhitespacesAndCommentsBinder
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.cucumber.psi.GherkinElementTypes
import org.jetbrains.plugins.cucumber.psi.GherkinParserDefinition
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

/**
 * Gherkin's parser (`GherkinParser`, from the Gherkin plugin, Apache License 2.0) with the three
 * places Yadda differs, so it builds Gherkin's PSI from what Yadda reads:
 *
 * - **A step needs no keyword.** Any line of a scenario that is not something else is a step, so a
 *   line of text starts one. [org.jetbrains.plugins.cucumber.psi.GherkinStep.getKeyword] is null for it.
 * - **Examples rows need no pipes.** [YaddaLexer] cuts `qty | total` into cells and pipes, so a row can
 *   start with a cell, and the rows of a table are found by line, not by the pipe that starts them.
 * - **A row can be annotated.** A line of tags between rows belongs to the table, not the scenario.
 *
 * Gherkin's does it all by what token comes next; so does this.
 */
class YaddaParser : PsiParser {
  override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
    val marker = builder.mark()
    parseFileTopLevel(builder)
    marker.done(GherkinParserDefinition.GHERKIN_FILE)
    return builder.treeBuilt
  }

  private fun parseFileTopLevel(builder: PsiBuilder) {
    while (!builder.eof()) {
      when (builder.tokenType) {
        GherkinTokenTypes.FEATURE_KEYWORD -> parseFeature(builder)
        GherkinTokenTypes.TAG -> parseTags(builder)
        else -> builder.advanceLexer()
      }
    }
  }

  private fun parseFeature(builder: PsiBuilder) {
    val marker = builder.mark()
    val featureEnd = builder.currentOffset + length(builder.tokenText)
    var description: PsiBuilder.Marker? = null
    do {
      val type = builder.tokenType
      if (type == GherkinTokenTypes.TEXT && description == null && hadLineBreakBefore(builder, featureEnd)) {
        description = builder.mark()
      }
      if (type in FEATURE_ELEMENT_STARTS) {
        description?.done(GherkinElementTypes.FEATURE_HEADER)
        description = null
        parseFeatureElements(builder)
        if (builder.tokenType == GherkinTokenTypes.FEATURE_KEYWORD) break
      }
      builder.advanceLexer()
    } while (!builder.eof())
    description?.done(GherkinElementTypes.FEATURE_HEADER)
    marker.done(GherkinElementTypes.FEATURE)
  }

  private fun parseTags(builder: PsiBuilder) {
    while (builder.tokenType == GherkinTokenTypes.TAG) {
      val tag = builder.mark()
      builder.advanceLexer()
      tag.done(GherkinElementTypes.TAG)
    }
  }

  private fun parseFeatureElements(builder: PsiBuilder) {
    var rule: PsiBuilder.Marker? = null
    while (builder.tokenType != GherkinTokenTypes.FEATURE_KEYWORD && !builder.eof()) {
      if (builder.tokenType == GherkinTokenTypes.RULE_KEYWORD) {
        rule?.done(GherkinElementTypes.RULE)
        rule = builder.mark()
        builder.advanceLexer()
        if (builder.tokenType != GherkinTokenTypes.COLON) break
        builder.advanceLexer()
        while (builder.tokenType == GherkinTokenTypes.TEXT) builder.advanceLexer()
      }
      val marker = builder.mark()
      parseTags(builder)
      val outline = builder.tokenType == GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD
      val headingEnd = builder.currentOffset + length(builder.tokenText)
      builder.advanceLexer()
      parseScenario(builder, headingEnd)
      marker.done(if (outline) GherkinElementTypes.SCENARIO_OUTLINE else GherkinElementTypes.SCENARIO)
    }
    rule?.done(GherkinElementTypes.RULE)
  }

  /** The rest of a scenario or background, from the end of its keyword at [headingEnd]. */
  private fun parseScenario(builder: PsiBuilder, headingEnd: Int) {
    // The colon and the title, which are on the keyword's line. A text line after it is a step.
    var previousEnd = headingEnd
    while (builder.tokenType in HEADING_TOKENS && !hadLineBreakBefore(builder, previousEnd)) {
      previousEnd = builder.currentOffset + length(builder.tokenText)
      if (!parseStepParameter(builder)) builder.advanceLexer()
    }

    while (!atScenarioEnd(builder)) {
      if (builder.tokenType == GherkinTokenTypes.TAG) {
        val marker = builder.mark()
        parseTags(builder)
        if (atScenarioEnd(builder)) {
          marker.rollbackTo()
          break
        }
        marker.drop()
      }
      if (parseStepParameter(builder)) continue
      when (builder.tokenType) {
        GherkinTokenTypes.STEP_KEYWORD, GherkinTokenTypes.TEXT -> parseStep(builder)
        GherkinTokenTypes.EXAMPLES_KEYWORD -> parseExamplesBlock(builder)
        else -> builder.advanceLexer()
      }
    }
  }

  private fun atScenarioEnd(builder: PsiBuilder): Boolean {
    var i = 0
    while (builder.lookAhead(i) == GherkinTokenTypes.TAG) i++
    val type = builder.lookAhead(i)
    return type == null || type in SCENARIO_END_TOKENS
  }

  private fun parseStepParameter(builder: PsiBuilder): Boolean {
    if (builder.tokenType != GherkinTokenTypes.STEP_PARAMETER_TEXT) return false
    val marker = builder.mark()
    builder.advanceLexer()
    marker.done(GherkinElementTypes.STEP_PARAMETER)
    return true
  }

  /** A step: its keyword if it has one, the text of its line, then the table or text block under it. */
  private fun parseStep(builder: PsiBuilder) {
    val marker = builder.mark()
    if (builder.tokenType == GherkinTokenTypes.STEP_KEYWORD) builder.advanceLexer()
    var previousEnd = -1
    while (builder.tokenType in STEP_TEXT_TOKENS) {
      if (hadLineBreakBefore(builder, previousEnd)) break
      previousEnd = builder.currentOffset + length(builder.tokenText)
      if (!parseStepParameter(builder)) builder.advanceLexer()
    }
    when (builder.tokenType) {
      GherkinTokenTypes.PIPE -> parseTable(builder)
      GherkinTokenTypes.PYSTRING -> parsePystring(builder)
    }
    marker.done(GherkinElementTypes.STEP)
  }

  private fun parsePystring(builder: PsiBuilder) {
    if (builder.eof()) return
    val marker = builder.mark()
    builder.advanceLexer()
    while (!builder.eof() && builder.tokenType != GherkinTokenTypes.PYSTRING) {
      if (!parseStepParameter(builder)) builder.advanceLexer()
    }
    if (!builder.eof()) builder.advanceLexer()
    marker.done(GherkinElementTypes.PYSTRING)
  }

  private fun parseExamplesBlock(builder: PsiBuilder) {
    val marker = builder.mark()
    var previousEnd = builder.currentOffset + length(builder.tokenText)
    builder.advanceLexer()
    if (builder.tokenType == GherkinTokenTypes.COLON) {
      previousEnd = builder.currentOffset + length(builder.tokenText)
      builder.advanceLexer()
    }
    while (builder.tokenType == GherkinTokenTypes.TEXT && !hadLineBreakBefore(builder, previousEnd)) {
      previousEnd = builder.currentOffset + length(builder.tokenText)
      builder.advanceLexer()
    }
    if (atRow(builder) || (builder.tokenType == GherkinTokenTypes.TAG && rowFollowsTags(builder))) parseTable(builder)
    marker.done(GherkinElementTypes.EXAMPLES_BLOCK)
  }

  /** A table: rows of cells and pipes, each on a line of its own, with the tags that annotate a row before it. */
  private fun parseTable(builder: PsiBuilder) {
    val table = builder.mark()
    var header = true
    var startsWithCell = false
    while (true) {
      if (builder.tokenType == GherkinTokenTypes.TAG && rowFollowsTags(builder)) parseTags(builder)
      if (!atRow(builder)) break
      if (header) startsWithCell = builder.tokenType == GherkinTokenTypes.TABLE_CELL
      parseRow(builder, header, startsWithCell)
      header = false
    }
    table.done(GherkinElementTypes.TABLE)
    table.setCustomEdgeTokenBinders(null, TRAILING_FENCES)
  }

  /**
   * One row. In a table whose rows start with a cell, a row that starts with a pipe has nothing in
   * its first cell (the second line of a row written over two lines does), so that cell is there
   * without a token, and the cells that follow it are in the columns they are in.
   */
  private fun parseRow(builder: PsiBuilder, header: Boolean, tableStartsWithCell: Boolean) {
    val row = builder.mark()
    var previous: IElementType? = null
    var previousEnd = -1
    if (tableStartsWithCell && !header && builder.tokenType == GherkinTokenTypes.PIPE) {
      builder.mark().done(GherkinElementTypes.TABLE_CELL)
    }
    while (atRow(builder)) {
      if (previous != null && hadLineBreakBefore(builder, previousEnd)) break
      val type = builder.tokenType
      // A pipe straight after a pipe closes a cell with nothing in it.
      if (type == GherkinTokenTypes.PIPE && previous == GherkinTokenTypes.PIPE) {
        builder.mark().done(GherkinElementTypes.TABLE_CELL)
      }
      val cell = if (type == GherkinTokenTypes.TABLE_CELL) builder.mark() else null
      previousEnd = builder.currentOffset + length(builder.tokenText)
      previous = type
      builder.advanceLexer()
      cell?.done(GherkinElementTypes.TABLE_CELL)
    }
    row.done(if (header) GherkinElementTypes.TABLE_HEADER_ROW else GherkinElementTypes.TABLE_ROW)
  }

  private fun atRow(builder: PsiBuilder): Boolean =
    builder.tokenType == GherkinTokenTypes.PIPE || builder.tokenType == GherkinTokenTypes.TABLE_CELL

  private fun rowFollowsTags(builder: PsiBuilder): Boolean {
    var i = 0
    while (builder.lookAhead(i) == GherkinTokenTypes.TAG) i++
    val type = builder.lookAhead(i)
    return type == GherkinTokenTypes.PIPE || type == GherkinTokenTypes.TABLE_CELL
  }

  private fun hadLineBreakBefore(builder: PsiBuilder, previousEnd: Int): Boolean {
    if (previousEnd < 0) return false
    return builder.originalText.subSequence(previousEnd, builder.currentOffset).contains('\n')
  }

  private fun length(text: String?): Int = text?.length ?: 0

  private companion object {
    /** A table takes the dashes that close it, so they go where its rows go when the file is reformatted. */
    val TRAILING_FENCES = WhitespacesAndCommentsBinder { tokens, _, getter ->
      var end = 0
      for (i in tokens.indices) {
        val type = tokens[i]
        if (type == GherkinTokenTypes.COMMENT && FENCE.matches(getter.get(i))) end = i + 1
        else if (type != TokenType.WHITE_SPACE) break
      }
      end
    }

    val FENCE = Regex("""[|┆]?-{3,}.*""")

    val SCENARIO_END_TOKENS = TokenSet.create(
      GherkinTokenTypes.BACKGROUND_KEYWORD,
      GherkinTokenTypes.SCENARIO_KEYWORD,
      GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD,
      GherkinTokenTypes.RULE_KEYWORD,
      GherkinTokenTypes.FEATURE_KEYWORD,
    )

    val FEATURE_ELEMENT_STARTS = TokenSet.orSet(
      GherkinTokenTypes.SCENARIOS_KEYWORDS,
      TokenSet.create(GherkinTokenTypes.RULE_KEYWORD, GherkinTokenTypes.BACKGROUND_KEYWORD, GherkinTokenTypes.TAG),
    )

    val HEADING_TOKENS = TokenSet.create(
      GherkinTokenTypes.COLON,
      GherkinTokenTypes.TEXT,
      GherkinTokenTypes.STEP_PARAMETER_TEXT,
      GherkinTokenTypes.STEP_PARAMETER_BRACE,
    )

    val STEP_TEXT_TOKENS = TokenSet.create(
      GherkinTokenTypes.TEXT,
      GherkinTokenTypes.STEP_PARAMETER_BRACE,
      GherkinTokenTypes.STEP_PARAMETER_TEXT,
    )
  }
}
