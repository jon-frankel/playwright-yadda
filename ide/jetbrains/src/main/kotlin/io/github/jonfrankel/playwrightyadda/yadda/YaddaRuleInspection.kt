package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile

/**
 * Yadda reads `Rule:` and keeps the scenarios under it apart from the feature's own, and
 * playwright-yadda defines tests for the feature's own only (`defineFeature` in `src/feature.ts`).
 * So a scenario under a rule parses, and is never run.
 */
class YaddaRuleInspection : LocalInspectionTool() {
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (file !is YaddaFile) return null
    return file.grammar.ruleLines.map { line ->
      manager.createProblemDescriptor(
        file,
        file.lineRange(line),
        "playwright-yadda doesn't run the scenarios under a Rule",
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        isOnTheFly,
      )
    }.toTypedArray().takeIf { it.isNotEmpty() }
  }
}
