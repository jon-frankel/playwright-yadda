package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtilEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinPystring

/**
 * Typing a `|` in a table row pads the row so the pipe lands under the one above, as Gherkin's
 * handler does. That one acts only when the file's type is Gherkin's, so a `*.yadda.feature` file
 * never gets it, and it reads the row from the tree, which has no row for a last line the file
 * ends on. This one reads the two lines and takes over for every Yadda file.
 *
 * Both can run on a `.feature` file: whichever goes first leaves the caret at the column the other
 * wants, so the second finds nothing to pad.
 */
class YaddaTypedHandler : TypedHandlerDelegate() {
  override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
    if (c != '|' || file !is YaddaFile || editor.selectionModel.hasSelection()) return Result.CONTINUE
    val document = editor.document
    val offset = editor.caretModel.offset
    val line = document.getLineNumber(offset)
    if (line == 0) return Result.CONTINUE

    val lineStart = document.getLineStartOffset(line)
    val before = document.getText(TextRange(lineStart, offset))
    val above = document.getText(TextRange(document.getLineStartOffset(line - 1), document.getLineEndOffset(line - 1)))
    val rowStart = before.indexOfFirst { !it.isWhitespace() }
    val aboveStart = above.indexOfFirst { !it.isWhitespace() }
    if (rowStart < 0 || before[rowStart] != '|' || aboveStart < 0 || above[aboveStart] != '|') return Result.CONTINUE
    // A line that starts with a pipe in a text block is text, not a row.
    if (file.findElementAt(offset - 1)?.let { PsiTreeUtil.getParentOfType(it, GherkinPystring::class.java, false) } != null) {
      return Result.CONTINUE
    }

    // The pipe about to be typed is the next after those already in the row, so it belongs under
    // the one that many along the row above.
    val target = pipes(above).getOrNull(pipes(before).size) ?: return Result.CONTINUE
    val missing = (target - aboveStart) - (before.length - rowStart)
    if (missing > 0) EditorModificationUtilEx.insertStringAtCaret(editor, " ".repeat(missing))
    return Result.CONTINUE
  }

  /** Where the cell separators are: pipes not escaped with a backslash. */
  private fun pipes(row: String): List<Int> = row.indices.filter { row[it] == '|' && (it == 0 || row[it - 1] != '\\') }
}
