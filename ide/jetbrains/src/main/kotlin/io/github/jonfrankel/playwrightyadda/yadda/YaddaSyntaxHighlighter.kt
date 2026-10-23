package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.cucumber.psi.GherkinSyntaxHighlighter
import org.jetbrains.plugins.cucumber.psi.i18n.JsonGherkinKeywordProvider

/**
 * Gherkin's colours, over the tokens [YaddaLexer] makes. Gherkin's highlighter would lex with Gherkin's
 * lexer, so rows without pipes, `Where:` and a fenced block would be coloured as the plain text they
 * look like to it.
 */
class YaddaSyntaxHighlighter : GherkinSyntaxHighlighter(JsonGherkinKeywordProvider.getKeywordProvider(true)) {
  override fun getHighlightingLexer(): Lexer = YaddaLexer(super.getHighlightingLexer())
}

class YaddaSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
  override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter = YaddaSyntaxHighlighter()
}
