package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

/** Asks for the new pattern of a step definition. The groups in it are kept as they are, and checked when it is changed. */
class YaddaStepRenameDialog(project: Project, private val target: StepRenamer.Target) : DialogWrapper(project) {
  private val input = JBTextField(target.pattern.displayed, 50)

  /** The text typed. */
  val text: String
    get() = input.text

  init {
    title = YaddaStepRenameHandler.TITLE
    init()
  }

  override fun createCenterPanel(): JComponent =
    FormBuilder.createFormBuilder()
      .addLabeledComponent(JBLabel("Rename this step definition, and every step that runs it, to:"), input, true)
      .addComponent(JBLabel("<html>Text outside the parentheses is renamed. The groups in parentheses are kept as they are.</html>"))
      .panel

  override fun getPreferredFocusedComponent(): JComponent = input

  override fun doValidate(): ValidationInfo? = StepRenamer.check(target, input.text)?.let { ValidationInfo(it, input) }
}
