package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import io.github.jonfrankel.playwrightyadda.yadda.YaddaFileType
import org.jetbrains.plugins.cucumber.CucumberUtil

/**
 * Reports the Gherkin steps that run a step definition as references to it, leaving out steps
 * from scenarios that do not enable the definition's library.
 *
 * The Cucumber plugin only looks in files of Gherkin's own file type, so `*.yadda.feature` files get a
 * second pass of their own. A plain `.feature` file in a Yadda project keeps Gherkin's file type.
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
        !LibraryScope.isEnabledFor(literal, reference.element, reference.element.textRange.startOffset + reference.rangeInElement.startOffset) ||
          consumer.process(reference)
      }

    val scope = queryParameters.effectiveSearchScope
    if (!CucumberUtil.findGherkinReferencesToElement(literal, regex, enabledOnly, scope)) return

    // A local scope is passed through untouched by the Cucumber plugin, so it already covers Yadda files.
    if (scope !is GlobalSearchScope) return
    val manager = PsiManager.getInstance(queryParameters.project)
    val yaddaFiles =
      FileTypeIndex.getFiles(YaddaFileType, scope).mapNotNull { manager.findFile(it) }
    if (yaddaFiles.isEmpty()) return
    CucumberUtil.findGherkinReferencesToElement(literal, regex, enabledOnly, LocalSearchScope(yaddaFiles.toTypedArray()))
  }
}
