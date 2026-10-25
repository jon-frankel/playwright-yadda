package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiFile
import io.github.jonfrankel.playwrightyadda.yadda.Vocabulary
import io.github.jonfrankel.playwrightyadda.yadda.YaddaMarkdown
import io.github.jonfrankel.playwrightyadda.yadda.YaddaSettings
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.cucumber.steps.CucumberStepHelper

/**
 * What a step of a Markdown feature runs. The Cucumber plugin only resolves the steps of its own
 * Gherkin PSI, which a Markdown file doesn't have, so this asks it for the project's step
 * definitions (`CucumberStepHelper.loadStepsFor`) and matches them to the step's text the way
 * `YaddaCucumberExtension` does for a `.feature` step: by the first line, and by the whole of the
 * step, fenced block included, with `[name]` filled in from the scenario's first examples row.
 */
object YaddaMarkdownSteps {
  /**
   * The texts a definition is tried against: the first line, and the whole step with its block, each as
   * written and with `[name]` filled in, each with and without its keyword (a step may have none). A definition's
   * pattern is prefixed with a keyword of [vocabulary]'s language at runtime, while the Cucumber plugin matches a
   * step's text without it.
   */
  fun texts(site: YaddaMarkdown.Site, vocabulary: Vocabulary = Vocabulary.ENGLISH): List<String> {
    val step = site.step
    val firstLine = step.text.substringBefore('\n')
    return listOf(firstLine, step.expanded(firstLine), step.expanded(step.text))
      .flatMap { listOf(it, vocabulary.withoutStepKeyword(it)) }
      .distinct()
  }

  fun definitions(file: PsiFile, site: YaddaMarkdown.Site): List<AbstractStepDefinition> {
    if (DumbService.isDumb(file.project)) return emptyList()
    val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return emptyList()
    val texts = texts(site, YaddaSettings.vocabularyOf(file.project))
    return CucumberStepHelper.loadStepsFor(module).filter { definition -> texts.any { definition.matches(it) } }
  }

  /** The definitions [site] runs, narrowed to the libraries its scenario enables when that rules some out. */
  fun candidates(file: PsiFile, site: YaddaMarkdown.Site): LibraryScope.Candidates {
    val elements = definitions(file, site).mapNotNull { it.element }.distinct()
    val anchor = file.findElementAt(site.range.startOffset) ?: return LibraryScope.Candidates(elements, false)
    return LibraryScope.candidates(elements, anchor)
  }
}
