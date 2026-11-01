package io.github.jonfrankel.playwrightyadda.libraries

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSInitializerOwner
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Whether a `createStepLibrary()` variable is in `"fixtures"` mode, where a step's first parameter is the
 * scenario's Playwright fixtures, as a destructuring pattern, and the captured arguments follow it.
 *
 * That is so for `createStepLibrary(dictionary, "fixtures", language)`, and for the `createStepLibrary`
 * that `createBdd(test)` returns, however it is reached: `createBdd(test).createStepLibrary()`,
 * `const { createStepLibrary } = createBdd(test)` (also when exported from another module, which the IDE resolves) or
 * `const bdd = createBdd(test); bdd.createStepLibrary()`. A call with another qualifier, such as the namespace of
 * `import * as yadda from "playwright-yadda"`, is read by its mode argument like an unqualified one.
 */
object LibraryModes {
  private const val MAX_HOPS = 5

  fun isFixtures(library: JSVariable): Boolean {
    val call = library.initializer as? JSCallExpression ?: return false
    return isFixtures(call)
  }

  fun isFixtures(call: JSCallExpression): Boolean {
    val callee = call.methodExpression as? JSReferenceExpression ?: return false
    val qualifier = callee.qualifier
    if (qualifier != null) {
      if (comesFromCreateBdd(qualifier, 0)) return true
    } else if (declaredByCreateBdd(callee)) {
      return true
    }
    return stringValue(call.arguments.getOrNull(1), 0) == "fixtures"
  }

  /** True when [element] is, or was initialised from, a `createBdd(…)` call. */
  private fun comesFromCreateBdd(element: PsiElement?, hops: Int): Boolean {
    if (element == null || hops > MAX_HOPS) return false
    return when (element) {
      is JSCallExpression -> (element.methodExpression as? JSReferenceExpression)?.referenceName == "createBdd"
      is JSReferenceExpression -> comesFromCreateBdd(element.resolve(), hops + 1)
      is JSVariable -> comesFromCreateBdd(initializerOf(element), hops + 1)
      else -> false
    }
  }

  /**
   * `createStepLibrary` as `const { createStepLibrary } = createBdd(test)` declares it. The IDE resolves a name
   * taken out of a pattern, in the same file, to a copy that has no parents, so that declaration is looked for
   * by name in the file. One exported from another module resolves to its declaration.
   */
  private fun declaredByCreateBdd(reference: JSReferenceExpression): Boolean {
    val resolved = reference.resolve() as? JSVariable
    if (resolved != null && comesFromCreateBdd(resolved, 0)) return true
    val name = reference.referenceName ?: return false
    return PsiTreeUtil.findChildrenOfType(reference.containingFile, JSVariable::class.java)
      .any { it.name == name && comesFromCreateBdd(it, 0) }
  }

  /** The initializer of [variable], which for a name in `const { a } = init` is the one of the whole pattern. */
  private fun initializerOf(variable: JSVariable): JSExpression? =
    variable.initializer ?: PsiTreeUtil.getParentOfType(variable, JSInitializerOwner::class.java, true)?.initializer

  /** The text of a string literal, or of a `const` the expression names. */
  private fun stringValue(expression: JSExpression?, hops: Int): String? {
    if (hops > MAX_HOPS) return null
    return when (expression) {
      is JSLiteralExpression -> expression.takeIf { it.isQuotedLiteral }?.stringValue
      is JSReferenceExpression -> (expression.resolve() as? JSVariable)?.initializer.let { stringValue(it, hops + 1) }
      else -> null
    }
  }
}
