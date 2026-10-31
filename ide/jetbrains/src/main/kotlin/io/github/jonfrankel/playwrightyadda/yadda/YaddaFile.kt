package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFile
import org.jetbrains.plugins.cucumber.psi.GherkinLexer
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes
import org.jetbrains.plugins.cucumber.psi.i18n.JsonGherkinKeywordProvider
import org.jetbrains.plugins.cucumber.psi.impl.GherkinFileImpl

/**
 * What [GherkinFileImpl] is for Gherkin. That class fixes its language to Gherkin, so a file of
 * another language can't extend it.
 */
class YaddaFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, YaddaLanguage), GherkinFile {
  /** The file's own type: Yadda for `*.yadda.feature`, and for a substituted `.feature` file, Gherkin's. */
  override fun getFileType(): FileType = viewProvider.fileType

  override fun toString(): String = "YaddaFile:$name"

  /** What Yadda's own parser makes of the file, kept until the file changes. */
  val grammar: YaddaGrammar.Result
    get() = CachedValuesManager.getCachedValue(this) {
      val settings = YaddaSettings.of(project)
      CachedValueProvider.Result.create(YaddaGrammar.analyze(text, settings.vocabulary), this, settings.tracker)
    }

  /** What Yadda makes of [step], a step of this file. */
  fun yaddaStep(step: PsiElement): YaddaGrammar.Step? {
    val line = viewProvider.document?.getLineNumber(step.textOffset)?.plus(1) ?: return null
    return grammar.steps[line]
  }

  /** The text of the 1-based [line], without its indentation and trailing space. */
  fun lineRange(line: Int): TextRange = FeatureLines.range(this, line)

  override fun getStepKeywords(): List<String> {
    val provider = JsonGherkinKeywordProvider.getKeywordProvider(this)
    return provider.getKeywordsTable(localeLanguage)?.stepKeywords?.toList().orEmpty()
  }

  override fun getLocaleLanguage(): String {
    var node = node.firstChildNode
    while (node != null) {
      if (node.elementType == GherkinTokenTypes.COMMENT) {
        GherkinLexer.fetchLocationLanguage(node.text.substring(1).trim())?.let { return it }
      } else if (node.elementType != TokenType.WHITE_SPACE) {
        break
      }
      node = node.treeNext
    }
    return GherkinFileImpl.getDefaultLocale()
  }

  override fun getFeatures(): Array<GherkinFeature> = findChildrenByClass(GherkinFeature::class.java)

  override fun findElementAt(offset: Int): PsiElement? {
    val result = super.findElementAt(offset)
    if (result != null || offset != textLength) return result
    var last: PsiElement? = lastChild
    while (last?.lastChild != null) last = last.lastChild
    return last
  }
}
