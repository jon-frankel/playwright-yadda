package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtilEx
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile

/**
 * Enter at the end of a block's opening fence (`--- json`) adds the closing fence, as Gherkin's handler
 * does for `"""`. That one would add quote marks, so for Yadda files this runs first and takes over
 * every Enter at the end of a fence line.
 *
 * Fences are read from the text rather than the tree, the way the lexer pairs them: the first fence
 * line opens a block and the next closes it, so a fence with an odd number of fence lines above it is
 * an opening one. It needs a closing fence unless the lines under it already lead to one. Yadda reads
 * a line that starts with a step keyword, a tag or a `#` even inside a block, so such a line can't be
 * the block's content: the block is empty and a later fence belongs to some other block. A block with
 * nothing in it has no doc string in the tree yet, which is why the tree can't say.
 */
class YaddaEnterHandler : EnterHandlerDelegate {
  override fun preprocessEnter(
    file: PsiFile,
    editor: Editor,
    caretOffset: Ref<Int>,
    caretAdvance: Ref<Int>,
    dataContext: DataContext,
    originalHandler: EditorActionHandler?,
  ): EnterHandlerDelegate.Result {
    if (file !is YaddaFile) return EnterHandlerDelegate.Result.Continue
    val document = editor.document
    val offset = caretOffset.get()
    val line = document.getLineNumber(offset)
    val lines = document.charsSequence.toString().lines()

    val fence = FENCE.matchEntire(lines[line]) ?: return EnterHandlerDelegate.Result.Continue
    // Not Gherkin's either: it would insert quote marks. Anything but the end of an unclosed opening fence is a plain Enter.
    if (offset != document.getLineEndOffset(line)) return EnterHandlerDelegate.Result.Default
    val fencesAbove = lines.take(line).count { FENCE.matches(it) }
    if (fencesAbove % 2 == 1) return EnterHandlerDelegate.Result.Default
    val next = lines.drop(line + 1).firstOrNull { it.isNotBlank() }
    if (next != null && !startsSomethingElse(next, file)) return EnterHandlerDelegate.Result.Default

    val indent = fence.groupValues[1]
    val closing = fence.groupValues[2]
    EditorModificationUtilEx.insertStringAtCaret(editor, "\n$indent\n$indent$closing")
    editor.caretModel.moveCaretRelatively(-closing.length, -1, false, false, true)
    return EnterHandlerDelegate.Result.Stop
  }

  /** Whether [line] is one Yadda reads as a step, a heading, a tag or a comment, whatever block it is in. */
  private fun startsSomethingElse(line: String, file: YaddaFile): Boolean {
    val text = line.trim()
    if (text.startsWith("#") || text.startsWith("@") || text.startsWith("*")) return true
    val word = text.takeWhile { !it.isWhitespace() && it != ':' }
    return word in HEADINGS || file.stepKeywords.any { it.trim() == word }
  }

  private companion object {
    val HEADINGS = setOf("Feature", "Background", "Scenario", "Examples", "Where", "Rule", "Given", "When", "Then", "And", "But")

    // Yadda's own pattern is /^\s*[|┆]?-{3,}/ and anything after the dashes is a remark. Group 1 is
    // the indent and group 2 the fence itself, which is also what closes the block.
    val FENCE = Regex("""([ \t]*)([|┆]?-{3,}).*""")
  }
}
