package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.lang.javascript.psi.JSArgumentList
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.cucumber.steps.CucumberStepHelper

/**
 * What counts as a Yadda step definition: `<anything>.given("text" | /regex/, fn)`.
 *
 * WebStorm's Cucumber.js plugin indexes every call whose method name is a Gherkin keyword and ignores
 * the receiver, which is why `storefrontSteps.given(...)` is already found when navigating from a
 * `.feature` file. This object mirrors that rule syntactically and then confirms against the plugin's
 * own step-definition list, so the two can never disagree about what a step definition is.
 */
object YaddaSteps {
  // Same rule as the Cucumber.js plugin's indexer: lower-cased method name vs. the English step keywords.
  private val STEP_KEYWORDS = setOf("given", "when", "then", "and", "but")

  /** Syntax-only check, cheap enough to run on every PSI walk. */
  fun isStepCallCandidate(call: JSCallExpression): Boolean = stepLiteralOf(call) != null

  /** The first argument of a keyword-named call when it is a string or regex literal. */
  fun stepLiteralOf(call: JSCallExpression): JSLiteralExpression? {
    val callee = call.methodExpression as? JSReferenceExpression ?: return null
    val name = callee.referenceName ?: return null
    if (name.lowercase() !in STEP_KEYWORDS) return null

    val literal = call.arguments.firstOrNull() as? JSLiteralExpression ?: return null
    return literal.takeIf { it.isQuotedLiteral || it.isRegExpLiteral }
  }

  /** The step-definition literal that [element] stands for, if it is one. Syntax only. */
  fun stepLiteralOf(element: PsiElement): JSLiteralExpression? {
    if (element is PomTargetPsiElement) {
      return (element.target as? YaddaStepPomTarget)?.navigationElement as? JSLiteralExpression
    }
    if (element !is JSLiteralExpression) return null

    val call = (element.parent as? JSArgumentList)?.parent as? JSCallExpression ?: return null
    return element.takeIf { stepLiteralOf(call) === it }
  }

  /** The Cucumber.js plugin's own definition for [literal], or null if the plugin does not index it. */
  fun definitionOf(literal: JSLiteralExpression): AbstractStepDefinition? {
    val module = ModuleUtilCore.findModuleForPsiElement(literal) ?: return null
    val manager = literal.manager
    return CucumberStepHelper.loadStepsFor(module).firstOrNull { definition ->
      definition.element?.let { manager.areElementsEquivalent(it, literal) } == true
    }
  }
}
