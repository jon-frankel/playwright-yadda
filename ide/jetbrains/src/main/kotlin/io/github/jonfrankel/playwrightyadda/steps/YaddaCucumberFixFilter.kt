package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.codeInsight.daemon.impl.IntentionActionFilter
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.psi.PsiFile
import io.github.jonfrankel.playwrightyadda.yadda.YaddaFile
import org.jetbrains.plugins.cucumber.inspections.CucumberCreateStepFixBase

/**
 * Hides the Cucumber.js plugin's "Create step definition" and "Create all step definitions" in a Yadda
 * feature. They write a `Given(…)` call that imports from `cucumber` rather than a Yadda step, and on a
 * step with no keyword they throw. [YaddaCreateStepIntention] and [YaddaCreateAllStepsIntention] stand
 * in for them.
 */
class YaddaCucumberFixFilter : IntentionActionFilter {
  override fun accept(intentionAction: IntentionAction, file: PsiFile?): Boolean {
    if (file !is YaddaFile) return true
    return QuickFixWrapper.unwrap(intentionAction) !is CucumberCreateStepFixBase
  }
}
