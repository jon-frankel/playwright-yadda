package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReferenceBase
import com.intellij.util.IncorrectOperationException
import io.github.jonfrankel.playwrightyadda.libraries.LibraryNames

/**
 * A library name in `@Libraries=…`, as a reference to the registry property that defines it.
 *
 * The reference sits on the whole file because the IDE's Gherkin lexer splits `@Libraries=a, b` into
 * pieces that don't line up with names.
 *
 * Rename calls [handleElementRename] once per file, however many names it holds, so the first call
 * rewrites every use of [key] in the file and any further call finds nothing left to do.
 */
class YaddaLibraryNameReference(
  file: PsiFile,
  range: TextRange,
  private val property: PsiElement,
  private val key: String,
) : PsiReferenceBase<PsiFile>(file, range, true) {
  override fun resolve(): PsiElement = property

  override fun handleElementRename(newElementName: String): PsiElement {
    val manager = PsiDocumentManager.getInstance(element.project)
    val document = manager.getDocument(element) ?: throw IncorrectOperationException("No document for ${element.name}")
    // Last to first, so rewriting one name doesn't move the ones before it.
    for (use in LibraryNames.parse(document.text).filter { it.name == key }.asReversed()) {
      document.replaceString(use.nameRange.startOffset, use.nameRange.endOffset, newElementName)
    }
    manager.commitDocument(document)
    return element
  }
}
