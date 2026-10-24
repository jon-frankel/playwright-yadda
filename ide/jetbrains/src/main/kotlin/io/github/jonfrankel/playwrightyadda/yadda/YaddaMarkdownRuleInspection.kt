package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile

/** The same warning as [YaddaRuleInspection], for a `.md` feature: playwright-yadda doesn't run the scenarios under a Rule. */
class YaddaMarkdownRuleInspection : LocalInspectionTool() {
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (!YaddaMarkdown.isFeature(file)) return null
    return YaddaMarkdown.analysis(file).ruleLines.map { line ->
      manager.createProblemDescriptor(
        file,
        FeatureLines.range(file, line),
        "playwright-yadda doesn't run the scenarios under a Rule",
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        isOnTheFly,
      )
    }.toTypedArray().takeIf { it.isNotEmpty() }
  }
}
