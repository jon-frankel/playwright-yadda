package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiElement

/** Enables Find Usages on a step definition. */
class YaddaFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
  override fun canFindUsages(element: PsiElement): Boolean =
    element is PomTargetPsiElement && element.target is YaddaStepPomTarget

  override fun createFindUsagesHandler(
    element: PsiElement,
    forHighlightUsages: Boolean,
  ): FindUsagesHandler = object : FindUsagesHandler(element) {}
}
