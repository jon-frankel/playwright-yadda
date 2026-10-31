package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile

/**
 * Gherkin attaches a bare `| a | b |` row to the step above it. Yadda does not: only a block between
 * two lines of dashes belongs to the step above, so each bare row is a step of its own, which no
 * definition matches and which fails as an undefined step at run time. The editor shows these rows as
 * that step's table, so nothing else points at them.
 */
class YaddaBareRowInspection : LocalInspectionTool() {
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (file !is YaddaFile) return null
    return file.grammar.steps.values
      .filter { step -> step.text.substringBefore('\n').trimStart().let { it.startsWith("|") || it.startsWith("┆") } }
      .map { step ->
        manager.createProblemDescriptor(
          file,
          file.lineRange(step.line),
          "Yadda reads a row without --- lines around its table as a step of its own",
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          isOnTheFly,
        )
      }.toTypedArray().takeIf { it.isNotEmpty() }
  }
}
