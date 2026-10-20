package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.pom.PomNamedTarget
import com.intellij.psi.DelegatePsiTarget

/** The declaration a step-definition call stands for; Find Usages searches for references to this. */
class YaddaStepPomTarget(literal: JSLiteralExpression, private val stepName: String) :
  DelegatePsiTarget(literal), PomNamedTarget {
  override fun getName(): String = stepName
}
