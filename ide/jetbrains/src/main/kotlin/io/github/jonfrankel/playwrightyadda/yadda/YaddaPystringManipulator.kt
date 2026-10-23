package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.GherkinPyStringManipulator
import org.jetbrains.plugins.cucumber.psi.GherkinPystring

/**
 * Edits a Yadda block's content, which is what the "Edit JSON Fragment" window writes back. Gherkin's
 * manipulator assumes `"""` around the content, so on a dashed block it replaces the fences with
 * quote marks. This one keeps the fences and edits between them, and leaves other files to Gherkin's.
 */
class YaddaPystringManipulator : AbstractElementManipulator<GherkinPystring>() {
  private val gherkin = GherkinPyStringManipulator()

  override fun getRangeInElement(element: GherkinPystring): TextRange {
    if (element.containingFile !is YaddaFile) return gherkin.getRangeInElement(element)
    return FencedBlock.of(element.text)?.content ?: gherkin.getRangeInElement(element)
  }

  override fun handleContentChange(element: GherkinPystring, range: TextRange, newContent: String): GherkinPystring? {
    if (element.containingFile !is YaddaFile) return gherkin.handleContentChange(element, range, newContent)
    val dummy = PsiFileFactory.getInstance(element.project).createFileFromText(
      "block.yadda.feature",
      YaddaFileType,
      "Feature: F\n  Scenario: S\n    Given a step\n${range.replace(element.text, newContent)}",
    )
    val parsed = PsiTreeUtil.findChildOfType(dummy, GherkinPystring::class.java) ?: return element
    return element.replace(parsed) as? GherkinPystring
  }
}
