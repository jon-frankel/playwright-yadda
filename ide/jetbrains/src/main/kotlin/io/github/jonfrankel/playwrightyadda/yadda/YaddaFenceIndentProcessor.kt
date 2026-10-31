package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinPystring
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/**
 * Moves a fenced block that is not a table (JSON, CSV, prose) with the step above it. The formatter
 * leaves a block's text alone, so after the step has been re-indented the block is where it was.
 *
 * The opening fence goes one indent in from its step, where a table's fences go, and the rest of the block
 * moves by as much. Yadda takes a block's text from the column of its opening fence and rejects a line
 * that starts before it, so shifting every line by the same amount does not change what the step is
 * given. A block is left alone if that would cut into the text of a line, or if it is indented with tabs.
 */
class YaddaFenceIndentProcessor : PostFormatProcessor {
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    val file = source.containingFile
    if (file is YaddaFile) process(file, source.textRange, settings)
    return source
  }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    if (source !is YaddaFile) return rangeToReformat
    val change = process(source, rangeToReformat, settings)
    return TextRange(rangeToReformat.startOffset, (rangeToReformat.endOffset + change).coerceAtLeast(rangeToReformat.startOffset))
  }

  /** Moves the blocks that overlap [range]. Returns how many characters that added to the text. */
  private fun process(file: PsiFile, range: TextRange, settings: CodeStyleSettings): Int {
    val options = settings.getIndentOptionsByFile(file)
    if (options.USE_TAB_CHARACTER) return 0
    val manager = PsiDocumentManager.getInstance(file.project)
    val document = manager.getDocument(file) ?: return 0
    manager.commitDocument(document)

    val blocks = PsiTreeUtil.findChildrenOfType(file, GherkinPystring::class.java)
      .filter { it.textRange.intersects(range) }
      .sortedByDescending { it.textRange.startOffset }
    var change = 0
    for (block in blocks) {
      val step = block.parent as? GherkinStep ?: continue
      val text = document.charsSequence
      val stepLine = document.getLineNumber(step.textRange.startOffset)
      val stepIndent = indentOf(text, document.getLineStartOffset(stepLine), step.textRange.startOffset) ?: continue
      val firstLine = document.getLineNumber(block.textRange.startOffset)
      val fenceIndent = indentOf(text, document.getLineStartOffset(firstLine), block.textRange.startOffset) ?: continue
      val shift = stepIndent + options.INDENT_SIZE - fenceIndent
      if (shift == 0) continue

      val lastLine = document.getLineNumber((block.textRange.endOffset - 1).coerceAtLeast(block.textRange.startOffset))
      val lines = (firstLine..lastLine).map { document.getLineStartOffset(it) to document.getLineEndOffset(it) }
      val indents = lines.map { (from, to) -> if (isBlank(text, from, to)) null else leadingSpaces(text, from, to) }
      if (indents.any { it != null && it < 0 }) continue
      if (shift < 0 && indents.any { it != null && it < -shift }) continue

      // From the last line up, so that the offsets of the lines still to do hold.
      for (index in lines.indices.reversed()) {
        if (indents[index] == null) continue
        val from = lines[index].first
        if (shift > 0) document.insertString(from, " ".repeat(shift)) else document.deleteString(from, from - shift)
        change += shift
      }
    }
    manager.commitDocument(document)
    return change
  }

  /** The number of spaces from [from] to [at], or null if there is anything else between them. */
  private fun indentOf(text: CharSequence, from: Int, at: Int): Int? =
    if ((from until at).all { text[it] == ' ' }) at - from else null

  private fun isBlank(text: CharSequence, from: Int, to: Int): Boolean = (from until to).all { text[it] == ' ' || text[it] == '\t' }

  /** The spaces a line starts with, or -1 if a tab comes before its text. */
  private fun leadingSpaces(text: CharSequence, from: Int, to: Int): Int {
    var at = from
    while (at < to && text[at] == ' ') at++
    return if (at < to && text[at] == '\t') -1 else at - from
  }
}
