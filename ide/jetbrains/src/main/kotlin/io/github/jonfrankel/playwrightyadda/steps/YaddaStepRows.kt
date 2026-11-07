package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import io.github.jonfrankel.playwrightyadda.yadda.Vocabulary
import io.github.jonfrankel.playwrightyadda.yadda.YaddaGrammar
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.cucumber.steps.CucumberStepHelper

/**
 * What a Yadda step is matched against, for each row of its scenario's examples. Yadda expands an
 * outline once per row and matches every expansion, so a step is defined only if every row finds a
 * definition, and the definitions it runs are the union over the rows. The Cucumber plugin takes one
 * text per step, so this is how the plugin's own code (Markdown, the row inspection, Cmd+B) sees all of them.
 */
object YaddaStepRows {
  /**
   * The texts a definition is tried against for one expansion, [text] being the step with a row filled in: the
   * first line, and the whole step with its block, each with and without its keyword (a step may have none).
   * A definition's pattern is prefixed with a keyword of [vocabulary]'s language at runtime, while the
   * Cucumber plugin matches a step's text without it.
   */
  fun variants(text: String, vocabulary: Vocabulary): List<String> =
    listOf(text.substringBefore('\n'), text).flatMap { listOf(it, vocabulary.withoutStepKeyword(it)) }.distinct()

  /** One list of [variants] per examples row, or the one list for a step with no examples. */
  fun perRow(step: YaddaGrammar.Step, vocabulary: Vocabulary): List<List<String>> =
    step.expandedRows(step.text).map { variants(it, vocabulary) }

  /** Every text any row of [step] is tried against, and the first line as written, which the Cucumber plugin also tries. */
  fun allTexts(step: YaddaGrammar.Step, vocabulary: Vocabulary): List<String> =
    (perRow(step, vocabulary).flatten() + variants(step.text.substringBefore('\n'), vocabulary)).distinct()

  /** The step definitions of the project containing [context], or none while indexes are building. */
  fun loadDefinitions(context: PsiElement): List<AbstractStepDefinition> {
    if (DumbService.isDumb(context.project)) return emptyList()
    val module = ModuleUtilCore.findModuleForPsiElement(context) ?: return emptyList()
    return load(module).filter { def ->
      def.element?.let { YaddaStepFilter.isValidStepDefinition(it) } ?: true
    }
  }

  private fun load(module: Module): List<AbstractStepDefinition> = CucumberStepHelper.loadStepsFor(module)

  /** The definitions that match [step] for at least one of its rows. */
  fun definitions(definitions: List<AbstractStepDefinition>, step: YaddaGrammar.Step, vocabulary: Vocabulary): List<AbstractStepDefinition> {
    val texts = allTexts(step, vocabulary)
    return definitions.filter { definition -> texts.any { definition.matches(it) } }
  }

  /**
   * The 0-based examples rows of [step] that no definition matches, when some other row does match. A step that
   * nothing matches at all is the plain undefined step, which has its own report; so is a step with no examples.
   */
  fun unmatchedRows(definitions: List<AbstractStepDefinition>, step: YaddaGrammar.Step, vocabulary: Vocabulary): List<Int> {
    val rows = perRow(step, vocabulary)
    if (rows.size < 2) return emptyList()
    val matched = rows.map { variants -> definitions.any { definition -> variants.any { definition.matches(it) } } }
    return if (matched.any { it }) matched.indices.filter { !matched[it] } else emptyList()
  }

  /**
   * The text to offer the Cucumber plugin for [step], without its keyword: the first row's, unless a later row is the
   * first that a definition matches, so that a step resolves if any row does. [definitions] are loaded only when there
   * is more than one distinct row.
   */
  fun representative(step: YaddaGrammar.Step, vocabulary: Vocabulary, definitions: () -> List<AbstractStepDefinition>): String {
    val rows = step.expandedRows(step.text)
    val chosen =
      if (rows.distinct().size < 2) {
        rows.first()
      } else {
        val loaded = definitions()
        rows.firstOrNull { text -> variants(text, vocabulary).any { variant -> loaded.any { it.matches(variant) } } } ?: rows.first()
      }
    return vocabulary.withoutStepKeyword(chosen)
  }
}
