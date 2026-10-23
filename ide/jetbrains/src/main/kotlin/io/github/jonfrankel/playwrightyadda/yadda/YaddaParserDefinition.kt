package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.cucumber.psi.GherkinParserDefinition

/** [YaddaParser] over [YaddaLexer]. The PSI is Gherkin's, so every Gherkin feature reads it. */
class YaddaParserDefinition : ParserDefinition {
  private val gherkin = GherkinParserDefinition()

  override fun createLexer(project: Project?): Lexer = YaddaLexer(gherkin.createLexer(project))

  override fun createParser(project: Project?): PsiParser = YaddaParser()

  override fun getFileNodeType(): IFileElementType = YADDA_FILE

  override fun getCommentTokens(): TokenSet = gherkin.commentTokens

  override fun getStringLiteralElements(): TokenSet = gherkin.stringLiteralElements

  override fun createElement(node: ASTNode): PsiElement = gherkin.createElement(node)

  override fun createFile(viewProvider: FileViewProvider): PsiFile = YaddaFile(viewProvider)

  override fun spaceExistenceTypeBetweenTokens(left: ASTNode, right: ASTNode): ParserDefinition.SpaceRequirements =
    gherkin.spaceExistenceTypeBetweenTokens(left, right)

  companion object {
    val YADDA_FILE = IFileElementType("YADDA_FILE", YaddaLanguage)
  }
}
