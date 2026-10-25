package io.github.jonfrankel.playwrightyadda.yadda

import com.google.gson.JsonParser
import java.util.regex.Pattern

/**
 * The words one of Yadda's localisations (`yadda/lib/localisation`) reads as `Feature:`, `Scenario:` and so
 * on, and as the keyword in front of a step. Yadda writes each as the source of a regex, and so does
 * `vocabularies.json`, which `scripts/generate-vocabularies.mjs` makes from the installed yadda.
 *
 * The headings are embedded in Yadda's own pattern (`^\s*WORD:\s*(.*)`) as written, with no group around
 * them, so a word list Yadda writes as `a|b` splits the pattern in the same place here as it does there.
 */
class Vocabulary private constructor(
  val name: String,
  private val feature: String,
  private val rule: String?,
  private val scenario: String,
  private val background: String,
  private val examples: String,
  private val given: String,
  private val `when`: String,
  private val then: String,
) {
  /** The constructs a line can start. Yadda tries them in this order. */
  enum class Heading { FEATURE, RULE, SCENARIO, BACKGROUND, EXAMPLES }

  /** A heading line as Yadda matches it; [title] is what follows the colon. */
  class Match(val heading: Heading, val title: String)

  private val headings: List<Pair<Heading, Regex>> = listOfNotNull(
    Heading.FEATURE to headingRegex(feature),
    rule?.let { Heading.RULE to headingRegex(it) },
    Heading.SCENARIO to headingRegex(scenario),
    Heading.BACKGROUND to headingRegex(background),
    Heading.EXAMPLES to Regex("""^\s*$examples:""", RegexOption.IGNORE_CASE),
  )

  private fun headingRegex(word: String) = Regex("""^\s*$word:\s*(.*)""", RegexOption.IGNORE_CASE)

  /** What Yadda makes of [line] if it starts a construct, or null. [line] is a whole line, or the text of a Markdown heading. */
  fun match(line: String): Match? {
    for ((heading, regex) in headings) {
      val found = regex.find(line) ?: continue
      // The title is the pattern's last group, which is Yadda's `(.*)`; a word list with groups of its own has them before it.
      val title = if (heading == Heading.EXAMPLES) "" else found.groupValues.last()
      return Match(heading, title)
    }
    return null
  }

  /** Whether [line] starts the construct [heading]. */
  fun isHeading(line: String, heading: Heading): Boolean = headings.firstOrNull { it.first == heading }?.second?.containsMatchIn(line) == true

  /**
   * The headings at the very start of a line, up to but not including the colon, each as the pattern of
   * its word alone: what the lexer cuts a keyword token from. A group is put round a word list so the
   * colon that follows goes with all of it.
   */
  private val keywordPatterns: List<Pair<Heading, Pattern>> = listOfNotNull(
    Heading.FEATURE to feature,
    rule?.let { Heading.RULE to it },
    Heading.SCENARIO to scenario,
    Heading.BACKGROUND to background,
    Heading.EXAMPLES to examples,
  ).map { (heading, word) -> heading to Pattern.compile("^(?:$word)(?=:)", Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE) }

  /** The heading keyword (without its colon) that [text] starts, and its length. */
  fun keywordAt(text: CharSequence): Pair<Heading, Int>? {
    for ((heading, pattern) in keywordPatterns) {
      val matcher = pattern.matcher(text)
      if (matcher.find()) return heading to matcher.end()
    }
    return null
  }

  /** Whether [text] has the word for `Feature:` anywhere in it, which is cheap to ask before parsing the whole of it. */
  val mentionsFeature: Regex = Regex("(?:$feature):", RegexOption.IGNORE_CASE)

  /** The start of any heading line, for what comes after a tag line to say whether it ends a table. */
  val headingWords: String = listOfNotNull(feature, rule, scenario, background, examples).joinToString("|") { "(?:$it)" }

  private val stepKeyword = Regex("""^\s*((?:$given)|(?:$`when`)|(?:$then))\s+""")
  // A library's patterns are compiled without a case flag, so the case of a keyword is what its word list says.
  private val givenOnly = Regex("(?:$given)")
  private val whenOnly = Regex("(?:$`when`)")
  private val thenOnly = Regex("(?:$then)")

  /** A step line split into the keyword Yadda's library puts before a definition, and the rest. */
  class StepKeyword(val keyword: String, val rest: String, val method: String?)

  /**
   * The keyword [text] starts with, if it has one. [StepKeyword.method] is the library method it belongs
   * to (`given`, `when` or `then`), or null for a word every one of them accepts, like `And`.
   */
  fun stepKeyword(text: String): StepKeyword? {
    val found = stepKeyword.find(text) ?: return null
    val keyword = found.groupValues[1]
    val methods = listOf("given" to givenOnly, "when" to whenOnly, "then" to thenOnly).filter { it.second.matches(keyword) }
    val method = if (methods.size == 3) null else methods.firstOrNull()?.first
    return StepKeyword(keyword, text.substring(found.range.last + 1), method)
  }

  /** [text] without the keyword at its start, as a definition's pattern is written. */
  fun withoutStepKeyword(text: String): String = stepKeyword(text)?.rest ?: text

  override fun toString(): String = name

  companion object {
    /** Every localisation Yadda has, English first. */
    val all: List<Vocabulary> by lazy { load() }

    val ENGLISH: Vocabulary get() = byName("English")!!

    fun byName(name: String?): Vocabulary? = all.firstOrNull { it.name == name }

    private fun load(): List<Vocabulary> {
      val text = Vocabulary::class.java.getResourceAsStream("/yadda/vocabularies.json")!!.reader().use { it.readText() }
      val all = JsonParser.parseString(text).asJsonArray.mapNotNull { element ->
        val json = element.asJsonObject
        fun word(key: String): String? = json[key]?.takeIf { !it.isJsonNull }?.asString
        // A word list Java can't compile is left out; the file only holds lists JavaScript can.
        runCatching {
          Vocabulary(
            json["name"].asString,
            word("feature")!!,
            word("rule"),
            word("scenario")!!,
            word("background")!!,
            word("examples")!!,
            word("given")!!,
            word("when")!!,
            word("then")!!,
          )
        }.getOrNull()
      }
      return all.sortedBy { if (it.name == "English") "" else it.name }
    }
  }
}
