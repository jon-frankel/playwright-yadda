package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewTypeLocation

/** Labels a step definition "step definition" in the Find Usages window instead of its class name. */
class YaddaStepDescriptionProvider : ElementDescriptionProvider {
  override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
    if (element !is PomTargetPsiElement || element.target !is YaddaStepPomTarget) return null
    return if (location === UsageViewTypeLocation.INSTANCE) "step definition" else null
  }
}
