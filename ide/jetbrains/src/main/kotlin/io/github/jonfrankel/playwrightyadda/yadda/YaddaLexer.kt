package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes
import java.util.regex.Pattern

/**
 * Gherkin's lexer, taught Yadda's multi-line steps: a block between two lines of three or more
 * dashes, which belongs to the step above it.
 *
 * - A block of table rows is a table. The fences become comments and the rows are lexed by Gherkin,
 *   so the parser builds the step's table as if the fences were not there.
 * - Any other block (JSON, CSV, prose) is a doc string: the fence lines and the text between them
 *   are the tokens Gherkin makes of `"""`, so the parser attaches it to the step and the editor can
 *   inject a language into it.
 *
 * A block's closing fence is optional for the last step, so an unclosed block runs to the end.
 *
 * Under `Examples:` (or `Where:`) the lines are table rows, whether or not they start with a pipe.
 * Gherkin lexes `qty | total` as one text token, so those lines are cut into cells and pipes here,
 * and the fences that group a row over several lines stay comments. Yadda's keywords are matched
 * without regard to case, and `Where:` is Yadda's other word for `Examples:`; the lines Gherkin
 * read as text are given the keyword tokens it would have given them.
 *
 * Which of the two a block is depends on the rows after the opening fence, so those are read ahead;
 * editing the closing fence changes how the opening one is coloured only once the file is re-lexed.
 *
 * The editor restarts lexing from any token with the state it reported, so [getState] says where in
 * a block a token is, and [start] rebuilds the block's bounds from the text.
 */
class YaddaLexer(private val delegate: Lexer, private val vocabulary: Vocabulary = Vocabulary.ENGLISH) : LexerBase() {
  private var buf: CharSequence = ""
  private var end = 0

  // The token being reported when it is ours rather than the delegate's.
  private var own = false
  private var ownType: IElementType? = null
  private var ownStart = 0
  private var ownEnd = 0
  private var stage = STAGE_OPEN
  private var contentEnd = 0
  private var closeStart = -1
  private var closeEnd = -1

  // A fenced table, which the delegate lexes: the start of its opening and closing fence tokens.
  private var tableOpen = -1
  private var tableClose = -1

  // Whether the lexer is under an `Examples:` keyword, after the token being reported.
  private var examples = false

  // The pieces a single token of the delegate's is cut into, and which one is being reported.
  private var pieces: List<Piece> = emptyList()
  private var pieceIndex = 0

  // Where the delegate starts again once the pieces are done, when they cover more than its token.
  private var resumeAt = -1

  private class Piece(val type: IElementType, val start: Int, val end: Int)

  private val headingLine: Pattern = Pattern.compile(
    "^(?:$GHERKIN_HEADINGS|${vocabulary.headingWords})\\s*:",
    Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
  )

  private fun cut() = pieceIndex < pieces.size

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    buf = buffer
    end = endOffset
    own = false
    tableOpen = -1
    tableClose = -1
    pieces = emptyList()
    pieceIndex = 0
    resumeAt = -1

