package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.psi.PsiFile
import io.github.jonfrankel.playwrightyadda.yadda.Vocabulary
import io.github.jonfrankel.playwrightyadda.yadda.YaddaMarkdown
import io.github.jonfrankel.playwrightyadda.yadda.YaddaSettings
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

/**
 * What a step of a Markdown feature runs. The Cucumber plugin only resolves the steps of its own
 * Gherkin PSI, which a Markdown file doesn't have, so this asks it for the project's step
 * definitions (`CucumberStepHelper.loadStepsFor`) and matches them to the step's text the way
 * `YaddaCucumberExtension` does for a `.feature` step: by the first line, and by the whole of the
 * step, fenced block included, with `[name]` filled in from each of the scenario's examples rows, so a
 * step is defined when any row matches a definition (`YaddaStepRows`).
 */
object YaddaMarkdownSteps {
  /** The texts a definition is tried against: those of every examples row (see [YaddaStepRows]). */
  fun texts(site: YaddaMarkdown.Site, vocabulary: Vocabulary = Vocabulary.ENGLISH): List<String> =
    YaddaStepRows.allTexts(site.step, vocabulary)

  fun definitions(file: PsiFile, site: YaddaMarkdown.Site): List<AbstractStepDefinition> {
    val loaded = YaddaStepRows.loadDefinitions(file)
    if (loaded.isEmpty()) return emptyList()
    return YaddaStepRows.definitions(loaded, site.step, YaddaSettings.vocabularyOf(file.project))
  }

  /** The definitions [site] runs, narrowed to the libraries its scenario enables when that rules some out. */
  fun candidates(file: PsiFile, site: YaddaMarkdown.Site): LibraryScope.Candidates {
    val elements = definitions(file, site).mapNotNull { it.element }.distinct()
    val anchor = file.findElementAt(site.range.startOffset) ?: return LibraryScope.Candidates(elements, false)
    return LibraryScope.candidates(elements, anchor)
  }
}
