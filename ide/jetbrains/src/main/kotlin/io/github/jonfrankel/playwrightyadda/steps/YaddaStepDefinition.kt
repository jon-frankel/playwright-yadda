package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

/**
 * An [AbstractStepDefinition] for dynamic step definitions that WebStorm's Cucumber.js plugin
 * does not index (e.g. `new RegExp(...)`, template literals, string constants).
 */
class YaddaStepDefinition(
  private val patternElement: PsiElement,
  private val regex: String,
) : AbstractStepDefinition(patternElement) {
  override fun getVariableNames(): List<String> = emptyList()
  override fun getCucumberRegex(): String = regex
  override fun getCucumberRegexFromElement(element: PsiElement?): String = regex
}
