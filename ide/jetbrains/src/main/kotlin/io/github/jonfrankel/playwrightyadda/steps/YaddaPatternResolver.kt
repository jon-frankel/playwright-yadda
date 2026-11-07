package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.lang.ecmascript6.psi.ES6ImportExportSpecifier
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.psi.JSBinaryExpression
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSNewExpression
import com.intellij.lang.javascript.psi.JSPsiNamedElementBase
import com.intellij.lang.javascript.psi.JSPsiReferenceElement
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.psi.ecma6.ES6TaggedTemplateExpression
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.psi.PsiElement

/**
 * Statically extracts a regex pattern string from JS/TS expressions used as step patterns,
 * such as:
 * - `new RegExp(...)` / `RegExp(...)`
 * - String templates: `` `^${TRANSACTION}'s (credit|debit) is shown in full$` ``
 * - Tagged templates: `String.raw`...``
 * - String / regex literals: `"foo"`, `/^bar$/`
 * - String concatenations: `"a" + "b"`
 * - Variable / constant references: `MY_PATTERN`
 */
object YaddaPatternResolver {
  fun extractPattern(expression: JSExpression?): String? {
    if (expression == null) return null
    return when (expression) {
      is ES6TaggedTemplateExpression -> {
        val template = expression.templateExpression ?: return null
        extractTemplateString(template)
      }
      is JSStringTemplateExpression -> {
        extractTemplateString(expression)
      }
      is JSLiteralExpression -> {
        when {
          expression.isRegExpLiteral -> expression.text.trim().removeSurrounding("/")
          expression.isQuotedLiteral -> expression.stringValue ?: expression.text.trim().removeSurrounding("\"").removeSurrounding("'")
          else -> null
        }
      }
      is JSNewExpression -> {
        if (isRegExpConstructor(expression.methodExpression)) {
          extractPattern(expression.arguments.firstOrNull())
        } else null
      }
      is JSCallExpression -> {
        if (isRegExpConstructor(expression.methodExpression)) {
          extractPattern(expression.arguments.firstOrNull())
        } else null
      }
      is JSBinaryExpression -> {
        if (expression.operationSign == JSTokenTypes.PLUS) {
          val left = extractPattern(expression.lOperand) ?: return null
          val right = extractPattern(expression.rOperand) ?: return null
          left + right
        } else null
      }
      is JSReferenceExpression -> {
        val decl = resolveDeclaration(expression)
        if (decl is JSVariable) {
          extractPattern(decl.initializer)
        } else null
      }
      else -> null
    }
  }

  private fun isRegExpConstructor(callee: JSExpression?): Boolean {
    val ref = callee as? JSReferenceExpression ?: return false
    return ref.referenceName == "RegExp"
  }

  private fun extractTemplateString(template: JSStringTemplateExpression): String? {
    val sb = StringBuilder()
    for (child in template.node.getChildren(null)) {
      val psi = child.psi
      if (psi is JSExpression) {
        val resolved = extractPattern(psi) ?: return null
        sb.append(resolved)
      } else if (child.elementType == JSTokenTypes.STRING_TEMPLATE_PART) {
        sb.append(child.text)
      }
    }
    return sb.toString()
  }

  private fun resolveDeclaration(element: PsiElement?): PsiElement? {
    var cur = element
    val visited = HashSet<PsiElement>()
    while (cur != null && visited.add(cur)) {
      cur = when (cur) {
        is JSReferenceExpression -> cur.resolve()
        is ES6ImportExportSpecifier -> cur.resolve()
        is JSPsiNamedElementBase -> (cur as? JSPsiReferenceElement)?.resolve() ?: return cur
        else -> return cur
      }
    }
    return cur
  }
}
