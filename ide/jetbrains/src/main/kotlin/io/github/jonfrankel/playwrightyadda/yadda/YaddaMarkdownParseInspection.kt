package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile

/**
 * What Yadda's Markdown parser would throw about a feature, said before the tests run. The same
 * check as [YaddaParseInspection], for a `.md` file.
 */
class YaddaMarkdownParseInspection : LocalInspectionTool() {
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (!YaddaMarkdown.isFeature(file)) return null
    val issue = YaddaMarkdown.analysis(file).issue ?: return null
    return arrayOf(
      manager.createProblemDescriptor(
        file,
        FeatureLines.range(file, issue.line),
        "Yadda can't parse this: ${issue.message}",
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        isOnTheFly,
      ),
    )
  }
}
