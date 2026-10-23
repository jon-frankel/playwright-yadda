package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile

/**
 * What Yadda would throw about the file, said before the tests run. It is Yadda's own parser,
 * ported (`YaddaGrammar`), so the message is Yadda's and it flags what Yadda rejects, which
 * Gherkin's checks of the same file neither agree with nor cover.
 */
class YaddaParseInspection : LocalInspectionTool() {
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (file !is YaddaFile || file.text.isBlank()) return null
    val issue = file.grammar.issue ?: return null
    return arrayOf(
      manager.createProblemDescriptor(
        file,
        file.lineRange(issue.line),
        "Yadda can't parse this: ${issue.message}",
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        isOnTheFly,
      ),
    )
  }
}
