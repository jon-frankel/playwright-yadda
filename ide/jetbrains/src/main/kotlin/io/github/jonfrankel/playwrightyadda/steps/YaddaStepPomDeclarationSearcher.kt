package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.pom.PomDeclarationSearcher
import com.intellij.pom.PomTarget
import com.intellij.psi.PsiElement
import com.intellij.util.Consumer

/**
 * Makes `steps.given("…", fn)` a declaration when the caret is on `given` or on the step text.
 *
 * Without this, Cmd+B on a step definition has nothing to resolve ("Cannot find declaration to go
 * to"). With it, the IDE treats the call as a declaration and shows its usages instead.
 */
class YaddaStepPomDeclarationSearcher : PomDeclarationSearcher() {
  override fun findDeclarationsAt(
    element: PsiElement,
    offsetInElement: Int,
    consumer: Consumer<in PomTarget>,
  ) {
    val call = element as? JSCallExpression ?: return
    val literal = YaddaSteps.stepLiteralOf(call) ?: return

    // Only the method name and the step text, not the receiver (`storefrontSteps`) or the body.
    val offset = call.textRange.startOffset + offsetInElement
    val methodName = (call.methodExpression as JSReferenceExpression).referenceNameElement
    val onMethodName = methodName?.textRange?.containsOffset(offset) == true
    if (!onMethodName && !literal.textRange.containsOffset(offset)) return

    // Only a call the Cucumber.js plugin also treats as a definition is a declaration.
    YaddaSteps.definitionOf(literal) ?: return
    consumer.consume(YaddaStepPomTarget(literal, literal.stringValue ?: literal.text))
  }
}
