package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.psi.formatter.DocumentBasedFormattingModel
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.impl.source.tree.TreeUtil

/** Formats a Yadda file as Gherkin formats its own, with [YaddaBlock] for the rows that start with a cell. */
class YaddaFormattingModelBuilder : FormattingModelBuilder {
  override fun createModel(formattingContext: FormattingContext): FormattingModel {
    val file = formattingContext.containingFile
    val root = TreeUtil.getFileElement(SourceTreeToPsiMap.psiElementToTree(formattingContext.psiElement) as TreeElement)
    return DocumentBasedFormattingModel(YaddaBlock(root), file.project, formattingContext.codeStyleSettings, file.fileType, file)
  }
}
