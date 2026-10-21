package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

/**
 * A step definition as a row in the "Choose Declaration" popup.
 *
 * Two definitions of the same step text render identically, because a bare string literal has no
 * location of its own. This shows the file each one lives in, relative to the project, and
 * navigates to the literal.
 */
class YaddaStepTarget(private val literal: JSLiteralExpression) : FakePsiElement() {
  override fun getParent(): PsiElement = literal.parent

  override fun getContainingFile(): PsiFile = literal.containingFile

  override fun getNavigationElement(): PsiElement = literal

  override fun isValid(): Boolean = literal.isValid

  override fun getText(): String = literal.text

  override fun getName(): String = literal.text

  override fun getPresentableText(): String = literal.text

  override fun getLocationString(): String? {
    val file = literal.containingFile.virtualFile ?: return null
    val root = literal.project.guessProjectDir() ?: return file.name
    return VfsUtilCore.getRelativePath(file, root, '/') ?: file.path
  }

  override fun getIcon(open: Boolean): Icon? = null

  override fun canNavigate(): Boolean = literal.canNavigate()

  override fun navigate(requestFocus: Boolean) = literal.navigate(requestFocus)

  override fun isEquivalentTo(another: PsiElement?): Boolean =
    another === this || literal.isEquivalentTo(another)
}
