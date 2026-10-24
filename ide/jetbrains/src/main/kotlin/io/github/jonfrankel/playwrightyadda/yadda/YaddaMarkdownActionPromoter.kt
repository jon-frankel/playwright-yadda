package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiDocumentManager
import io.github.jonfrankel.playwrightyadda.libraries.LibraryNames

/**
 * Lets Cmd+B go to the definition from a step of a Markdown feature. The Markdown plugin binds the
 * same key to "Bold" in every Markdown editor, and promotes its own actions above others, so in a `.md`
 * file the key inserted `**` instead of navigating. With the caret on a step (or a name in
 * `@Libraries=…`) and nothing selected, Bold is set aside and Go to Declaration goes first; anywhere
 * else in the file Cmd+B stays Bold, as does a selection, which is what bold is for.
 */
class YaddaMarkdownActionPromoter : ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> =
    if (onStep(context)) actions.filter { ActionManager.getInstance().getId(it) == GO_TO_DECLARATION } else emptyList()

  override fun suppress(actions: List<AnAction>, context: DataContext): List<AnAction> =
    if (onStep(context)) actions.filter { ActionManager.getInstance().getId(it) == BOLD } else emptyList()

  private fun onStep(context: DataContext): Boolean {
    // The key dispatcher hands promoters a UI-only snapshot of the context, which has the editor but not
    // the PSI file, so the file is the editor's document's, if its PSI is built already (it is, in an open editor).
    val editor = CommonDataKeys.EDITOR.getData(context) ?: return false
    if (editor.selectionModel.hasSelection()) return false
    val project = editor.project ?: return false
    val file = PsiDocumentManager.getInstance(project).getCachedPsiFile(editor.document) ?: return false
    if (!YaddaMarkdown.isFeature(file)) return false
    val offset = editor.caretModel.offset
    return YaddaMarkdown.siteAt(file, offset) != null || LibraryNames.nameAt(file.text, offset, markdown = true) != null
  }

  private companion object {
    const val BOLD = "org.intellij.plugins.markdown.ui.actions.styling.ToggleBoldAction"
    const val GO_TO_DECLARATION = "GotoDeclaration"
  }
}
