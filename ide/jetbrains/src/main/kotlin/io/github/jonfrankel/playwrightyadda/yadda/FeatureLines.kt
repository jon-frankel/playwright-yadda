package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile

/** Where the lines of a feature file are, for the problems Yadda reports against a line number. */
object FeatureLines {
  /** The offset where the 1-based [line] starts. */
  fun start(file: PsiFile, line: Int): Int = StringUtil.lineColToOffset(file.text, line - 1, 0).coerceAtLeast(0)

  /** The text of the 1-based [line], without its indentation and trailing space. */
  fun range(file: PsiFile, line: Int): TextRange {
    val text = file.text
    val start = start(file, line)
    val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
    val trimmed = text.substring(start, end)
    val lead = trimmed.length - trimmed.trimStart().length
    return TextRange(start + lead, start + trimmed.trimEnd().length.coerceAtLeast(lead))
  }
}
