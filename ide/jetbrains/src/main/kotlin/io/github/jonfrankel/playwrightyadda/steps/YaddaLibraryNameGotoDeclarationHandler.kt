package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import io.github.jonfrankel.playwrightyadda.libraries.LibraryNames
import io.github.jonfrankel.playwrightyadda.libraries.LibraryRegistries
import org.jetbrains.plugins.cucumber.psi.GherkinFile

/**
 * Cmd+B on a name in `@Libraries=storefront, admin` goes to where the registry defines that name:
 * the `storefront: storefrontSteps` property of `createLibraryRegistry({ … })`.
 *
 * The property rather than the library it points at, because it is what the name actually means
 * (the key may differ from the variable, as in `"wp-common": wpCommonSteps`); one more Cmd+B on its
 * value reaches the library. A name that no registry defines goes nowhere, so a typo isn't mistaken
 * for a link. The tag is read from the line, not the PSI, because the IDE's Gherkin lexer ends a tag at
 * its first space and so splits `@Libraries=a, b` into pieces.
 */
class YaddaLibraryNameGotoDeclarationHandler : GotoDeclarationHandler {
  override fun getGotoDeclarationTargets(
    sourceElement: PsiElement?,
    offset: Int,
    editor: Editor?,
  ): Array<PsiElement>? {
    val file = sourceElement?.containingFile as? GherkinFile ?: return null
    val name = LibraryNames.nameAt(file.text, offset) ?: return null

    val definitions = LibraryRegistries.definitionsOf(file.project, name)
    return definitions.takeIf { it.isNotEmpty() }?.toTypedArray()
  }
}
