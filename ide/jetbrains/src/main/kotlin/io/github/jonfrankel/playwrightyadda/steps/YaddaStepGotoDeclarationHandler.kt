package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Cmd+B on a `.feature` step goes to the definition that runs, not every definition with that text.
 *
 * The Cucumber.js plugin resolves a step against every definition in the project. When two libraries
 * define the same text, this narrows the result to the libraries the scenario enables. Handlers are
 * consulted before reference resolution, and returning null falls back to the stock behaviour, which
 * is what happens whenever nothing would be ruled out.
 */
class YaddaStepGotoDeclarationHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(
    sourceElement: PsiElement?,
    offset: Int,
    editor: Editor?,
  ): Array<PsiElement>? {
    val step = PsiTreeUtil.getParentOfType(sourceElement, GherkinStep::class.java, false) ?: return null
    return LibraryScope.narrowedDefinitions(step)?.toTypedArray()
  }
}
