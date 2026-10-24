package io.github.jonfrankel.playwrightyadda.yadda

/**
 * The step definition a step needs, written the way this library's steps are: `library.given(/^…$/, …)`.
 *
 * Yadda's English library prefixes a `given`, `when` or `then` definition with its own keyword, and the
 * prefix also accepts `And` and `But`, so a step's keyword picks the method and `And` or `But` take the
 * method of the step before. A step with no keyword has no such method: Yadda matches it only against a
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
  /** The statement that defines the step on [receiver]; [typed] adds `: string` to the parameters, for TypeScript. */
  fun render(receiver: String, typed: Boolean): String {
    val signature = if (isPlain) "\"$pattern\"" else "/^$pattern$/"
    val parameters = parameters.joinToString(", ") { if (typed) "$it: string" else it }
    return "$receiver.$method($signature, async function ($parameters) {\n  \n});"
  }

  companion object {
    private val KEYWORD = Regex("""^(Given|With|Except|When|If|Then|Expect|And|But)\s+(\S.*)$""", RegexOption.IGNORE_CASE)
    private val PLACEHOLDER = Regex("""\[([^\[\]]+)]""")
    private val RESERVED = setOf(
      "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else", "enum", "export", "extends",
      "false", "finally", "for", "function", "if", "import", "in", "instanceof", "let", "new", "null", "return", "super", "switch",
      "this", "throw", "true", "try", "typeof", "var", "void", "while", "with", "yield", "await", "async", "static",
    )
    private val REGEX_SPECIAL = "\\^$.|?*+()[]{}/"

    /** The skeleton for [step], or null when it has no keyword to take a method from. */
    fun of(step: YaddaGrammar.Step): StepSkeleton? {
      val first = step.text.substringBefore('\n')
      val match = KEYWORD.find(first) ?: return null
      val keyword = match.groupValues[1].lowercase()
      val method = methodOf(keyword) ?: inherited(step)
      val text = match.groupValues[2].trimEnd()

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

    private fun methodOf(keyword: String): String? =
      when (keyword) {
        "given", "with", "except" -> "given"
        "when", "if" -> "when"
        "then", "expect" -> "then"
        else -> null
      }

    /** `And`/`But` run whichever of `given`, `when` and `then` the step before them did; before any, `given`. */
    private fun inherited(step: YaddaGrammar.Step): String {
      val before = step.siblings.takeWhile { it !== step }
      for (previous in before.asReversed()) {
        val match = KEYWORD.find(previous.text.substringBefore('\n')) ?: continue
        methodOf(match.groupValues[1].lowercase())?.let { return it }
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
