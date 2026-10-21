package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor

/**
 * Enables Find Usages (and the usages popup behind Cmd+B) on a step definition.
 *
 * The platform's own word search also reports every step that resolves to the definition, whichever
 * library the step's scenario enables. Filtering here, where every consumer of the usages passes
 * through, is what keeps those out.
 */
class YaddaFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
  override fun canFindUsages(element: PsiElement): Boolean =
    element is PomTargetPsiElement && element.target is YaddaStepPomTarget

  override fun createFindUsagesHandler(
    element: PsiElement,
    forHighlightUsages: Boolean,
  ): FindUsagesHandler =
    object : FindUsagesHandler(element) {
      override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions,
      ): Boolean {
        val definition = YaddaSteps.stepLiteralOf(element)
        val enabledOnly =
          Processor<UsageInfo> { usage ->
            val step = usage.element
            definition == null ||
              step == null ||
              !LibraryScope.isEnabledFor(definition, step) ||
              processor.process(usage)
          }
        return super.processElementUsages(element, enabledOnly, options)
      }
    }
}
