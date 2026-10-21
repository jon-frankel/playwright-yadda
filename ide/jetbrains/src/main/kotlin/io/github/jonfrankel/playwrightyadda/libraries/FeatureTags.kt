package io.github.jonfrankel.playwrightyadda.libraries

/**
 * The `@Libraries=…` annotations of a feature file, read the way Yadda reads them.
 *
 * WebStorm's Gherkin parser ends a tag at the first space, so `@Libraries=a, b` is a tag plus loose
 * text. Yadda instead treats every line starting with `@` as one annotation (`@key` or `@key=value`),
 * so this reads lines, not PSI. Mirrors `node_modules/yadda/lib/parsers` (English keywords).
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

    private val FLAGS = setOf(RegexOption.IGNORE_CASE)
    private val SIMPLE_ANNOTATION = Regex("""^\s*@([^=]*)$""")
    private val VALUE_ANNOTATION = Regex("""^\s*@([^=]*)=(.*)$""")
    private val FEATURE = Regex("""^\s*feature:""", FLAGS)
    private val RULE = Regex("""^\s*rule:""", FLAGS)
    private val BACKGROUND = Regex("""^\s*background:""", FLAGS)
    private val SCENARIO = Regex("""^\s*(?:scenario|scenario outline):""", FLAGS)
    private val EXAMPLES = Regex("""^\s*(?:examples|where):""", FLAGS)

    fun parse(text: CharSequence): FeatureTags {
      val lines = text.split('\n')
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

      lines.forEachIndexed { index, line ->
        val annotation = parseAnnotation(line)
        if (annotation != null) {
          // The last occurrence of a key wins, as in Yadda's Annotations.stash().
          pending[annotation.first] = annotation.second
          return@forEachIndexed
        }
        when {
          FEATURE.containsMatchIn(line) -> {
            featureTags = pending
            headers += Header(index, null)
          }
          // Everything under a Rule is skipped by playwright-yadda, so it never has a known scenario.
          RULE.containsMatchIn(line) -> {
            inRule = true
            headers += Header(index, null)
          }
          BACKGROUND.containsMatchIn(line) || EXAMPLES.containsMatchIn(line) ->
            headers += Header(index, null)
          SCENARIO.containsMatchIn(line) -> headers += Header(index, if (inRule) null else pending)
          else -> return@forEachIndexed
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
