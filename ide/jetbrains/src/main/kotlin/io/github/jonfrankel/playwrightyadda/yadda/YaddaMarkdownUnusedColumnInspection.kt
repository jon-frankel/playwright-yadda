package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 * The same check as [YaddaUnusedColumnInspection], for the examples table of a `.md` feature: a column
 * that neither the scenario's title nor any of its steps refers to as `[name]`.
 */
class YaddaMarkdownUnusedColumnInspection : LocalInspectionTool() {
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (!YaddaMarkdown.isFeature(file)) return null
    val problems = mutableListOf<ProblemDescriptor>()
    for (table in YaddaMarkdown.analysis(file).examples) {
      val referring = table.scenario.title + "\n" + table.scenario.steps.joinToString("\n") { it.text }
      val cells = cellsOf(file, table.headerLine)
      for (name in table.columns) {
        if (name.isEmpty() || isReferredTo(name, referring)) continue
        val range = cells.firstOrNull { file.text.substring(it.startOffset, it.endOffset) == name } ?: continue
        problems += manager.createProblemDescriptor(
          file,
          range,
          "Nothing in the scenario uses this column",
          ProblemHighlightType.LIKE_UNUSED_SYMBOL,
          isOnTheFly,
        )
      }
    }
    return problems.toTypedArray().takeIf { it.isNotEmpty() }
  }

  /** The cells of a table row: what is between its pipes, without the padding. */
  private fun cellsOf(file: PsiFile, line: Int): List<TextRange> {
    val row = FeatureLines.range(file, line)
    val text = file.text.substring(row.startOffset, row.endOffset)
    val cells = mutableListOf<TextRange>()
    var from = 0
    for (cell in text.split('|')) {
      val name = cell.trim()
      if (name.isNotEmpty()) {
        val start = row.startOffset + from + cell.indexOf(name)
        cells += TextRange(start, start + name.length)
      }
      from += cell.length + 1
    }
    return cells
  }

  private fun isReferredTo(name: String, text: String): Boolean =
    Regex("""\[\s*${Regex.escape(name)}(?:\.index|\.start\.line|\.start\.column)?\s*]""").containsMatchIn(text)
}
