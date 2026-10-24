package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * "Create Yadda step definition" on a step with no definition, in a `.feature` or `.md` Yadda feature.
 * Replaces the Cucumber.js plugin's fix of the same name there (see [YaddaCucumberFixFilter]).
 */
class YaddaCreateStepIntention : IntentionAction {
  override fun getText(): String = "Create Yadda step definition"

  override fun getFamilyName(): String = "Create Yadda step definition"

  override fun startInWriteAction(): Boolean = false

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (editor == null || file == null || DumbService.isDumb(project)) return false
    val undefined = YaddaStepCreator.undefinedAt(file, editor.caretModel.offset) ?: return false
    return YaddaStepCreator.targetsFor(undefined).isNotEmpty()
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    if (editor == null || file == null) return
    val undefined = YaddaStepCreator.undefinedAt(file, editor.caretModel.offset) ?: return
    YaddaStepCreator.create(project, editor, listOf(undefined))
  }
}

/** "Create all Yadda step definitions" for the steps of the file that have none. */
class YaddaCreateAllStepsIntention : IntentionAction {
  override fun getText(): String = "Create all Yadda step definitions"

  override fun getFamilyName(): String = "Create Yadda step definition"

  override fun startInWriteAction(): Boolean = false

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (editor == null || file == null || DumbService.isDumb(project)) return false
    // At the caret, as the single-step fix is: the lightbulb only needs to appear on a step that is undefined.
    val here = YaddaStepCreator.undefinedAt(file, editor.caretModel.offset) ?: return false
    if (YaddaStepCreator.targetsFor(here).isEmpty()) return false
    return YaddaStepCreator.undefinedIn(file).size > 1
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    if (file == null) return
    YaddaStepCreator.create(project, editor, YaddaStepCreator.undefinedIn(file))
  }
}
