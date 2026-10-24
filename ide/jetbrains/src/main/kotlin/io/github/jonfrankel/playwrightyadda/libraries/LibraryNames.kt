package io.github.jonfrankel.playwrightyadda.libraries

import com.intellij.openapi.util.TextRange
import io.github.jonfrankel.playwrightyadda.yadda.YaddaGrammar

/**
 * The library names written in `@Libraries=a, b` lines of a feature file, with where they are.
 *
 * Read per line like [FeatureTags], because the IDE's Gherkin lexer ends a tag at its first space.
 */
object LibraryNames {
  /** [name] is lower-cased like registry keys; [nameRange] is the trimmed name, [entryRange] the whole comma-separated entry. */
  class Occurrence(val name: String, val nameRange: TextRange, val entryRange: TextRange)

  private val LIBRARIES_LINE = Regex("""^\s*@([^=]*)=(.*)$""")

  /** Every name in [text]; [annotationLines] limits it to those 1-based lines, for a format where not every line that looks like an annotation is one. */
  fun parse(text: CharSequence, annotationLines: Set<Int>? = null): List<Occurrence> {
    val result = mutableListOf<Occurrence>()
    var lineStart = 0
    for ((index, rawLine) in text.split('\n').withIndex()) {
      val line = rawLine.trimEnd('\r')
      val isAnnotation = annotationLines == null || index + 1 in annotationLines
      LIBRARIES_LINE.find(line)?.takeIf { isAnnotation && it.groupValues[1].trim().equals(FeatureTags.DEFAULT_ANNOTATION_KEY, true) }?.let { match ->
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

  /** The names in a Markdown feature, where a fence or a comment can hold a line that looks like `@Libraries=…`. */
  fun parseMarkdown(text: CharSequence): List<Occurrence> =
    parse(
      text,
      YaddaGrammar.analyzeMarkdown(text.toString()).markers
        .filter { it.kind == YaddaGrammar.MarkerKind.ANNOTATION }
        .mapTo(HashSet()) { it.line },
    )

  /** The name under [offset]: the caret anywhere in its entry, including at its edges, counts. */
  fun nameAt(text: CharSequence, offset: Int, markdown: Boolean = false): String? =
    (if (markdown) parseMarkdown(text) else parse(text)).firstOrNull { offset in it.entryRange.startOffset..it.entryRange.endOffset }?.name
}
