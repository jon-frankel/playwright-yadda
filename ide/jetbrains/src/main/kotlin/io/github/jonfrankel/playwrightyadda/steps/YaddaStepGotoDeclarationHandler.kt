package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.javascript.psi.JSLiteralExpression
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
 * is what happens for a step with a single definition.
 *
 * When several definitions remain, they are returned as [YaddaStepTarget]s so the chooser popup shows
 * which file each one is in.
 */
class YaddaStepGotoDeclarationHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(
    sourceElement: PsiElement?,
    offset: Int,
    editor: Editor?,
  ): Array<PsiElement>? {
    val step = PsiTreeUtil.getParentOfType(sourceElement, GherkinStep::class.java, false) ?: return null

    val (definitions, narrowed) = LibraryScope.candidateDefinitions(step).let { it.definitions to it.narrowed }
    return when {
      // One left after narrowing: go straight there.
      narrowed && definitions.size == 1 -> arrayOf(definitions.single())
      // Still several: let the popup say which file each is in.
      definitions.size >= 2 ->
        definitions.map { if (it is JSLiteralExpression) YaddaStepTarget(it) else it }.toTypedArray()
      // A single definition, or none: nothing to add to the IDE's own handling.
      else -> null
    }
  }
}
