package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Drops the Cucumber plugin's "Ambiguous step reference with N definitions" warning when
 * `@Libraries` leaves a step with exactly one definition.
 *
 * That warning comes from the inspection that also reports undefined steps (`CucumberUndefinedStep`)
 * and counts every definition in the project. A step with no definitions is never narrowed, and one
 * that is still ambiguous after narrowing keeps its warning, so only the false positive goes away.
 */
class YaddaAmbiguousStepSuppressor : InspectionSuppressor {
  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    if (toolId != CUCUMBER_STEP_INSPECTION) return false
    val step = PsiTreeUtil.getParentOfType(element, GherkinStep::class.java, false) ?: return false
    return LibraryScope.narrowedDefinitions(step)?.size == 1
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
    SuppressQuickFix.EMPTY_ARRAY

  private companion object {
    const val CUCUMBER_STEP_INSPECTION = "CucumberUndefinedStep"
  }
}
