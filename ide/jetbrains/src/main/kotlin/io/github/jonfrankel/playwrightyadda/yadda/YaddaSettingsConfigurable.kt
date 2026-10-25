package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel

/** Settings | Tools | Playwright Yadda: the language the project's features are written in. */
class YaddaSettingsConfigurable(private val project: Project) : BoundConfigurable("Playwright Yadda") {
  override fun createPanel(): DialogPanel {
    val settings = YaddaSettings.of(project)
    return panel {
      row("Feature language:") {
        comboBox(Vocabulary.all.map { it.name })
          .bindItem({ settings.language }, { if (it != null) settings.language = it })
          .comment(
            "The language of the headings (<code>Feature:</code>, <code>Scenario:</code>, …) and step keywords " +
              "in your features. Set it to what you pass as <code>language</code> to <code>defineFeatureSuite</code> " +
              "and <code>createStepLibrary</code>.",
          )
      }
    }
  }

  override fun apply() {
    val before = YaddaSettings.of(project).language
    super.apply()
    if (YaddaSettings.of(project).language != before) YaddaSettings.of(project).refresh(project)
  }
}
