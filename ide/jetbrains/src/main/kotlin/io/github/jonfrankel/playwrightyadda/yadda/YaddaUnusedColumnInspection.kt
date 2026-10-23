package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

/**
 * An examples column that nothing in the scenario refers to. Gherkin's own check looks for
 * `<name>`, which is not how Yadda spells it, so it would grey out every column; this one looks
 * for `[name]`, `[ name ]`, and the `.index`, `.start.line` and `.start.column` Yadda adds.
 */
class YaddaUnusedColumnInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    object : PsiElementVisitor() {
      override fun visitElement(block: PsiElement) {
        if (block !is GherkinExamplesBlock || block.containingFile !is YaddaFile) return
        val header = block.table?.headerRow ?: return
        val scenario = PsiTreeUtil.getParentOfType(block, GherkinStepsHolder::class.java) ?: return
        val referring = textOutsideExamples(scenario)
        for (cell in header.psiCells) {
          val name = cell.text.trim()
          if (name.isEmpty() || isReferredTo(name, referring)) continue
          holder.registerProblem(cell, "Nothing in the scenario uses this column", ProblemHighlightType.LIKE_UNUSED_SYMBOL)
        }
      }
    }

  private fun textOutsideExamples(scenario: GherkinStepsHolder): String {
    val text = scenario.text
    val examples = PsiTreeUtil.findChildrenOfType(scenario, GherkinExamplesBlock::class.java)
      .map { TextRange(it.textRange.startOffset - scenario.textRange.startOffset, it.textRange.endOffset - scenario.textRange.startOffset) }
    return buildString {
      var from = 0
      for (range in examples.sortedBy { it.startOffset }) {
        append(text, from, range.startOffset)
        from = range.endOffset
      }
      append(text, from, text.length)
    }
  }

  private fun isReferredTo(name: String, text: String): Boolean =
    Regex("""\[\s*${Regex.escape(name)}(?:\.index|\.start\.line|\.start\.column)?\s*]""").containsMatchIn(text)
}