    val mode = initialState ushr MODE_SHIFT
    val delegateState = initialState and DELEGATE_MASK
    examples = initialState and EXAMPLES_BIT != 0
    if (startOffset >= endOffset) {
      delegate.start(buffer, startOffset, endOffset, delegateState)
      return
    }
    when (mode) {
      MODE_NORMAL -> delegate.start(buffer, startOffset, endOffset, delegateState)
      MODE_TABLE -> {
        delegate.start(buffer, startOffset, endOffset, delegateState)
        tableOpen = startOffset - 1
        tableClose = fenceFrom(startOffset)?.let { firstNonBlank(it.first) } ?: Int.MAX_VALUE
      }
      else -> {
        delegate.start(buffer, endOffset, endOffset, 0)
        resumeBlock(mode, startOffset)
        return
      }
    }
    settle()
  }

  override fun getState(): Int {
    if (own) return if (stage == STAGE_OPEN) delegate.state else stage shl MODE_SHIFT
    val flag = if (examples) EXAMPLES_BIT else 0
    return (if (inTable()) MODE_TABLE shl MODE_SHIFT else 0) or flag or delegate.state
  }

  override fun getTokenType(): IElementType? {
    if (cut()) return pieces[pieceIndex].type
    if (own) return ownType
    val type = delegate.tokenType
    if (type == GherkinTokenTypes.TEXT && FENCE_LINE.matcher(delegate.tokenText).matches()) {
      return GherkinTokenTypes.COMMENT
    }
    return type
  }

  override fun getTokenStart(): Int = if (cut()) pieces[pieceIndex].start else if (own) ownStart else delegate.tokenStart

  override fun getTokenEnd(): Int = if (cut()) pieces[pieceIndex].end else if (own) ownEnd else delegate.tokenEnd

  override fun getBufferSequence(): CharSequence = buf

  override fun getBufferEnd(): Int = end

  override fun advance() {
    if (own) {
      enter(stage + 1)
      return
    }
    if (cut()) {
      pieceIndex++
      if (cut()) return
      pieces = emptyList()
      pieceIndex = 0
      if (resumeAt >= 0) {
        delegate.start(buf, resumeAt, end, 0)
        resumeAt = -1
        settle()
        return
      }
    }
    if (delegate.tokenStart >= tableClose && tableClose >= 0) {
      tableOpen = -1
      tableClose = -1
    }
    delegate.advance()
    settle()
  }

  /**
   * Looks at the delegate's current token and takes over if it opens a block that is a doc string,
   * or cuts it into pieces if it is a line of Yadda's that Gherkin read as plain text.
   */
  private fun settle() {
    if (own || delegate.tokenType == null) return
    val type = delegate.tokenType
    val start = delegate.tokenStart
    // Under Examples a line is a row, even one that starts with a word Gherkin reads as a step keyword. A line
    // that starts with one of Gherkin's headings that Yadda's word list lacks (`Example:`, `Scenarios:`,
    // `Business Need:`) is a step, or a row under Examples, to Yadda.
    if (onlyBlankBefore(start) && (examples && type == GherkinTokenTypes.STEP_KEYWORD || isGherkinOnlyHeading(type, start))) {
      var rowEnd = lineEnd(start)
      while (rowEnd > start && buf[rowEnd - 1].isWhitespace()) rowEnd--
      pieces = if (examples) cells(start, rowEnd) else listOf(Piece(GherkinTokenTypes.TEXT, start, rowEnd))
      pieceIndex = 0
      resumeAt = rowEnd
      return
    }
    if (type != GherkinTokenTypes.TEXT) track(type)
    if (type != GherkinTokenTypes.TEXT && type != GherkinTokenTypes.PIPE) return
    if (!startsFenceLine(start)) {
      if (type == GherkinTokenTypes.TEXT && onlyBlankBefore(start)) cutLine(start, delegate.tokenEnd)
      return
    }
    // The fences of a row that goes over several lines are comments, and so is a table's.
    if (start == tableClose || examples) return

    val openEnd = lineEnd(start)
    val close = findFence(minOf(openEnd + 1, end))
    val blockEnd = close?.first ?: end
    if (isTable(openEnd, blockEnd)) {
      tableOpen = start
      tableClose = close?.let { firstNonBlank(it.first) } ?: Int.MAX_VALUE
      return
    }
    closeStart = close?.first ?: -1
    closeEnd = close?.second ?: -1
    contentEnd = blockEnd
    own = true
    ownType = GherkinTokenTypes.PYSTRING
    ownStart = start
    ownEnd = openEnd
    stage = STAGE_OPEN
  }

  /** Whether [type] at [start] is a heading Gherkin reads and the vocabulary's word list does not have. */
  private fun isGherkinOnlyHeading(type: IElementType?, start: Int): Boolean =
    type in HEADING_TOKENS && vocabulary.keywordAt(buf.subSequence(start, lineEnd(start))) == null

  /** Whether the rows of an Examples block are what follows [type], which is a token of the delegate's. */
  private fun track(type: IElementType?) {
    when (type) {
      GherkinTokenTypes.EXAMPLES_KEYWORD -> examples = true
      GherkinTokenTypes.FEATURE_KEYWORD,
      GherkinTokenTypes.RULE_KEYWORD,
      GherkinTokenTypes.BACKGROUND_KEYWORD,
      GherkinTokenTypes.SCENARIO_KEYWORD,
      GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD,
      -> examples = false
      GherkinTokenTypes.TAG -> if (examples && tagsLeaveExamples(delegate.tokenStart)) examples = false
    }
  }

  /**
   * A tag line inside a table annotates the row under it. It ends the table when what follows the tags
   * is a heading, or nothing.
   */
  private fun tagsLeaveExamples(tagStart: Int): Boolean {
    var from = lineStart(tagStart)
    while (from < end) {
      val to = lineEnd(from)
      val line = buf.subSequence(from, to).trim()
      if (line.isNotEmpty() && line[0] != '@' && line[0] != '#') return headingLine.matcher(line).find()
      from = to + 1
    }
    return true
  }

  /** Cuts the line of text the delegate found at [start] into the tokens Gherkin would have made of it. */
  private fun cutLine(start: Int, lineEnd: Int) {
    val text = buf.subSequence(start, lineEnd)
    val word = vocabulary.keywordAt(text)
    if (word != null) {
      val type = keywordType(word.first, text.subSequence(0, word.second))
      val colon = start + word.second
      val list = mutableListOf(Piece(type, start, colon), Piece(GherkinTokenTypes.COLON, colon, colon + 1))
      var title = colon + 1
      while (title < lineEnd && buf[title].isWhitespace()) title++
      if (title > colon + 1) list.add(Piece(TokenType.WHITE_SPACE, colon + 1, title))
      if (title < lineEnd) list.add(Piece(GherkinTokenTypes.TEXT, title, lineEnd))
      pieces = list
      track(type)
    } else if (examples && !FENCE_LINE.matcher(text).matches()) {
      pieces = cells(start, lineEnd)
    }
    pieceIndex = 0
  }

  /**
   * A row of an Examples table that does not start with a pipe: its cells, and the pipes between
   * them. Yadda splits a row on `┆` if there is one in it, and on `|` if not.
   */
  private fun cells(start: Int, lineEnd: Int): List<Piece> {
    val separator = if ((start until lineEnd).any { buf[it] == '┆' }) '┆' else '|'
    val list = mutableListOf<Piece>()
    fun cell(from: Int, to: Int) {
      var a = from
      var b = to
      while (a < b && buf[a].isWhitespace()) a++
      while (b > a && buf[b - 1].isWhitespace()) b--
      if (a == b) {
        if (to > from) list.add(Piece(TokenType.WHITE_SPACE, from, to))
        return
      }
      if (a > from) list.add(Piece(TokenType.WHITE_SPACE, from, a))
      list.add(Piece(GherkinTokenTypes.TABLE_CELL, a, b))
      if (to > b) list.add(Piece(TokenType.WHITE_SPACE, b, to))
    }
    var from = start
    for (i in start until lineEnd) {
      if (buf[i] != separator) continue
      cell(from, i)
      list.add(Piece(GherkinTokenTypes.PIPE, i, i + 1))
      from = i + 1
    }
    cell(from, lineEnd)
    return list
  }

  private fun keywordType(heading: Vocabulary.Heading, word: CharSequence): IElementType = when (heading) {
    Vocabulary.Heading.FEATURE -> GherkinTokenTypes.FEATURE_KEYWORD
    Vocabulary.Heading.RULE -> GherkinTokenTypes.RULE_KEYWORD
    Vocabulary.Heading.BACKGROUND -> GherkinTokenTypes.BACKGROUND_KEYWORD
    Vocabulary.Heading.SCENARIO ->
      if (word.toString().equals("scenario outline", ignoreCase = true)) GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD
      else GherkinTokenTypes.SCENARIO_KEYWORD
    Vocabulary.Heading.EXAMPLES -> GherkinTokenTypes.EXAMPLES_KEYWORD
  }

  /** Reports the token of [stage] of the current block, or hands back to the delegate after its last. */
  private fun enter(stage: Int) {
    when (stage) {
      STAGE_CONTENT ->
        if (ownEnd < contentEnd) emit(GherkinTokenTypes.PYSTRING_TEXT, ownEnd, contentEnd, STAGE_CONTENT)
        else enter(STAGE_INDENT)
      STAGE_INDENT ->
        if (closeStart < 0) finishBlock()
        else if (firstNonBlank(closeStart) > closeStart) {
          emit(TokenType.WHITE_SPACE, closeStart, firstNonBlank(closeStart), STAGE_INDENT)
        } else enter(STAGE_CLOSE)
      STAGE_CLOSE -> emit(GherkinTokenTypes.PYSTRING, firstNonBlank(closeStart), closeEnd, STAGE_CLOSE)
      else -> finishBlock()
    }
  }

  private fun emit(type: IElementType, start: Int, end: Int, stage: Int) {
    own = true
    ownType = type
    ownStart = start
    ownEnd = end
    this.stage = stage
  }

  private fun finishBlock() {
    own = false
    val resume = if (closeStart >= 0) closeEnd else end
    delegate.start(buf, resume, end, 0)
    settle()
  }

  /** Picks up in the middle of a block, at the start of the token the editor remembered. */
  private fun resumeBlock(stage: Int, offset: Int) {
    when (stage) {
      STAGE_CONTENT -> {
        val close = findFence(minOf(offset + 1, end))
        closeStart = close?.first ?: -1
        closeEnd = close?.second ?: -1
        contentEnd = closeStart.takeIf { it >= 0 } ?: end
        ownEnd = offset
        enter(STAGE_CONTENT)
      }
      else -> {
        closeStart = lineStart(offset)
        closeEnd = lineEnd(closeStart)
        enter(if (stage == STAGE_INDENT) STAGE_INDENT else STAGE_CLOSE)
      }
    }
  }

  private fun inTable(): Boolean {
    if (tableOpen < 0) return false
    val start = delegate.tokenStart
    return start > tableOpen && start <= tableClose
  }

  private fun onlyBlankBefore(tokenStart: Int): Boolean = buf.subSequence(lineStart(tokenStart), tokenStart).isBlank()

  private fun startsFenceLine(tokenStart: Int): Boolean {
    val lineStart = lineStart(tokenStart)
    if (!buf.subSequence(lineStart, tokenStart).isBlank()) return false
    return FENCE.matcher(buf.subSequence(lineStart, lineEnd(tokenStart))).matches()
  }

  /** The first fence line at or after [from] (a line start): its start and end offsets. */
  private fun findFence(from: Int): Pair<Int, Int>? {
    if (from >= end) return null
    val matcher = FENCE_LINES.matcher(buf)
    matcher.region(from, end)
    matcher.useAnchoringBounds(false)
    return if (matcher.find()) matcher.start() to matcher.end() else null
  }

  /**
   * The fence that closes the table the token at [offset] is in. That is the fence line [offset] is
   * the start of, or else the first one on a later line: [offset] is often the line break that ends
   * the opening fence's own line, which is not it.
   */
  private fun fenceFrom(offset: Int): Pair<Int, Int>? {
    val lineStart = lineStart(offset)
    val onFenceLine = buf.subSequence(lineStart, offset).isBlank()
    return findFence(if (onFenceLine) lineStart else minOf(lineEnd(offset) + 1, end))
  }

  private fun isTable(from: Int, to: Int): Boolean =
    buf.subSequence(from, to).lineSequence().filter { it.isNotBlank() }.all { it.trimStart().startsWith("|") }

  private fun lineStart(offset: Int): Int {
    var i = offset
    while (i > 0 && buf[i - 1] != '\n') i--
    return i
  }

  private fun lineEnd(offset: Int): Int {
    var i = offset
    while (i < end && buf[i] != '\n') i++
    return i
  }

  private fun firstNonBlank(lineStart: Int): Int {
    var i = lineStart
    while (i < end && (buf[i] == ' ' || buf[i] == '\t')) i++
    return i
  }

  companion object {
    // The state is the delegate's, below 8, with whether it is under `Examples:` above that, and above
    // that where in a block the token is.
    private const val MODE_SHIFT = 24
    private const val EXAMPLES_BIT = 1 shl 23
    private const val DELEGATE_MASK = EXAMPLES_BIT - 1
    private const val MODE_NORMAL = 0
    private const val MODE_TABLE = 5

    // The tokens of a doc-string block, in order. These double as the modes that [getState] reports.
    private const val STAGE_OPEN = 0
    private const val STAGE_CONTENT = 1
    private const val STAGE_INDENT = 2
    private const val STAGE_CLOSE = 3

    // Yadda's own pattern is /^\s*[|┆]?-{3,}/, which also takes a trailing remark such as `--- json`.
    private const val FENCE_SOURCE = """[ \t]*[|┆]?-{3,}.*"""
    private val FENCE = Pattern.compile(FENCE_SOURCE)
    private val FENCE_LINES = Pattern.compile("^$FENCE_SOURCE$", Pattern.MULTILINE)
    private val FENCE_LINE = Pattern.compile("""[|┆]?-{3,}.*""")

    // The tokens Gherkin makes of a heading word.
    private val HEADING_TOKENS = setOf(
      GherkinTokenTypes.FEATURE_KEYWORD,
      GherkinTokenTypes.RULE_KEYWORD,
      GherkinTokenTypes.BACKGROUND_KEYWORD,
      GherkinTokenTypes.SCENARIO_KEYWORD,
      GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD,
      GherkinTokenTypes.EXAMPLES_KEYWORD,
    )

    // What a tag line can be followed by that ends a table: Gherkin's English headings as well as Yadda's.
    private const val GHERKIN_HEADINGS = "(?:feature|rule|scenario outline|scenario template|scenarios?|background|examples?|where)"
  }
}
