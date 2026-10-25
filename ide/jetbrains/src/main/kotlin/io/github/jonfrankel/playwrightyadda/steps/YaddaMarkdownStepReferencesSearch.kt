package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import io.github.jonfrankel.playwrightyadda.yadda.YaddaMarkdown
import io.github.jonfrankel.playwrightyadda.yadda.YaddaSettings

/**
 * Reports the steps of Markdown features that run a step definition as references to it, leaving
 * out steps from scenarios that don't enable the definition's library. The `.feature` steps are
 * found by [YaddaStepReferencesSearch].
 */
class YaddaMarkdownStepReferencesSearch :
  QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
  override fun processQuery(
    queryParameters: ReferencesSearch.SearchParameters,
    consumer: Processor<in PsiReference>,
  ) {
    val literal = YaddaSteps.stepLiteralOf(queryParameters.elementToSearch) ?: return
    if (DumbService.isDumb(queryParameters.project)) return
    val definition = YaddaSteps.definitionOf(literal) ?: return

    for (file in YaddaMarkdown.featureFiles(queryParameters.project, queryParameters.effectiveSearchScope)) {
      for (site in YaddaMarkdown.sites(file)) {
        if (YaddaMarkdownSteps.texts(site, YaddaSettings.vocabularyOf(file.project)).none { definition.matches(it) }) continue
        if (!LibraryScope.isEnabledFor(literal, file, site.range.startOffset)) continue
        if (!consumer.process(YaddaMarkdownStepReference(file, site.range, literal))) return
      }
    }
  }
}
