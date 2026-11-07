package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.lang.javascript.psi.JSArgumentList
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import io.github.jonfrankel.playwrightyadda.libraries.FeatureTags
import io.github.jonfrankel.playwrightyadda.libraries.LibraryRegistries
import io.github.jonfrankel.playwrightyadda.libraries.enabledLibraries
import io.github.jonfrankel.playwrightyadda.yadda.YaddaFile
import io.github.jonfrankel.playwrightyadda.yadda.YaddaMarkdown
import io.github.jonfrankel.playwrightyadda.yadda.YaddaSettings
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
  /**
   * False only if [definition] is certainly in a library that the scenario of [step] does not enable.
   * [offset] is where the step is in its file; for an element that is the whole file, it says which step.
   */
  fun isEnabledFor(definition: PsiElement, step: PsiElement, offset: Int = step.textRange.startOffset): Boolean {
    if (DumbService.isDumb(definition.project)) return true

    val library = libraryOf(definition) ?: return true
    val registries = LibraryRegistries.containing(library)
    if (registries.isEmpty()) return true

    val file = step.containingFile ?: return true
    val tags = featureTags(file)
    val libraries = tags.librariesAt(tags.lineOf(offset)) ?: return true

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
  fun narrowedDefinitions(step: GherkinStep): List<PsiElement>? = narrowed(step, definitionsOf(step))

  /** What [step] can go to: [narrowedDefinitions], or every definition the IDE found if nothing is ruled out. */
  fun candidateDefinitions(step: GherkinStep): Candidates = candidates(definitionsOf(step), step)

  /** The same for [definitions] already found for the step at [step], whatever the file it is in. */
  fun candidates(definitions: List<PsiElement>, step: PsiElement): Candidates {
    val narrowed = narrowed(step, definitions)
    if (narrowed != null) {
      return Candidates(narrowed, true)
    }
    val valid = YaddaStepFilter.filterCandidates(definitions, step)
    return Candidates(valid, valid.size < definitions.size)
  }

  class Candidates(val definitions: List<PsiElement>, val narrowed: Boolean)

  /**
   * What the Cucumber plugin resolves [step] to, and for an outline step the definitions of the other examples
   * rows too: the plugin takes one text per step, and Yadda runs every row.
   */
  private fun definitionsOf(step: GherkinStep): List<PsiElement> {
    val resolved =
      step.references
        .filterIsInstance<CucumberStepReference>()
        .firstOrNull()
        ?.multiResolve(false)
        ?.mapNotNull { it.element }
        .orEmpty()
    return (resolved + otherRowDefinitions(step)).distinct()
  }

  private fun otherRowDefinitions(step: GherkinStep): List<PsiElement> {
    val file = step.containingFile as? YaddaFile ?: return emptyList()
    val yadda = file.yaddaStep(step) ?: return emptyList()
    if (yadda.expandedRows(yadda.text).size < 2) return emptyList()
    val loaded = YaddaStepRows.loadDefinitions(step)
    return YaddaStepRows.definitions(loaded, yadda, YaddaSettings.vocabularyOf(step.project)).mapNotNull { it.element }
  }

  private fun narrowed(step: PsiElement, definitions: List<PsiElement>): List<PsiElement>? {
    if (definitions.isEmpty()) return null
    val valid = YaddaStepFilter.filterCandidates(definitions, step)
    val enabled = valid.filter { isEnabledFor(it, step) }
    val candidates = if (enabled.isNotEmpty()) enabled else valid
    return candidates.takeIf { it.size < definitions.size }
  }

  /** The `createStepLibrary()` variable a definition is called on: `storefrontSteps` in `storefrontSteps.given(…)`. */
  private fun libraryOf(definition: PsiElement): PsiElement? {
    val call = (definition.parent as? JSArgumentList)?.parent as? JSCallExpression
      ?: definition as? JSCallExpression
      ?: return null
    val receiver = (call.methodExpression as? JSReferenceExpression)?.qualifier as? JSReferenceExpression
    return receiver?.resolve()
  }

  fun pathTagsOf(file: PsiFile): List<String> {
    val vFile = file.virtualFile ?: file.viewProvider.virtualFile ?: return emptyList()
    val segments = vFile.path.replace('\\', '/').split('/')
    val dirSegments = segments.dropLast(1)
    val tags = mutableListOf<String>()
    for (segment in dirSegments) {
      if (segment.startsWith("@")) {
        val raw = segment.drop(1).trim()
        val split = raw.indexOfFirst { it == ':' || it == '=' }
        val tag = if (split < 0) raw.lowercase() else raw.substring(0, split).lowercase() + raw.substring(split)
        if (tag.isNotEmpty() && !tag.any(Char::isWhitespace) && tag !in tags) {
          tags.add(tag)
        }
      }
    }
    return tags
  }

  private fun featureTags(file: PsiFile): FeatureTags =
    CachedValuesManager.getCachedValue(file) {
      val settings = YaddaSettings.of(file.project)
      val pathTags = pathTagsOf(file)
      val tags = if (YaddaMarkdown.isFeature(file)) FeatureTags.parseMarkdown(file.text, settings.vocabulary, pathTags)
                 else FeatureTags.parse(file.text, settings.vocabulary, pathTags)
      CachedValueProvider.Result.create(tags, file, settings.tracker)
    }
}
