package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import io.github.jonfrankel.playwrightyadda.yadda.PlaywrightYaddaProjects
import io.github.jonfrankel.playwrightyadda.yadda.YaddaFile
import io.github.jonfrankel.playwrightyadda.yadda.YaddaMarkdown
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Rename (Shift+F6) on a step of a Yadda feature: renames the step's definition and every step that runs it.
 *
 * The Cucumber plugin has a rename of its own for a Gherkin step. It refuses a step with a block or a `[name]`,
 * it never rewrites a JavaScript definition, and it can't rename a step with no keyword, so for Yadda files this
 * takes its place: see [YaddaRenameHandlerRegistry], which makes it the only handler offered for such a step.
 * It also covers the steps of Markdown features, which the Cucumber plugin doesn't know are steps, and starts from a
 * step definition too (the caret on its `given` or on its text), where the IDE's own rename has nothing to rename.
 *
 * What is renamed is the definition's pattern: the dialog shows it with its groups, which stay as they are.
 */
class YaddaStepRenameHandler : RenameHandler {
  override fun isAvailableOnDataContext(dataContext: DataContext): Boolean = subjectOf(dataContext) != null

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext) = rename(project, editor, dataContext)

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) = rename(project, null, dataContext)

  private fun rename(project: Project, editor: Editor?, dataContext: DataContext) {
    val subject = subjectOf(dataContext) ?: return
    fun hint(message: String?) = CommonRefactoringUtil.showErrorHint(project, editor, message ?: "This step can't be renamed.", TITLE, null)

    val literals = subject.definitions().mapNotNull { YaddaSteps.stepLiteralOf(it) }.distinct()
    if (literals.isEmpty()) return hint("This step has no step definition to rename.")
    if (literals.size > 1) return hint("This step has ${literals.size} step definitions, so it isn't clear which to rename. Rename them by hand.")

    val target =
      try {
        StepRenamer.targetOf(literals.single())
      } catch (refusal: StepRenamer.Refusal) {
        return hint(refusal.message)
      }
    val answer = (answerForTests ?: { ask(project, it) })(target) ?: return
    val plan =
      try {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
          ThrowableComputable<StepRenamer.Plan, StepRenamer.Refusal> {
            ReadAction.computeBlocking<StepRenamer.Plan, StepRenamer.Refusal> { StepRenamer.plan(target, answer) }
          },
          "Finding the Steps to Rename",
          true,
          project,
        )
      } catch (refusal: StepRenamer.Refusal) {
        return hint(refusal.message)
      }
    StepRenamer.apply(project, plan)
  }

  private fun ask(project: Project, target: StepRenamer.Target): String? {
    val dialog = YaddaStepRenameDialog(project, target)
    return if (dialog.showAndGet()) dialog.text else null
  }

  /** What a rename starts from: a step of a `.feature` file or of a Markdown feature, or a step definition; and where to look for its definitions. */
  private class Subject(val definitions: () -> List<PsiElement>) {
    fun definitions(): List<PsiElement> = definitions.invoke()
  }

  private fun subjectOf(dataContext: DataContext): Subject? {
    val editor = CommonDataKeys.EDITOR.getData(dataContext)
    val file = CommonDataKeys.PSI_FILE.getData(dataContext)
    if (editor != null && file != null) {
      val offset = editor.caretModel.offset
      if (YaddaMarkdown.isFeature(file)) {
        val site = YaddaMarkdown.siteAt(file, offset) ?: return null
        return Subject { YaddaMarkdownSteps.candidates(file, site).definitions }
      }
      if (file is YaddaFile) {
        val step = PsiTreeUtil.getParentOfType(file.findElementAt(offset), GherkinStep::class.java, false) ?: return null
        return subjectOf(step)
      }
      definitionAt(file, offset)?.let { return subjectOf(it) }
    }
    val element = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
    if (element is GherkinStep) return subjectOf(element)
    return YaddaSteps.stepLiteralOf(element ?: return null)?.takeIf { inYaddaProject(it) }?.let { subjectOf(it) }
  }

  /** The step definition whose `given` or step text holds [offset], in a project that runs its features with playwright-yadda. */
  private fun definitionAt(file: PsiFile, offset: Int): JSLiteralExpression? {
    // The caret in a regex definition is in an injected regular expression, and that is the file the IDE passes.
    InjectedLanguageManager.getInstance(file.project).getInjectionHost(file)?.let { host ->
      return (host as? JSLiteralExpression)?.let { YaddaSteps.stepLiteralOf(it as PsiElement) }?.takeIf { inYaddaProject(it) }
    }
    val element = file.findElementAt(offset) ?: return null
    val call = PsiTreeUtil.getParentOfType(element, JSCallExpression::class.java, false) ?: return null
    val literal = YaddaSteps.stepLiteralOf(call) ?: return null
    val method = (call.methodExpression as? JSReferenceExpression)?.referenceNameElement
    if (method?.textRange?.containsOffset(offset) != true && !literal.textRange.containsOffset(offset)) return null
    return literal.takeIf { inYaddaProject(it) }
  }

  private fun inYaddaProject(literal: JSLiteralExpression): Boolean =
    literal.containingFile?.virtualFile?.let { PlaywrightYaddaProjects.isYaddaFeature(it) } == true

  private fun subjectOf(definition: JSLiteralExpression): Subject = Subject { listOf(definition) }

  private fun subjectOf(step: GherkinStep): Subject? =
    if (step.containingFile is YaddaFile) Subject { LibraryScope.candidateDefinitions(step).definitions } else null

  companion object {
    const val TITLE = "Rename Step"

    private var answerForTests: ((StepRenamer.Target) -> String?)? = null

    /** Stands in for the dialog: the new pattern for a target, or null to cancel. */
    @TestOnly
    fun answerWith(answer: ((StepRenamer.Target) -> String?)?) {
      answerForTests = answer
    }
  }
}
