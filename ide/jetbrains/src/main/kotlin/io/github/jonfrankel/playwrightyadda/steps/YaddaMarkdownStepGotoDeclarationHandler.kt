package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import io.github.jonfrankel.playwrightyadda.yadda.YaddaMarkdown

/**
 * Cmd+B on a step of a Markdown feature goes to the definition that runs it. The Cucumber plugin has
 * nothing to resolve there (see [YaddaMarkdownSteps]), so unlike for a `.feature` step this is the
 * only handler: it returns every definition the step matches, narrowed by `@Libraries`.
 */
class YaddaMarkdownStepGotoDeclarationHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(
    sourceElement: PsiElement?,
    offset: Int,
    editor: Editor?,
  ): Array<PsiElement>? {
    val file = sourceElement?.containingFile ?: return null
    if (DumbService.isDumb(file.project) || !YaddaMarkdown.isFeature(file)) return null
    val site = YaddaMarkdown.siteAt(file, offset) ?: return null

    val definitions = YaddaMarkdownSteps.candidates(file, site).definitions
    return when {
      definitions.isEmpty() -> null
      definitions.size == 1 -> arrayOf(definitions.single())
      // Several: let the popup say which file each is in.
      else -> definitions.map { if (it is JSLiteralExpression) YaddaStepTarget(it) else it }.toTypedArray()
    }
  }
}
