package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/**
 * Yadda's Markdown features: a `.md` file that Yadda reads as a feature, kept as the Markdown file
 * the IDE already understands. Nothing here is a language of its own, so the Markdown plugin's
 * editing, preview and highlighting are untouched; the plugin only adds what a feature needs on
 * top of it, from what Yadda's own parser (`YaddaGrammar.analyzeMarkdown`) makes of the text.
 *
 * playwright-yadda parses every `.md` and `.markdown` file under the features directory, and a
 * README there would fail with "A feature must contain one or more scenarios". The IDE can't tell
 * which directory that is, so a file counts as a feature when it could be one: it is in a project
 * that depends on playwright-yadda and has a `Feature:` heading, or it is named `*.yadda.md`.
 *
 * Nothing here names a Markdown class, so the plugin works with the Markdown plugin switched off.
 */
object YaddaMarkdown {
  private val EXTENSIONS = listOf("md", "markdown")
  private val FORCED_NAMES = listOf(".yadda.md", ".yadda.markdown")
  private val MENTIONS_FEATURE = Regex("feature:", RegexOption.IGNORE_CASE)

  /** A step as the IDE sees it in the file: where its first line is, and the text Yadda makes of it. */
  class Site(val step: YaddaGrammar.Step, val line: TextRange, val range: TextRange)

  fun isFeature(file: PsiFile?): Boolean {
    if (file == null || file.language.id != MARKDOWN_LANGUAGE_ID) return false
    // A package.json can change which files count, so any change in the project is a reason to ask again.
    return CachedValuesManager.getCachedValue(file) {
      CachedValueProvider.Result.create(isFeatureNow(file), PsiModificationTracker.MODIFICATION_COUNT)
    }
  }

  private fun isFeatureNow(file: PsiFile): Boolean {
    val virtualFile = (file.originalFile.virtualFile ?: file.viewProvider.virtualFile)
    val name = virtualFile.name.lowercase()
    val forced = FORCED_NAMES.any { name.endsWith(it) }
    if (!forced) {
      if (EXTENSIONS.none { name.endsWith(".$it") } || !PlaywrightYaddaProjects.isYaddaFeature(virtualFile)) return false
      if (!MENTIONS_FEATURE.containsMatchIn(file.text)) return false
    }
    // A fence or a comment can hold a "Feature:" that isn't one, so ask Yadda's parser, not a regex.
    return forced || analysis(file).markers.any { it.kind == YaddaGrammar.MarkerKind.FEATURE }
  }

  /** What Yadda makes of the file, kept until it changes. */
  fun analysis(file: PsiFile): YaddaGrammar.Result =
    CachedValuesManager.getCachedValue(file, ANALYSIS) {
      CachedValueProvider.Result.create(YaddaGrammar.analyzeMarkdown(file.text), file)
    }

  /** The steps of a feature, where each starts. */
  fun sites(file: PsiFile): List<Site> =
    CachedValuesManager.getCachedValue(file, SITES) {
      CachedValueProvider.Result.create(sitesOf(file), file)
    }

  /** A heading that starts a construct, with where its keyword is in the file. */
  class Heading(val marker: YaddaGrammar.Marker, val offset: Int)

  /** The `Feature:`, `Rule:`, `Scenario:`, `Background:` and `Examples:` headings of a feature, in order. */
  fun headings(file: PsiFile): List<Heading> =
    CachedValuesManager.getCachedValue(file, HEADINGS) {
      val markers = analysis(file).markers.filter { it.kind != YaddaGrammar.MarkerKind.ANNOTATION }
      CachedValueProvider.Result.create(markers.map { Heading(it, FeatureLines.start(file, it.line) + it.column) }, file)
    }

  /** The step whose line holds [offset], bullet and all. */
  fun siteAt(file: PsiFile, offset: Int): Site? = sites(file).firstOrNull { offset in it.line.startOffset..it.line.endOffset }

  private fun sitesOf(file: PsiFile): List<Site> {
    val text = file.text
    return analysis(file).steps.values.map { step ->
      val start = FeatureLines.start(file, step.line)
      val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
      val last = start + text.substring(start, end).trimEnd().length
      Site(step, TextRange(start, end), TextRange((start + step.column).coerceAtMost(last), last))
    }
  }

  /** The Markdown features that [scope] covers. */
  fun featureFiles(project: Project, scope: SearchScope): List<PsiFile> {
    val manager = PsiManager.getInstance(project)
    val files = when (scope) {
      is GlobalSearchScope -> EXTENSIONS
        .map { FileTypeManager.getInstance().getFileTypeByExtension(it) }
        .distinct()
        .flatMap { FileTypeIndex.getFiles(it, scope) }
        .distinct()
        .mapNotNull { manager.findFile(it) }
      is LocalSearchScope -> scope.scope.mapNotNull { it.containingFile }.distinct()
      else -> emptyList()
    }
    return files.filter { isFeature(it) }
  }

  private val ANALYSIS = Key.create<CachedValue<YaddaGrammar.Result>>("playwrightyadda.markdown.analysis")
  private val HEADINGS = Key.create<CachedValue<List<Heading>>>("playwrightyadda.markdown.headings")
  private val SITES = Key.create<CachedValue<List<Site>>>("playwrightyadda.markdown.sites")

  private const val MARKDOWN_LANGUAGE_ID = "Markdown"
}
