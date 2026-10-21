package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.plugins.cucumber.CucumberUtil

/**
 * Reports the Gherkin steps that run a step definition as references to it, leaving out steps
 * from scenarios that do not enable the definition's library.
 */
class YaddaStepReferencesSearch :
  QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
  override fun processQuery(
    queryParameters: ReferencesSearch.SearchParameters,
    consumer: Processor<in PsiReference>,
  ) {
    val literal = YaddaSteps.stepLiteralOf(queryParameters.elementToSearch) ?: return
    val regex = YaddaSteps.definitionOf(literal)?.cucumberRegex ?: return

    val enabledOnly =
      Processor<PsiReference> { reference ->
        !LibraryScope.isEnabledFor(literal, reference.element) || consumer.process(reference)
      }

    CucumberUtil.findGherkinReferencesToElement(
      literal,
      regex,
      enabledOnly,
      queryParameters.effectiveSearchScope,
    )
  }
}
