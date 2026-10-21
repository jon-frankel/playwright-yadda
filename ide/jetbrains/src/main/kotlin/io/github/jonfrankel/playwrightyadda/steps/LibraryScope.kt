package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.lang.javascript.psi.JSArgumentList
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import io.github.jonfrankel.playwrightyadda.libraries.FeatureTags
import io.github.jonfrankel.playwrightyadda.libraries.LibraryRegistries
import io.github.jonfrankel.playwrightyadda.libraries.enabledLibraries
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference

/**
 * Whether a step definition can run for a given step, going by `@Libraries`.
 *
 * Only ever rules a definition out when everything needed is statically readable: the definition
 * belongs to a known registry, the step is in a plain scenario, and the registry's defaults are
 * literals. Anything else counts as enabled, so the IDE never shows less than it did before.
 */
object LibraryScope {
  /** False only if [definition] is certainly in a library that the scenario of [step] does not enable. */
  fun isEnabledFor(definition: JSLiteralExpression, step: PsiElement): Boolean {
    if (DumbService.isDumb(definition.project)) return true

    val library = libraryOf(definition) ?: return true
    val registries = LibraryRegistries.containing(library)
    if (registries.isEmpty()) return true

    val file = step.containingFile ?: return true
    val tags = featureTags(file)
    val libraries = tags.librariesAt(tags.lineOf(step.textRange.startOffset)) ?: return true

    for (registry in registries) {
      val defaults = registry.defaultLibraries ?: return true
      val inheritance = registry.inheritance ?: return true
      val enabled = enabledLibraries(defaults, inheritance, libraries.feature, libraries.scenario)
      if (registry.keysOf(library).any { it in enabled }) return true
    }
    return false
  }

  /**
   * The definitions [step] runs when `@Libraries` rules some of the candidates out, else null.
   *
   * Null means "nothing to narrow": fewer than two candidates, nothing ruled out, or everything ruled
   * out (the step is defined, just not for this scenario). Callers then keep the IDE's own result.
   */
  fun narrowedDefinitions(step: GherkinStep): List<PsiElement>? {
    val reference = step.references.filterIsInstance<CucumberStepReference>().firstOrNull() ?: return null
    val definitions = reference.multiResolve(false).mapNotNull { it.element }
    if (definitions.size < 2) return null

    val enabled = definitions.filter { it !is JSLiteralExpression || isEnabledFor(it, step) }
    return enabled.takeIf { it.isNotEmpty() && it.size < definitions.size }
  }

  /** The `createStepLibrary()` variable a definition is called on: `storefrontSteps` in `storefrontSteps.given(…)`. */
  private fun libraryOf(definition: JSLiteralExpression): PsiElement? {
    val call = (definition.parent as? JSArgumentList)?.parent as? JSCallExpression ?: return null
    val receiver = (call.methodExpression as? JSReferenceExpression)?.qualifier as? JSReferenceExpression
    return receiver?.resolve()
  }

  private fun featureTags(file: PsiElement): FeatureTags =
    CachedValuesManager.getCachedValue(file) {
      CachedValueProvider.Result.create(FeatureTags.parse(file.text), file)
    }
}
