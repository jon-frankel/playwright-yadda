package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.plugins.cucumber.CucumberUtil

/** Reports the Gherkin steps that run a step definition as references to it. */
class YaddaStepReferencesSearch :
  QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
  override fun processQuery(
    queryParameters: ReferencesSearch.SearchParameters,
    consumer: Processor<in PsiReference>,
  ) {
    val literal = YaddaSteps.stepLiteralOf(queryParameters.elementToSearch) ?: return
    val regex = YaddaSteps.definitionOf(literal)?.cucumberRegex ?: return

    CucumberUtil.findGherkinReferencesToElement(
      literal,
      regex,
      consumer,
      queryParameters.effectiveSearchScope,
    )
  }
}
