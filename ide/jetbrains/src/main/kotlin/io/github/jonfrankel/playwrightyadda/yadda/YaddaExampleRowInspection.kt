package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.github.jonfrankel.playwrightyadda.steps.YaddaStepRows

/**
 * An outline step that no step definition matches for some row of its examples, while others do.
 * Yadda expands the outline once per row and matches each, so a definition written for
 * `(\d+)` fails the row where `[qty]` is `many`, at run time, in the middle of the suite. A step that
 * no row matches at all is the plain undefined step, which is reported on its own.
 */
class YaddaExampleRowInspection : LocalInspectionTool() {
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (file !is YaddaFile || DumbService.isDumb(file.project)) return null
    val steps = file.grammar.steps.values.filter { it.expandedRows(it.text).size > 1 }
    if (steps.isEmpty()) return null
    return problems(file, manager, isOnTheFly, steps.map { it to file.lineRange(it.line) })
  }
}

/** [YaddaExampleRowInspection] for a Markdown feature. */
class YaddaMarkdownExampleRowInspection : LocalInspectionTool() {
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (DumbService.isDumb(file.project) || !YaddaMarkdown.isFeature(file)) return null
    val sites = YaddaMarkdown.sites(file).filter { it.step.expandedRows(it.step.text).size > 1 }
    if (sites.isEmpty()) return null
    return problems(file, manager, isOnTheFly, sites.map { it.step to it.range })
  }
}

private fun problems(
  file: PsiFile,
  manager: InspectionManager,
  isOnTheFly: Boolean,
  steps: List<Pair<YaddaGrammar.Step, TextRange>>,
): Array<ProblemDescriptor>? {
  val definitions = YaddaStepRows.loadDefinitions(file)
  if (definitions.isEmpty()) return null
  val vocabulary = YaddaSettings.vocabularyOf(file.project)
  return steps
    .mapNotNull { (step, range) ->
      val unmatched = YaddaStepRows.unmatchedRows(definitions, step, vocabulary)
      if (unmatched.isEmpty()) return@mapNotNull null
      val first = unmatched.first()
      val text = vocabulary.withoutStepKeyword(step.expandedRows(step.text)[first]).substringBefore('\n')
      val more = if (unmatched.size > 1) " and ${unmatched.size - 1} more" else ""
      manager.createProblemDescriptor(
        file,
        range,
        "No step definition matches \"$text\", which Yadda runs for example row ${first + 1}$more",
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        isOnTheFly,
      )
    }.toTypedArray()
    .takeIf { it.isNotEmpty() }
}
