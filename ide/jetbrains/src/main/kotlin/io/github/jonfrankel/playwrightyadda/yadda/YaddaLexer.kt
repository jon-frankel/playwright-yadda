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
 * Which of the two a block is depends on the rows after the opening fence, so those are read ahead;
 * editing the closing fence changes how the opening one is coloured only once the file is re-lexed.
 *
 * The editor restarts lexing from any token with the state it reported, so [getState] says where in
 * a block a token is, and [start] rebuilds the block's bounds from the text.
 */
class YaddaLexer(private val delegate: Lexer) : LexerBase() {
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

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    buf = buffer
    end = endOffset
    own = false
    tableOpen = -1
    tableClose = -1

    val mode = initialState ushr MODE_SHIFT
    val delegateState = initialState and DELEGATE_MASK
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
    return (if (inTable()) MODE_TABLE shl MODE_SHIFT else 0) or delegate.state
  }

  override fun getTokenType(): IElementType? {
    if (own) return ownType
    val type = delegate.tokenType
    if (type == GherkinTokenTypes.TEXT && FENCE_LINE.matcher(delegate.tokenText).matches()) {
      return GherkinTokenTypes.COMMENT
    }
    return type
  }

  override fun getTokenStart(): Int = if (own) ownStart else delegate.tokenStart

  override fun getTokenEnd(): Int = if (own) ownEnd else delegate.tokenEnd

  override fun getBufferSequence(): CharSequence = buf

  override fun getBufferEnd(): Int = end

  override fun advance() {
    if (own) {
      enter(stage + 1)
      return
    }
    if (delegate.tokenStart >= tableClose && tableClose >= 0) {
      tableOpen = -1
      tableClose = -1
    }
    delegate.advance()
    settle()
  }

  /** Looks at the delegate's current token and takes over if it opens a block that is a doc string. */
  private fun settle() {
    if (own || delegate.tokenType == null) return
    val type = delegate.tokenType
    if (type != GherkinTokenTypes.TEXT && type != GherkinTokenTypes.PIPE) return
    val start = delegate.tokenStart
    if (!startsFenceLine(start)) return
    if (start == tableClose) return

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
    // The state is the delegate's, below 8, with where in a block the token is above it.
    private const val MODE_SHIFT = 24
    private const val DELEGATE_MASK = (1 shl MODE_SHIFT) - 1
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
  }
}
