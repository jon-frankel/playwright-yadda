package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.jonfrankel.playwrightyadda.steps.YaddaStepRows
import org.jetbrains.plugins.cucumber.BDDFrameworkType
import org.jetbrains.plugins.cucumber.OptionalStepDefinitionExtensionPoint
import org.jetbrains.plugins.cucumber.StepDefinitionCreator
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.AbstractCucumberExtension
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

/**
 * Tells the Cucumber plugin what Yadda matches a step against. The plugin matches a step's first
 * line, with `<name>` filled in from the examples; Yadda matches the whole of it, fenced block
 * included, with `[name]` filled in, so a definition written for what Yadda sees (a regex that
 * requires the block, or that takes a number where the outline has `[qty]`) looked undefined.
 *
 * This adds a second text for such a step. A step resolves if either text matches a definition, and
 * the definitions come from the Cucumber.js plugin as before: this one has none of its own, and
 * takes no part in creating step definitions.
 *
 * An outline step is offered for one row of its examples, since the Cucumber plugin takes one text per
 * step: the first row that a definition matches, else the first row (see [YaddaStepRows.representative]).
 * Yadda matches every row, so a definition that only some rows match still resolves, and a step that no row
 * matches is undefined. Cmd+B sees the definitions of every row, and the row inspection the rows that miss.
 */
class YaddaCucumberExtension : AbstractCucumberExtension(), OptionalStepDefinitionExtensionPoint {
  override fun participateInStepDefinitionCreation(file: PsiElement): Boolean = false

  override fun isStepLikeFile(child: PsiElement): Boolean = false

  override fun isWritableStepLikeFile(child: PsiElement): Boolean = false

  override fun getStepFileType(): BDDFrameworkType = BDDFrameworkType(JavaScriptFileType, "Yadda")

  override fun getStepDefinitionCreator(): StepDefinitionCreator = object : StepDefinitionCreator {
    override fun createStepDefinitionContainer(directory: PsiDirectory, name: String): PsiFile =
      throw UnsupportedOperationException("Yadda step definitions are created by the Cucumber.js plugin")

    override fun getDefaultStepDefinitionFolderPath(step: GherkinStep): String = ""

    override fun getStepDefinitionFilePath(file: PsiFile): String = ""

    override fun getDefaultStepFileName(step: GherkinStep): String = ""
  }

  override fun loadStepsFor(module: Module): List<AbstractStepDefinition> =
    io.github.jonfrankel.playwrightyadda.steps.YaddaStepLoader.loadDynamicSteps(module)

  override fun getStepDefinitionContainers(file: GherkinFile): Collection<PsiFile> = emptyList()

  override fun getStepName(step: PsiElement): String? {
    if (step !is GherkinStep) return null
    val file = step.containingFile as? YaddaFile ?: return null
    val yadda = file.yaddaStep(step) ?: return null
    val text = YaddaStepRows.representative(yadda, YaddaSettings.vocabularyOf(step.project)) { YaddaStepRows.loadDefinitions(step) }
    return text.takeIf { it != step.substitutedName }
  }
}
