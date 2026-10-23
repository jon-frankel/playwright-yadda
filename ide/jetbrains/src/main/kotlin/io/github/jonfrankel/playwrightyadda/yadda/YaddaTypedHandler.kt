package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtilEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinExamplesBlock
import org.jetbrains.plugins.cucumber.psi.GherkinPystring
import org.jetbrains.plugins.cucumber.psi.GherkinTable

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
    if (rowStart < 0 || aboveStart < 0) return Result.CONTINUE
    if (before[rowStart] == '|') {
      if (above[aboveStart] != '|') return Result.CONTINUE
      // A line that starts with a pipe in a text block is text, not a row.
      if (file.findElementAt(offset - 1)?.let { PsiTreeUtil.getParentOfType(it, GherkinPystring::class.java, false) } != null) {
        return Result.CONTINUE
      }
    } else if (!inOneExamplesTable(project, file, document, lineStart + rowStart, document.getLineStartOffset(line - 1) + aboveStart)) {
      // Rows of an Examples table don't need a pipe to start them, but anywhere else a line of text is text.
      return Result.CONTINUE
    }

    // The pipe about to be typed is the next after those already in the row, so it belongs under
    // the one that many along the row above.
    val target = pipes(above).getOrNull(pipes(before).size) ?: return Result.CONTINUE
    val missing = (target - aboveStart) - (before.length - rowStart)
    if (missing > 0) EditorModificationUtilEx.insertStringAtCaret(editor, " ".repeat(missing))
    return Result.CONTINUE
  }

  /** Whether the two offsets, which are on different lines, are in the rows of the same Examples table. */
  private fun inOneExamplesTable(project: Project, file: PsiFile, document: com.intellij.openapi.editor.Document, a: Int, b: Int): Boolean {
    PsiDocumentManager.getInstance(project).commitDocument(document)
    fun table(offset: Int) = file.findElementAt(offset)?.let { PsiTreeUtil.getParentOfType(it, GherkinTable::class.java) }
    val first = table(a) ?: return false
    return first === table(b) && first.parent is GherkinExamplesBlock
  }

  /** Where the cell separators are: pipes not escaped with a backslash. */
  private fun pipes(row: String): List<Int> = row.indices.filter { row[it] == '|' && (it == 0 || row[it - 1] != '\\') }
}
