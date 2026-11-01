package io.github.jonfrankel.playwrightyadda.yadda

/**
 * The step definition a step needs, written the way this library's steps are: `library.given(/^…$/, …)`.
 *
 * Yadda's library prefixes a `given`, `when` or `then` definition with the keywords of its language, and the
 * prefix also accepts a word like `And` or `But` for all three, so a step's keyword picks the method and
 * such a word takes the method of the step before. `given`, `when` and `then` are the method names in every
 * language, so the skeleton is the same whichever the project's features are written in. A step with no keyword has no such method: Yadda matches it only against a
 * `define`, which the IDE doesn't read as a step definition (it would still show the step as undefined),
 * so there is no skeleton for it.
 */
class StepSkeleton private constructor(
  val method: String,
  /** What goes between the slashes of the regex, or the text of a plain string when [isPlain]. */
  val pattern: String,
  /** The parameters of the function, one per capture group, as `name`. */
  val parameters: List<String>,
  val isPlain: Boolean,
) {
  /**
   * The statement that defines the step on [receiver]; [typed] adds `: string` to the parameters, for TypeScript.
   * A [fixtures] library passes the scenario's fixtures as the first argument and wants them destructured, so
   * its step takes an empty pattern ahead of the captures, for the author to fill in with what the step uses.
   */
  fun render(receiver: String, typed: Boolean, fixtures: Boolean = false): String {
    val signature = if (isPlain) "\"$pattern\"" else "/^$pattern$/"
    val captured = parameters.map { if (typed) "$it: string" else it }
    if (fixtures) return "$receiver.$method($signature, async (${(listOf("{}") + captured).joinToString(", ")}) => {\n  \n});"
    return "$receiver.$method($signature, async function (${captured.joinToString(", ")}) {\n  \n});"
  }

  companion object {
    private val PLACEHOLDER = Regex("""\[([^\[\]]+)]""")
    private val RESERVED = setOf(
      "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "enum", "export", "extends",
      "false", "finally", "for", "function", "if", "import", "in", "instanceof", "let", "new", "null", "return", "super", "switch",
      "this", "throw", "true", "try", "typeof", "var", "void", "while", "with", "yield", "await", "async", "static",
    )
    private val REGEX_SPECIAL = "\\^$.|?*+()[]{}/"

    /** The skeleton for [step], read in the language of [vocabulary], or null when it has no keyword to take a method from. */
    fun of(step: YaddaGrammar.Step, vocabulary: Vocabulary = Vocabulary.ENGLISH): StepSkeleton? {
      val first = step.text.substringBefore('\n')
      val keyword = vocabulary.stepKeyword(first)?.takeIf { it.rest.isNotBlank() } ?: return null
      val method = keyword.method ?: inherited(step, vocabulary)
      val text = keyword.rest.trimEnd()

      val parameters = mutableListOf<String>()
      val pattern = StringBuilder()
      var at = 0
      for (placeholder in PLACEHOLDER.findAll(text)) {
        // `[name]` is a placeholder only where the scenario's examples fill it in.
        if (step.expanded(placeholder.value) == placeholder.value) continue
        pattern.append(escape(text.substring(at, placeholder.range.first)))
        pattern.append("(.+)")
        parameters += parameterName(placeholder.groupValues[1], parameters)
        at = placeholder.range.last + 1
      }
      pattern.append(escape(text.substring(at)))

      val block = step.block != null
      if (block) {
        pattern.append("""\n([\s\S]+)""")
        parameters += uniqueName("block", parameters)
      }

      val plain = parameters.isEmpty() && text.none { it in REGEX_SPECIAL || it == '"' } && text.none { it.isISOControl() }
      return StepSkeleton(method, if (plain) text else pattern.toString(), parameters, plain)
    }

    /** `And`/`But` run whichever of `given`, `when` and `then` the step before them did; before any, `given`. */
    private fun inherited(step: YaddaGrammar.Step, vocabulary: Vocabulary): String {
      val before = step.siblings.takeWhile { it !== step }
      for (previous in before.asReversed()) {
        vocabulary.stepKeyword(previous.text.substringBefore('\n'))?.method?.let { return it }
      }
      return "given"
    }

    private fun escape(text: String): String = buildString {
      for (c in text) {
        if (c in REGEX_SPECIAL) append('\\')
        append(c)
      }
    }

    /** `[price]` becomes `price`, `[row.index]` becomes `rowIndex`. */
    private fun parameterName(placeholder: String, taken: List<String>): String {
      val words = placeholder.split(Regex("""[^A-Za-z0-9]+""")).filter { it.isNotEmpty() }
      val joined = words.mapIndexed { i, word -> if (i == 0) word.replaceFirstChar { it.lowercase() } else word.replaceFirstChar { it.uppercase() } }.joinToString("")
      val name = if (joined.isEmpty() || joined[0].isDigit() || joined in RESERVED) "value${joined.replaceFirstChar { it.uppercase() }}" else joined
      return uniqueName(name, taken)
    }

    private fun uniqueName(name: String, taken: List<String>): String {
      if (name !in taken) return name
      var n = 2
      while ("$name$n" in taken) n++
      return "$name$n"
    }
  }
}
