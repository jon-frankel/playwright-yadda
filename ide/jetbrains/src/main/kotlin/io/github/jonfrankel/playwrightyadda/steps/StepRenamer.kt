package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import io.github.jonfrankel.playwrightyadda.yadda.FeatureLines
import io.github.jonfrankel.playwrightyadda.yadda.Vocabulary
import io.github.jonfrankel.playwrightyadda.yadda.YaddaFile
import io.github.jonfrankel.playwrightyadda.yadda.YaddaGrammar
import io.github.jonfrankel.playwrightyadda.yadda.YaddaMarkdown
import io.github.jonfrankel.playwrightyadda.yadda.YaddaSettings
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Renames a step definition together with every step that runs it, in `.feature` and Markdown files.
 *
 * The Cucumber plugin renames the steps and leaves the definition as it was, because its step definitions for
 * JavaScript have no way to write themselves back. It also looks a step's definition up by the first line only,
 * so a step with a block, or with `[name]` in it, is "not renamable" to it. This does the whole job on the text:
 * it splits the definition's pattern into its literal text and its groups ([StepPattern]), asks for new text, and
 * rewrites each step by matching it against the pattern and swapping the text around the groups, so whatever the
 * groups matched (a number, a placeholder, a fenced block) is kept as written.
 *
 * Nothing is changed unless every step can be rewritten.
 */
object StepRenamer {
  /** The definition being renamed. */
  class Target(val literal: JSLiteralExpression, val pattern: StepPattern, val regexLiteral: Boolean)

  /** Why a rename can't be done, in words for the person who asked for it. */
  class Refusal(message: String) : Exception(message)

  /** One change to a file's text. */
  class Edit(val file: PsiFile, val range: TextRange, val text: String)

  /** Everything a rename changes: the definition, and the first line of each step that runs it. */
  class Plan(val edits: List<Edit>, val steps: Int)

  /** The definition at [literal], if its pattern is plain text around groups. */
  fun targetOf(literal: JSLiteralExpression): Target {
    val regexLiteral = literal.isRegExpLiteral
    val source =
      if (regexLiteral) {
        val text = literal.text
        text.substring(1, text.lastIndexOf('/').coerceAtLeast(1))
      } else {
        literal.stringValue ?: throw Refusal("The step definition isn't a string or a regular expression.")
      }
    return when (val parsed = StepPattern.parse(source)) {
      is StepPatternParse.Ok -> Target(literal, parsed.pattern, regexLiteral)
      is StepPatternParse.Unsupported ->
        throw Refusal(
          "The step definition has ${parsed.reason}, so a step can't be rewritten from it. " +
            "Rename the definition and its steps by hand.",
        )
    }
  }

  /** What is wrong with [newText] as the new pattern for [target], or null if it can be used. */
  fun check(target: Target, newText: String): String? = newPatternOf(target, newText).problem

  /** The pattern for a new text, or what is wrong with it. */
  private class NewPattern(val pattern: StepPattern?, val problem: String?)

  private fun newPatternOf(target: Target, newText: String): NewPattern {
    if (newText.isBlank()) return NewPattern(null, "The new step can't be empty.")
    val parsed =
      when (val parsed = StepPattern.parse(newText)) {
        is StepPatternParse.Ok -> parsed.pattern
        is StepPatternParse.Unsupported ->
          return NewPattern(null, "Outside the groups, write plain text (put a backslash before punctuation such as . or ?): ${parsed.reason}.")
      }
    if (parsed.groups != target.pattern.groups) {
      val groups = target.pattern.groups.joinToString(" ").ifEmpty { "there are none" }
      return NewPattern(null, "The groups of the pattern can't be changed here: $groups.")
    }
    if (parsed.texts == target.pattern.texts) return NewPattern(null, "That is the name it already has.")
    return NewPattern(parsed, null)
  }

  /** The changes to rename [target] to [newText]. */
  fun plan(target: Target, newText: String): Plan {
    val checked = newPatternOf(target, newText)
    val newPattern = checked.pattern ?: throw Refusal(checked.problem ?: "That isn't a step.")
    val project = target.literal.project
    val vocabulary = YaddaSettings.vocabularyOf(project)

    val edits = mutableListOf<Edit>()
    val seen = mutableSetOf<Pair<PsiFile, Int>>()
    for (usage in usages(target.literal)) {
      if (!seen.add(usage.file to usage.start)) continue
      edits +=
        edit(usage, target.pattern, newPattern.texts, vocabulary)
          ?: throw Refusal("The step \"${usage.step.text.substringBefore('\n')}\" at ${usage.file.name}:${usage.step.line} can't be rewritten, so nothing was renamed.")
    }
    val steps = seen.size
    edits += Edit(target.literal.containingFile, target.literal.textRange, literalText(target, newPattern.texts))
    return Plan(edits, steps)
  }

  /** Makes the changes of [plan] as one command, so one undo takes them back. */
  fun apply(project: Project, plan: Plan) {
    val files = plan.edits.map { it.file }.distinct()
    WriteCommandAction.writeCommandAction(project, *files.toTypedArray()).withName("Rename Step").run<RuntimeException> {
      val documents = PsiDocumentManager.getInstance(project)
      documents.commitAllDocuments()
      for ((file, edits) in plan.edits.groupBy { it.file }) {
        val document = documents.getDocument(file) ?: continue
        // Later offsets first, so the earlier ones are still where they were.
        for (edit in edits.sortedByDescending { it.range.startOffset }) {
          document.replaceString(edit.range.startOffset, edit.range.endOffset, edit.text)
        }
      }
      documents.commitAllDocuments()
    }
  }

