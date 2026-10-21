package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import io.github.jonfrankel.playwrightyadda.libraries.LibraryNames
import io.github.jonfrankel.playwrightyadda.libraries.LibraryRegistries
import org.jetbrains.plugins.cucumber.psi.GherkinFileType

/**
 * Reports the names in `@Libraries=…` as references to the registry property that defines them, so
 * Cmd+B, Find Usages and rename on `storefront: storefrontSteps` reach the feature files that use it.
 *
 * Names don't say which registry they mean, so a name defined by two registries is a usage of both.
 */
class YaddaLibraryNameReferencesSearch :
  QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
  override fun processQuery(
    queryParameters: ReferencesSearch.SearchParameters,
    consumer: Processor<in PsiReference>,
  ) {
    val property = queryParameters.elementToSearch as? JSProperty ?: return
    if (!LibraryRegistries.isRegistryKey(property)) return
    val key = property.name?.trim()?.lowercase() ?: return

    for (file in featureFiles(queryParameters)) {
      for (occurrence in LibraryNames.parse(file.text)) {
        if (occurrence.name != key) continue
        if (!consumer.process(YaddaLibraryNameReference(file, occurrence.nameRange, property, key))) return
      }
    }
  }

  private fun featureFiles(parameters: ReferencesSearch.SearchParameters): List<PsiFile> {
    val project = parameters.project
    val manager = PsiManager.getInstance(project)
    return when (val scope = parameters.effectiveSearchScope) {
      is GlobalSearchScope ->
        FileTypeIndex.getFiles(GherkinFileType.INSTANCE, scope).mapNotNull { manager.findFile(it) }
      is LocalSearchScope -> scope.scope.mapNotNull { it.containingFile }.distinct().filter { it.fileType == GherkinFileType.INSTANCE }
      else -> emptyList()
    }
  }
}
