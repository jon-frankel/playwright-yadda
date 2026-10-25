package io.github.jonfrankel.playwrightyadda.libraries

import io.github.jonfrankel.playwrightyadda.yadda.Vocabulary
import io.github.jonfrankel.playwrightyadda.yadda.YaddaGrammar

/**
 * The `@Libraries=…` annotations of a feature file, read the way Yadda reads them.
 *
 * WebStorm's Gherkin parser ends a tag at the first space, so `@Libraries=a, b` is a tag plus loose
 * text. Yadda instead treats every line starting with `@` as one annotation (`@key` or `@key=value`),
 * so this reads lines, not PSI. Mirrors `node_modules/yadda/lib/parsers`, with the headings of the [Vocabulary] it is given.
 */
class FeatureTags private constructor(
  private val lineStarts: IntArray,
  private val feature: Map<String, String?>,
  private val headers: List<Header>,
) {
  /** [scenario] is null for headers that are not a plain scenario, e.g. `Background:` or `Rule:`. */
  private class Header(val line: Int, val scenario: Map<String, String?>?)

  /** The libraries a scenario asks for. */
  class Libraries(val feature: List<String>, val scenario: List<String>)

  fun lineOf(offset: Int): Int {
    val index = lineStarts.binarySearch(offset)
    return if (index >= 0) index else -index - 2
  }

  /**
   * The libraries requested for the scenario containing [line], or null if that line is not in a plain
   * scenario (Background steps run in every scenario, and Yadda does not run scenarios inside a `Rule:`),
   * in which case the caller should not assume anything.
   */
  fun librariesAt(line: Int, annotationKey: String = DEFAULT_ANNOTATION_KEY): Libraries? {
    val header = headers.lastOrNull { it.line <= line } ?: return null
    val scenario = header.scenario ?: return null
    return Libraries(namesIn(feature, annotationKey), namesIn(scenario, annotationKey))
  }

  companion object {
    const val DEFAULT_ANNOTATION_KEY = "libraries"

    private val SIMPLE_ANNOTATION = Regex("""^\s*@([^=]*)$""")
    private val VALUE_ANNOTATION = Regex("""^\s*@([^=]*)=(.*)$""")

    /** One line that starts a construct or is an annotation, 0-based. */
    private class Item(val line: Int, val kind: YaddaGrammar.MarkerKind, val key: String = "", val value: String? = null)

    fun parse(text: CharSequence, vocabulary: Vocabulary = Vocabulary.ENGLISH): FeatureTags {
      val lines = text.split('\n')
      val items = mutableListOf<Item>()
      lines.forEachIndexed { index, line ->
        val annotation = parseAnnotation(line)
        val heading = vocabulary.match(line)?.heading
        val kind = when {
          annotation != null -> YaddaGrammar.MarkerKind.ANNOTATION
          heading == Vocabulary.Heading.FEATURE -> YaddaGrammar.MarkerKind.FEATURE
          heading == Vocabulary.Heading.RULE -> YaddaGrammar.MarkerKind.RULE
          heading == Vocabulary.Heading.BACKGROUND -> YaddaGrammar.MarkerKind.BACKGROUND
          heading == Vocabulary.Heading.EXAMPLES -> YaddaGrammar.MarkerKind.EXAMPLES
          heading == Vocabulary.Heading.SCENARIO -> YaddaGrammar.MarkerKind.SCENARIO
          else -> return@forEachIndexed
        }
        items += Item(index, kind, annotation?.first.orEmpty(), annotation?.second)
      }
      return assemble(lines, items)
    }

    /**
     * The same for a Markdown feature, where a keyword is a heading and a fence, a comment or a table hides
     * what looks like one, so what is a heading is Yadda's own reading of it.
     */
    fun parseMarkdown(text: CharSequence, vocabulary: Vocabulary = Vocabulary.ENGLISH): FeatureTags {
      val items = YaddaGrammar.analyzeMarkdown(text.toString(), vocabulary).markers.mapNotNull { marker ->
        if (marker.kind == YaddaGrammar.MarkerKind.ANNOTATION) {
          // As in parseAnnotation: a key with a space in it is not an annotation Yadda would accept.
          if (marker.key.isEmpty() || marker.key.any(Char::isWhitespace)) return@mapNotNull null
          Item(marker.line - 1, marker.kind, marker.key.lowercase(), marker.value)
        } else {
          Item(marker.line - 1, marker.kind)
        }
      }
      return assemble(text.split('\n'), items)
    }

    private fun assemble(lines: List<String>, items: List<Item>): FeatureTags {
      val lineStarts = IntArray(lines.size)
      var start = 0
      lines.forEachIndexed { index, line ->
        lineStarts[index] = start
        start += line.length + 1
      }

      var pending = LinkedHashMap<String, String?>()
      var featureTags: Map<String, String?> = emptyMap()
      var inRule = false
      val headers = mutableListOf<Header>()

      for (item in items) {
        when (item.kind) {
          YaddaGrammar.MarkerKind.ANNOTATION -> {
            // The last occurrence of a key wins, as in Yadda's Annotations.stash().
            pending[item.key] = item.value
            continue
          }
          YaddaGrammar.MarkerKind.FEATURE -> {
            featureTags = pending
            headers += Header(item.line, null)
          }
          // Everything under a Rule is skipped by playwright-yadda, so it never has a known scenario.
          YaddaGrammar.MarkerKind.RULE -> {
            inRule = true
            headers += Header(item.line, null)
          }
          YaddaGrammar.MarkerKind.BACKGROUND, YaddaGrammar.MarkerKind.EXAMPLES -> headers += Header(item.line, null)
          YaddaGrammar.MarkerKind.SCENARIO -> headers += Header(item.line, if (inRule) null else pending)
        }
        pending = LinkedHashMap()
      }
      return FeatureTags(lineStarts, featureTags, headers)
    }

    /** `@key` gives (key, null); `@key=value` gives (key, value). Keys are case-insensitive. */
    private fun parseAnnotation(line: String): Pair<String, String?>? {
      VALUE_ANNOTATION.find(line)?.let { match ->
        val key = match.groupValues[1].trim()
        return if (key.isEmpty() || key.any(Char::isWhitespace)) null
        else key.lowercase() to match.groupValues[2].trim()
      }
      SIMPLE_ANNOTATION.find(line)?.let { match ->
        val key = match.groupValues[1].trim()
        return if (key.isEmpty() || key.any(Char::isWhitespace)) null else key.lowercase() to null
      }
      return null
    }

    private fun namesIn(tags: Map<String, String?>, annotationKey: String): List<String> =
      tags[annotationKey.trim().lowercase()]?.let(::splitNames) ?: emptyList()
  }
}

/** Comma-separated library names, normalised like `extractLibraryNames` in `src/annotations.ts`. */
internal fun splitNames(value: String): List<String> =
  value.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

/**
 * The library keys a scenario runs with. Mirrors `resolveLibraryNames` in `src/annotations.ts`:
 * the defaults, plus the feature's and scenario's `@Libraries`, except that with `override`
 * inheritance a scenario that names libraries replaces the feature's.
 */
fun enabledLibraries(
  defaults: List<String>,
  inheritance: String,
  feature: List<String>,
  scenario: List<String>,
): Set<String> {
  val result = LinkedHashSet<String>()
  defaults.map { it.trim().lowercase() }.filterTo(result) { it.isNotEmpty() }
  if (inheritance == "override" && scenario.isNotEmpty()) {
    result += scenario
  } else {
    result += feature
    result += scenario
  }
  return result
}
