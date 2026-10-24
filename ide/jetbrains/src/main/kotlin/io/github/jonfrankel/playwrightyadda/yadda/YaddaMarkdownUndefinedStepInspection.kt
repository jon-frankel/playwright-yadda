package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiFile
import io.github.jonfrankel.playwrightyadda.steps.YaddaMarkdownSteps

/**
 * A step of a `.md` feature that no step definition matches. The Cucumber plugin reports these for
 * a `.feature` file ("Undefined step reference"); a Markdown file has nothing of its to report them.
 *
 * Any line of a scenario that isn't a heading, a comment or a block is a step to Yadda, so a stray
 * paragraph in a scenario is reported too, which is what Yadda says when it runs.
 */
class YaddaMarkdownUndefinedStepInspection : LocalInspectionTool() {
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (DumbService.isDumb(file.project) || !YaddaMarkdown.isFeature(file)) return null
    return YaddaMarkdown.sites(file)
      .filter { YaddaMarkdownSteps.definitions(file, it).isEmpty() }
      .map {
        manager.createProblemDescriptor(
          file,
          it.range,
          "Undefined step",
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          isOnTheFly,
        )
      }
      .toTypedArray()
      .takeIf { it.isNotEmpty() }
  }
}
