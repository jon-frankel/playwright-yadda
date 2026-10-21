package io.github.jonfrankel.playwrightyadda.libraries

import com.intellij.openapi.util.TextRange

/**
 * The library names written in `@Libraries=a, b` lines of a feature file, with where they are.
 *
 * Read per line like [FeatureTags], because the IDE's Gherkin lexer ends a tag at its first space.
 */
object LibraryNames {
  /** [name] is lower-cased like registry keys; [nameRange] is the trimmed name, [entryRange] the whole comma-separated entry. */
  class Occurrence(val name: String, val nameRange: TextRange, val entryRange: TextRange)

  private val LIBRARIES_LINE = Regex("""^\s*@([^=]*)=(.*)$""")

  fun parse(text: CharSequence): List<Occurrence> {
    val result = mutableListOf<Occurrence>()
    var lineStart = 0
    for (rawLine in text.split('\n')) {
      val line = rawLine.trimEnd('\r')
      LIBRARIES_LINE.find(line)?.takeIf { it.groupValues[1].trim().equals(FeatureTags.DEFAULT_ANNOTATION_KEY, true) }?.let { match ->
        var entryStart = lineStart + match.groups[2]!!.range.first
        for (entry in match.groupValues[2].split(',')) {
          val name = entry.trim()
          if (name.isNotEmpty()) {
            val nameStart = entryStart + entry.indexOf(name)
            result += Occurrence(
              name.lowercase(),
              TextRange(nameStart, nameStart + name.length),
              TextRange(entryStart, entryStart + entry.length),
            )
          }
          entryStart += entry.length + 1
        }
      }
      lineStart += rawLine.length + 1
    }
    return result
  }

  /** The name under [offset]: the caret anywhere in its entry, including at its edges, counts. */
  fun nameAt(text: CharSequence, offset: Int): String? =
    parse(text).firstOrNull { offset in it.entryRange.startOffset..it.entryRange.endOffset }?.name
}
