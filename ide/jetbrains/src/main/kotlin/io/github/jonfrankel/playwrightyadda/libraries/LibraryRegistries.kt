package io.github.jonfrankel.playwrightyadda.libraries

import com.intellij.lang.javascript.psi.JSArrayLiteralExpression
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor

/**
 * A `createLibraryRegistry({ key: library, … }, options)` call, as far as it can be read statically.
 *
 * [defaultLibraries] and [inheritance] are null when they are set to something other than literals;
 * callers must then assume nothing about which libraries a scenario enables.
 */
class LibraryRegistry(
  private val libraries: List<Pair<String, PsiElement>>,
  val defaultLibraries: List<String>?,
  val inheritance: String?,
) {
  /** The registry keys under which [library] is registered (keys are case-insensitive, so lower-cased). */
  fun keysOf(library: PsiElement): List<String> =
    libraries.filter { (_, candidate) -> library.manager.areElementsEquivalent(candidate, library) }.map { it.first }
}

object LibraryRegistries {
  private val REGISTRY_FACTORIES = setOf("createLibraryRegistry", "LibraryRegistryManager")
  private val SUITE_FUNCTIONS = setOf("defineFeatureSuite", "defineFeature")

  /** The registries that [library] (a `createStepLibrary()` variable) is part of. */
  fun containing(library: PsiElement): List<LibraryRegistry> =
    registries(library.project).filter { it.keysOf(library).isNotEmpty() }

  private fun registries(project: Project): List<LibraryRegistry> =
    CachedValuesManager.getManager(project).getCachedValue(project) {
      CachedValueProvider.Result.create(discover(project), PsiModificationTracker.MODIFICATION_COUNT)
    }

  private class Draft(
    val call: JSCallExpression,
    val libraries: List<Pair<String, PsiElement>>,
    var defaults: List<String>?,
    var inheritance: String?,
  )

  private fun discover(project: Project): List<LibraryRegistry> {
    val drafts =
      callsNamed(project, REGISTRY_FACTORIES).mapNotNull { call -> draftOf(call) }

    for (suite in callsNamed(project, SUITE_FUNCTIONS)) applySuiteOverrides(suite, drafts)

    return drafts.map { LibraryRegistry(it.libraries, it.defaults, it.inheritance) }
  }

  private fun draftOf(call: JSCallExpression): Draft? {
    val entries = call.arguments.firstOrNull() as? JSObjectLiteralExpression ?: return null
    val libraries =
      entries.properties.mapNotNull { property ->
        val key = property.name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val target = (property.value as? JSReferenceExpression)?.resolve() ?: return@mapNotNull null
        key to target
      }

    var defaults: List<String>? = listOf("common")
    var inheritance: String? = "merge"
    val optionsArgument = call.arguments.getOrNull(1)
    if (optionsArgument != null) {
      val options = optionsArgument as? JSObjectLiteralExpression
      if (options == null || options.findProperty("annotationKey") != null) {
        // Not a literal, or reads a different annotation: no way to know what a scenario enables.
        defaults = null
        inheritance = null
      } else {
        options.findProperty("defaultLibraries")?.let { defaults = readStrings(it.value)?.map(String::lowercase) }
        options.findProperty("inheritance")?.let { inheritance = readInheritance(it.value) }
      }
    }
    return Draft(call, libraries, defaults, inheritance)
  }

  /**
   * `defineFeatureSuite({ registry, defaultLibraries, libraryInheritance })` overrides the registry's
   * own options. If we cannot tell which registry such a call uses, no registry's defaults are known.
   */
  private fun applySuiteOverrides(suite: JSCallExpression, drafts: List<Draft>) {
    val options = suite.arguments.firstOrNull() as? JSObjectLiteralExpression ?: return
    val defaultsProperty = options.findProperty("defaultLibraries")
    val inheritanceProperty = options.findProperty("libraryInheritance")
    if (defaultsProperty == null && inheritanceProperty == null) return

    val draft = draftUsedBy(options, drafts)
    if (draft == null) {
      drafts.forEach { it.defaults = null; it.inheritance = null }
      return
    }
    defaultsProperty?.let { draft.defaults = readStrings(it.value)?.map(String::lowercase) }
    inheritanceProperty?.let { draft.inheritance = readInheritance(it.value) }
  }

  private fun draftUsedBy(suiteOptions: JSObjectLiteralExpression, drafts: List<Draft>): Draft? {
    val registry = (suiteOptions.findProperty("registry")?.value as? JSReferenceExpression)?.resolve()
    val initializer = (registry as? JSVariable)?.initializer ?: return null
    return drafts.firstOrNull { it.call == initializer }
  }

  private fun callsNamed(project: Project, names: Set<String>): List<JSCallExpression> {
    val files = LinkedHashSet<PsiFile>()
    val helper = PsiSearchHelper.getInstance(project)
    val scope = GlobalSearchScope.projectScope(project)
    for (name in names) {
      helper.processAllFilesWithWord(name, scope, Processor { files.add(it); true }, true)
    }
    return files.flatMap { file ->
      PsiTreeUtil.findChildrenOfType(file, JSCallExpression::class.java).filter { call ->
        (call.methodExpression as? JSReferenceExpression)?.referenceName in names
      }
    }
  }

  private fun readString(expression: JSExpression?): String? =
    (expression as? JSLiteralExpression)?.takeIf { it.isQuotedLiteral }?.stringValue

  private fun readStrings(expression: JSExpression?): List<String>? {
    val array = expression as? JSArrayLiteralExpression ?: return null
    return array.expressions.map { readString(it) ?: return null }
  }

  private fun readInheritance(expression: JSExpression?): String? =
    readString(expression)?.takeIf { it == "merge" || it == "override" }
}
