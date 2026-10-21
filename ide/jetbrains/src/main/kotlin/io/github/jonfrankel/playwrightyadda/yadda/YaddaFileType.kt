package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.fileTypes.LanguageFileType
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import javax.swing.Icon

object YaddaFileType : LanguageFileType(YaddaLanguage) {
  override fun getName(): String = "Yadda"

  override fun getDescription(): String = "Yadda feature"

  override fun getDefaultExtension(): String = "yadda.feature"

  override fun getIcon(): Icon? = GherkinFileType.INSTANCE.icon
}
