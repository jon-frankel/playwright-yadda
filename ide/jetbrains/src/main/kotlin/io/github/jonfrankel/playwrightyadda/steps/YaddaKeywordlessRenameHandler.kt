package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import io.github.jonfrankel.playwrightyadda.yadda.YaddaFile
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Rename on a step with no keyword. The Cucumber plugin renames a step through `GherkinStepImpl.setName`,
 * which throws for a step with no keyword, so the rename of such a step would end in an IDE error. This
 * says why it can't be renamed instead.
 */
class YaddaKeywordlessRenameHandler : RenameHandler {
  override fun isAvailableOnDataContext(dataContext: DataContext): Boolean =
    isKeywordless(dataContext.getData(CommonDataKeys.PSI_ELEMENT))

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext) = explain(project, editor)

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) = explain(project, null)

  private fun explain(project: Project, editor: Editor?) =
    CommonRefactoringUtil.showErrorHint(project, editor, MESSAGE, "Rename", null)

  companion object {
    const val MESSAGE = "A step without a keyword can't be renamed here. Give it Given, When or Then and rename it again, or edit its text."

    fun isKeywordless(element: PsiElement?): Boolean =
      element is GherkinStep && element.keyword == null && element.containingFile is YaddaFile
  }
}
