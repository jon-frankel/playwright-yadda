package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.PsiElement
import org.intellij.plugins.intelliLang.inject.InjectorUtils
import org.jetbrains.plugins.cucumber.psi.GherkinPystring

/**
 * Injects the language a block names after its dashes: `--- json` is JSON, `--- sql` is SQL, and so
 * on for any language the IDE can find by that name. A remark that names none, such as
 * `--- the response body`, injects nothing.
 */
class YaddaLanguageInjector : MultiHostInjector {
  override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(GherkinPystring::class.java)

  override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
    val host = context as? GherkinPystring ?: return
    val block = FencedBlock.of(host.text) ?: return
    if (block.remark.isEmpty() || block.content.isEmpty) return
    val language = InjectorUtils.getLanguageByString(block.remark) ?: return
    // A name the IDE only knows as a plain-text file type (csv, when no CSV plugin is installed) has no language to add.
    if (language == PlainTextLanguage.INSTANCE) return
    registrar.startInjecting(language).addPlace(null, null, host, block.content).doneInjecting()
  }
}
