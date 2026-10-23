package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.util.TextRange
import java.util.regex.Pattern

/**
 * The text of a Yadda `---` block as the editor holds it: the opening fence line, the content, and
 * the closing fence if there is one. Offsets are into the text it was made from, which is the host
 * element's, so the first character is the first dash.
 */
class FencedBlock private constructor(
  /** What follows the dashes on the opening line, such as `json` for `--- json`. */
  val remark: String,
  /** The lines between the fences, from the start of the first to the start of the closing fence's line. */
  val content: TextRange,
) {
  companion object {
    // Yadda's own pattern is /^\s*[|┆]?-{3,}/, and everything after the dashes is a remark.
    private val FENCE = Pattern.compile("""[|┆]?-{3,}(.*)""")

    fun of(text: String): FencedBlock? {
      val open = FENCE.matcher(text.lineSequence().first().trim())
      if (!open.matches()) return null
      val openEnd = text.indexOf('\n')
      if (openEnd < 0) return FencedBlock(open.group(1).trim(), TextRange.EMPTY_RANGE)

      val lastStart = text.lastIndexOf('\n') + 1
      val closed = lastStart > openEnd && FENCE.matcher(text.substring(lastStart).trim()).matches()
      val end = if (closed) lastStart else text.length
      return FencedBlock(open.group(1).trim(), TextRange(openEnd + 1, end))
    }
  }
}
