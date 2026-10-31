package io.github.jonfrankel.playwrightyadda.steps

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * A step definition's pattern, split into the plain text a rename may change and the groups it must leave alone.
 *
 * `/^the total is ([\d.]+)$/` is the text "the total is ", the group `([\d.]+)`, and the empty text after it.
 * Renaming changes the texts and keeps the groups, which is what makes it possible to rewrite a step like
 * `Then the total is 80.00` without knowing what the group stands for. A pattern is only split when everything
 * outside its top-level groups is literal text, so that rewriting it can't change what it matches by accident:
 * a `.`, `|`, `[…]`, `\d` or `$term` outside a group is [StepPatternParse.Unsupported].
 */
class StepPattern private constructor(
  /** The literal text around the groups; one more than there are [groups]. */
  val texts: List<String>,
  /** Each top-level group as written, with the quantifier after it. */
  val groups: List<String>,
  val anchoredStart: Boolean,
  val anchoredEnd: Boolean,
) {
  /** What a match of the pattern in a step's text covers, and where each group sits in it. */
  class Match(val start: Int, val end: Int, val groups: List<IntRange>)

  private val regex: Pattern =
    Pattern.compile(
      buildString {
        if (anchoredStart) append('^')
        texts.forEachIndexed { index, text ->
          if (text.isNotEmpty()) append(Pattern.quote(text))
          groups.getOrNull(index)?.let { append("(?<g$index>").append(it).append(')') }
        }
        // `$` in a JavaScript pattern is the end of the text; in Java it also matches before a final newline.
        if (anchoredEnd) append("\\z")
      },
    )

  /** The first place [text] matches, as Yadda matches a step against a definition; null if it does not. */
  fun find(text: String): Match? {
    val matcher = regex.matcher(text)
    if (!matcher.find()) return null
    return Match(matcher.start(), matcher.end(), groups.indices.map { matcher.start("g$it") until matcher.end("g$it") })
  }

  /** The pattern as the rename dialog shows it: no anchors, the texts escaped as the pattern has them. */
  val displayed: String get() = interleave(texts.map { escape(it, regexLiteral = false) })

  /** The pattern with [newTexts] in place of its texts, anchored as it was. */
  fun source(newTexts: List<String>, regexLiteral: Boolean): String =
    (if (anchoredStart) "^" else "") + interleave(newTexts.map { escape(it, regexLiteral) }) + (if (anchoredEnd) "$" else "")

  private fun interleave(parts: List<String>): String = buildString {
    parts.forEachIndexed { index, text ->
      append(text)
      groups.getOrNull(index)?.let { append(it) }
    }
  }

  companion object {
    /** Characters that mean something in a pattern, which only a group may hold unescaped. */
    private const val SPECIAL = ".*+?^\${}[]|)("

    /** The pattern of [source], or what stops it being split. [source] is a regex source, or a string definition's value. */
    fun parse(source: String): StepPatternParse {
      var body = source
      val anchoredStart = body.startsWith("^")
      if (anchoredStart) body = body.drop(1)
      val anchoredEnd = body.endsWith("$") && body.dropLast(1).takeLastWhile { it == '\\' }.length % 2 == 0
      if (anchoredEnd) body = body.dropLast(1)

      val texts = mutableListOf<String>()
      val groups = mutableListOf<String>()
      val current = StringBuilder()
      var index = 0
      while (index < body.length) {
        val char = body[index]
        when {
          char == '\\' -> {
            val escaped = body.getOrNull(index + 1) ?: return StepPatternParse.Unsupported("it ends with a backslash")
            current.append(literalOfEscape(escaped) ?: return StepPatternParse.Unsupported("\\$escaped outside a group"))
            index += 2
          }
          char == '(' -> {
            val end = groupEnd(body, index) ?: return StepPatternParse.Unsupported("a group is never closed")
            val stop = quantifierEnd(body, end + 1)
            texts += current.toString()
            current.setLength(0)
            groups += body.substring(index, stop)
            index = stop
          }
          char in SPECIAL -> return StepPatternParse.Unsupported("'$char' outside a group")
          else -> {
            current.append(char)
            index++
          }
        }
      }
      texts += current.toString()
      return try {
        StepPatternParse.Ok(StepPattern(texts, groups, anchoredStart, anchoredEnd))
      } catch (_: PatternSyntaxException) {
        StepPatternParse.Unsupported("a group isn't a pattern Java reads")
      }
    }

    private fun literalOfEscape(char: Char): String? = when {
      char == 'n' -> "\n"
      char == 't' -> "\t"
      char == 'r' -> "\r"
      char.isLetterOrDigit() -> null
      else -> char.toString()
    }

    /** Where the group opened at [open] is closed, skipping escapes and character classes. */
    private fun groupEnd(text: String, open: Int): Int? {
      var depth = 0
      var index = open
      var inClass = false
      while (index < text.length) {
        when (text[index]) {
          '\\' -> index++
          '[' -> inClass = true
          ']' -> inClass = false
          '(' -> if (!inClass) depth++
          ')' -> if (!inClass && --depth == 0) return index
        }
        index++
      }
      return null
    }

    /** The end of the quantifier (`?`, `*`, `+`, `{n,m}`, each with an optional lazy `?`) that starts at [from]. */
    private fun quantifierEnd(text: String, from: Int): Int {
      var index = from
      when (text.getOrNull(index)) {
        '?', '*', '+' -> index++
        '{' -> {
          val close = text.indexOf('}', index)
          if (close > 0 && text.substring(index + 1, close).all { it.isDigit() || it == ',' }) index = close + 1
        }
        else -> return index
      }
      if (text.getOrNull(index) == '?') index++
      return index
    }

    /** [text] written so that it is that literal text in a pattern. */
    fun escape(text: String, regexLiteral: Boolean): String = buildString {
      for (char in text) {
        when {
          char == '\n' -> append("\\n")
          char == '\t' -> append("\\t")
          char == '\r' -> append("\\r")
          char == '\\' || char in SPECIAL -> append('\\').append(char)
          char == '/' && regexLiteral -> append("\\/")
          else -> append(char)
        }
      }
    }
  }
}

/** What reading a definition's pattern found. */
sealed interface StepPatternParse {
  class Ok(val pattern: StepPattern) : StepPatternParse

  class Unsupported(val reason: String) : StepPatternParse
}
