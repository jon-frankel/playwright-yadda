package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReferenceBase

/**
 * A step of a Markdown feature, as a reference to the definition that runs it.
 *
 * The reference sits on the whole file, as [YaddaLibraryNameReference] does, because a step's line has
 * no element of its own in the Markdown tree that is the step: it is a paragraph, a list item or a
 * heading, depending on how it was written.
 */
class YaddaMarkdownStepReference(
  file: PsiFile,
  range: TextRange,
  private val definition: PsiElement,
) : PsiReferenceBase<PsiFile>(file, range, true) {
  override fun resolve(): PsiElement = definition
}
