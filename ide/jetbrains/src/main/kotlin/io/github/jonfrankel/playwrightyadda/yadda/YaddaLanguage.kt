package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.lang.Language
import org.jetbrains.plugins.cucumber.psi.GherkinLanguage

/**
 * Yadda's dialect of Gherkin, for files named `*.yadda.feature`. Gherkin is its base language, so
 * everything registered for Gherkin (highlighting, formatting, the Cucumber inspections) applies
 * here too unless the dialect says otherwise.
 */
object YaddaLanguage : Language(GherkinLanguage.INSTANCE, "Yadda") {
  override fun getDisplayName(): String = "Yadda"

  private fun readResolve(): Any = YaddaLanguage
}