  /** The new definition: its literal, with [newTexts] around the groups it had. */
  private fun literalText(target: Target, newTexts: List<String>): String {
    val source = target.pattern.source(newTexts, target.regexLiteral)
    if (target.regexLiteral) {
      val text = target.literal.text
      return "/" + source + text.substring(text.lastIndexOf('/'))
    }
    val quote = target.literal.text.first()
    return quote + source.replace("\\", "\\\\").replace(quote.toString(), "\\$quote").replace("\n", "\\n").replace("\r", "\\r") + quote
  }

  /** A step that runs the definition: where its first line starts, and what Yadda makes of it. */
  private class Usage(val file: PsiFile, val step: YaddaGrammar.Step, val start: Int)

  private fun usages(literal: JSLiteralExpression): List<Usage> {
    val result = mutableListOf<Usage>()
    for (reference in ReferencesSearch.search(literal, GlobalSearchScope.projectScope(literal.project), false).findAll()) {
      val element = reference.element
      when {
        element is GherkinStep -> {
          val file = element.containingFile as? YaddaFile ?: continue
          val step = file.yaddaStep(element) ?: continue
          result += Usage(file, step, FeatureLines.start(file, step.line) + step.column)
        }
        reference is YaddaMarkdownStepReference && element is PsiFile -> {
          val site = YaddaMarkdown.siteAt(element, reference.rangeInElement.startOffset) ?: continue
          result += Usage(element, site.step, site.range.startOffset)
        }
      }
    }
    return result
  }

  /**
   * The change that gives [usage] the text [newTexts] (none, if it has it already), or null if its text can't be matched to [pattern]. Yadda runs
   * an outline step once per row, with `[name]` replaced, so the step is tried as written and then as each row makes
   * it, and the groups found in the row's text are traced back to where they are in the step as written.
   */
  private fun edit(usage: Usage, pattern: StepPattern, newTexts: List<String>, vocabulary: Vocabulary): List<Edit>? {
    val raw = usage.step.text
    val keyword = raw.length - vocabulary.withoutStepKeyword(raw).length
    for (candidate in candidates(usage.step)) {
      for (body in listOf(candidate.text.substring(keyword), candidate.text.substring(keyword).substringBefore('\n')).distinct()) {
        val match = pattern.find(body) ?: continue
        fun toRaw(offset: Int): Int? = candidate.toRaw(keyword + offset)
        val start = toRaw(match.start) ?: continue
        val end = toRaw(match.end) ?: continue
        val groups = match.groups.map { group -> (toRaw(group.first) ?: return@map null) to (toRaw(group.last + 1) ?: return@map null) }
        if (groups.any { it == null }) continue

        val replacement = buildString {
          newTexts.forEachIndexed { index, text ->
            append(text)
            groups.getOrNull(index)?.let { (from, to) -> append(raw, from, to) }
          }
        }
        val rewritten = raw.substring(0, start) + replacement + raw.substring(end)
        // Only the first line is edited; a pattern that reaches into the block must leave the block as it was.
        if (rewritten.substringAfter('\n', "") != raw.substringAfter('\n', "")) continue

        return difference(usage, raw.substringBefore('\n'), rewritten.substringBefore('\n'))
      }
    }
    return null
  }

  /** The smallest change from [old] to [new], which are the first line of [usage]'s step; none if they are the same. */
  private fun difference(usage: Usage, old: String, new: String): List<Edit> {
    if (old == new) return emptyList()
    val prefix = old.commonPrefixWith(new).length
    val suffix = old.drop(prefix).commonSuffixWith(new.drop(prefix)).length
    return listOf(
      Edit(
        usage.file,
        TextRange(usage.start + prefix, usage.start + old.length - suffix),
        new.substring(prefix, new.length - suffix),
      ),
    )
  }

  /** A text of the step to match, and how an offset in it is an offset in the step as written. */
  private class Candidate(val text: String, private val segments: List<Segment>?) {
    fun toRaw(offset: Int): Int? {
      if (segments == null) return offset
      val segment = segments.firstOrNull { offset in it.expanded.first..it.expanded.last + 1 } ?: return null
      if (!segment.replaced) return segment.raw.first + (offset - segment.expanded.first)
      return when (offset) {
        segment.expanded.first -> segment.raw.first
        segment.expanded.last + 1 -> segment.raw.last + 1
        else -> null
      }
    }
  }

  private class Segment(val expanded: IntRange, val raw: IntRange, val replaced: Boolean)

  private val PLACEHOLDER = Regex("""\[[^\[\]\n]*]""")

  private fun candidates(step: YaddaGrammar.Step): List<Candidate> {
    val raw = step.text
    val rows = step.expandedRows(raw)
    val result = mutableListOf(Candidate(raw, null))
    val placeholders = PLACEHOLDER.findAll(raw).toList()
    rows.forEachIndexed { index, expandedText ->
      if (expandedText == raw) return@forEachIndexed
      val segments = mutableListOf<Segment>()
      val built = StringBuilder()
      var rawFrom = 0
      fun plain(until: Int) {
        if (until > rawFrom) {
          segments += Segment(built.length until built.length + (until - rawFrom), rawFrom until until, false)
          built.append(raw, rawFrom, until)
        }
      }
      for (placeholder in placeholders) {
        plain(placeholder.range.first)
        val value = step.expandedRows(placeholder.value).getOrNull(index) ?: placeholder.value
        segments += Segment(built.length until built.length + value.length, placeholder.range, value != placeholder.value)
        built.append(value)
        rawFrom = placeholder.range.last + 1
      }
      plain(raw.length)
      if (built.toString() == expandedText) result += Candidate(expandedText, segments)
    }
    return result.distinctBy { it.text }
  }
}
