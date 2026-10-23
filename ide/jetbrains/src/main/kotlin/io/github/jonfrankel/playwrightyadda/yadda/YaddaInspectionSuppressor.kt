package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement

/**
 * Silences Gherkin checks that say the opposite of what Yadda does. Yadda has no outline keyword:
 * `Scenario:` takes examples, and a `Scenario Outline:` with none is a scenario. Columns are used
 * as `[name]`, which Gherkin's unused-column check can't see, and [YaddaUnusedColumnInspection]
 * checks instead.
 */
class YaddaInspectionSuppressor : InspectionSuppressor {
  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean =
    toolId in SUPPRESSED && element.containingFile is YaddaFile

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> = SuppressQuickFix.EMPTY_ARRAY

  private companion object {
    val SUPPRESSED = setOf("CucumberTableInspection", "GherkinScenarioToScenarioOutline", "CucumberMissedExamples")
  }
}
