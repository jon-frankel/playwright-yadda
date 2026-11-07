package io.github.jonfrankel.playwrightyadda.run

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import io.github.jonfrankel.playwrightyadda.yadda.YaddaFileType
import io.github.jonfrankel.playwrightyadda.yadda.YaddaGrammar
import io.github.jonfrankel.playwrightyadda.yadda.YaddaMarkdown
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

/**
 * Resolves test and suite nodes from the WebStorm test runner window to their scenario or feature
 * declarations in `.feature` or Markdown feature files.
 *
 * Playwright tests declared by playwright-yadda run under titles of the form `Feature: <feature>`
 * and `Scenario: <scenario>`. When double-clicked in the test tree, this locator navigates to the
 * corresponding scenario or feature definition in the feature file rather than the spec file.
 */
class YaddaTestLocator(
  private val proxy: SMTestProxy? = null,
  private val delegate: SMTestLocator? = null,
) : SMTestLocator {

  override fun getLocation(
    protocol: String,
    path: String,
    project: Project,
    scope: GlobalSearchScope,
  ): List<Location<*>> = getLocation(protocol, path, null, project, scope)

  override fun getLocation(
    protocol: String,
    path: String,
    metainfo: String?,
    project: Project,
    scope: GlobalSearchScope,
  ): List<Location<*>> {
    val location = ReadAction.computeBlocking<Location<*>?, RuntimeException> {
      if (DumbService.isDumb(project)) null
      else resolve(project, scope, protocol, path)
    }
    if (location != null) return listOf(location)
    return delegate?.getLocation(protocol, path, metainfo, project, scope).orEmpty()
  }

  private fun resolve(
    project: Project,
    scope: GlobalSearchScope,
    protocol: String,
    path: String,
  ): Location<*>? {
    val query = parseQuery(proxy, protocol, path) ?: return null
    return findLocation(project, scope, query)
  }

  data class Query(
    val specFilePath: String?,
    val featureTitle: String,
    val scenarioTitle: String?,
  )

  companion object {
    private val FEATURE_FILE_TYPES = listOf(GherkinFileType.INSTANCE, YaddaFileType)
    private val SCENARIO_PREFIX = Regex("""^Scenario\s*(?:Outline)?:?\s*""", RegexOption.IGNORE_CASE)
    private val FEATURE_PREFIX = Regex("""^Feature:?\s*""", RegexOption.IGNORE_CASE)
    private val DURATION_SUFFIX = Regex("""\s*\(\d+(?:\.\d+)?\s*(?:ms|s|m)\)$""")
    private val TAGS_SUFFIX = Regex("""(?:\s+@\S+)+$""")
    private val SPEC_FILE_REGEX = Regex("""^(/*)(.+?\.(?:spec|test)\.[cm]?[jt]sx?)(?:\.(.+))?$""", RegexOption.IGNORE_CASE)
    private val OUTLINE_PLACEHOLDER = Regex("""\[[^\]]*]|<[^>]*>""")

    private fun normalizePath(p: String): String = "/" + p.replace('\\', '/').trimStart('/')

    private fun extractSpecFileFromPath(path: String): Pair<String, String?>? {
      val normalized = path.replace('\\', '/')
      val match = SPEC_FILE_REGEX.find(normalized) ?: return null
      val leadingSlash = if (match.groupValues[1].isNotEmpty()) "/" else ""
      val specPath = leadingSlash + match.groupValues[2]
      val rest = match.groupValues.getOrNull(3)
      return specPath to rest
    }

    /**
     * Parses the feature title, scenario title and spec file path from either the [proxy] hierarchy
     * or the test [path].
     */
    fun parseQuery(proxy: SMTestProxy?, protocol: String, path: String): Query? {
      // 1. Try parsing from proxy hierarchy
      if (proxy != null) {
        val queryFromProxy = parseFromProxy(proxy)
        if (queryFromProxy != null) return queryFromProxy
      }

      // 2. Fall back to parsing from location path
      return parseFromPath(path)
    }

    private fun parseFromProxy(proxy: SMTestProxy): Query? {
      val rawName = proxy.name
      val cleanName = cleanTitle(rawName)
      val specFilePath = findSpecFilePath(proxy)

      if (isScenario(cleanName)) {
        val scenarioTitle = cleanName.replace(SCENARIO_PREFIX, "").trim()
        val featureSuite = findAncestorStartingWith(proxy, "Feature:") ?: return null
        val featureTitle = cleanTitle(featureSuite.name).replace(FEATURE_PREFIX, "").trim()
        if (featureTitle.isEmpty() || scenarioTitle.isEmpty()) return null
        return Query(specFilePath, featureTitle, scenarioTitle)
      }

      if (isFeature(cleanName)) {
        val featureTitle = cleanName.replace(FEATURE_PREFIX, "").trim()
        if (featureTitle.isEmpty()) return null
        return Query(specFilePath, featureTitle, null)
      }

      return null
    }

    private fun parseFromPath(path: String): Query? {
      val (specFilePath, rest) = extractSpecFileFromPath(path) ?: return null
      rest ?: return null

      val segments = splitUnescapedDots(rest)
      var featureTitle: String? = null
      var scenarioTitle: String? = null

      for (segment in segments) {
        val clean = cleanTitle(segment)
        if (isFeature(clean)) {
          featureTitle = clean.replace(FEATURE_PREFIX, "").trim()
        } else if (isScenario(clean)) {
          scenarioTitle = clean.replace(SCENARIO_PREFIX, "").trim()
        }
      }

      if (featureTitle.isNullOrBlank()) return null
      return Query(specFilePath, featureTitle, scenarioTitle?.takeIf { it.isNotBlank() })
    }

    private fun isScenario(name: String): Boolean = SCENARIO_PREFIX.containsMatchIn(name)
    private fun isFeature(name: String): Boolean = FEATURE_PREFIX.containsMatchIn(name)

    private fun cleanTitle(title: String): String =
      title.replace(DURATION_SUFFIX, "").replace(TAGS_SUFFIX, "").trim()

    private fun findAncestorStartingWith(proxy: SMTestProxy, prefix: String): SMTestProxy? {
      var current: SMTestProxy? = proxy.parent
      while (current != null) {
        if (cleanTitle(current.name).startsWith(prefix, ignoreCase = true)) {
          return current
        }
        current = current.parent
      }
      return null
    }

    private fun findSpecFilePath(proxy: SMTestProxy): String? {
      var current: SMTestProxy? = proxy
      while (current != null) {
        val url = current.locationUrl
        if (!url.isNullOrBlank()) {
          val path = VirtualFileManager.extractPath(url)
          val (specFilePath, _) = extractSpecFileFromPath(path) ?: (null to null)
          if (specFilePath != null) {
            return specFilePath
          }
        }
        current = current.parent
      }
      return null
    }

    private fun splitUnescapedDots(text: String): List<String> {
      val result = mutableListOf<String>()
      val current = StringBuilder()
      var escaped = false
      for (ch in text) {
        if (escaped) {
          if (ch != '.') current.append('\\')
          current.append(ch)
          escaped = false
        } else if (ch == '\\') {
          escaped = true
        } else if (ch == '.') {
          result.add(current.toString())
          current.setLength(0)
        } else {
          current.append(ch)
        }
      }
      if (current.isNotEmpty()) {
        result.add(current.toString())
      }
      return result
    }

    /** Finds the PSI location for the feature or scenario matching [query]. */
    fun findLocation(project: Project, scope: GlobalSearchScope, query: Query): Location<*>? {
      val candidates = collectCandidateFiles(project, scope, query.specFilePath)
      for (file in candidates) {
        if (YaddaMarkdown.isFeature(file)) {
          val loc = findInMarkdownFile(file, query.featureTitle, query.scenarioTitle)
          if (loc != null) return loc
        } else {
          val loc = findInGherkinFile(file, query.featureTitle, query.scenarioTitle)
          if (loc != null) return loc
        }
      }
      return null
    }

    private fun collectCandidateFiles(
      project: Project,
      scope: GlobalSearchScope,
      specFilePath: String?,
    ): List<PsiFile> {
      val manager = PsiManager.getInstance(project)
      val effectiveScope = if (scope == GlobalSearchScope.EMPTY_SCOPE) GlobalSearchScope.projectScope(project) else scope

      fun filesIn(searchScope: GlobalSearchScope): List<PsiFile> {
        val gherkin = FEATURE_FILE_TYPES.flatMap { FileTypeIndex.getFiles(it, searchScope) }
          .mapNotNull { manager.findFile(it) }
        val markdown = YaddaMarkdown.featureFiles(project, searchScope)
        return (gherkin + markdown).distinct()
      }

      var candidates = filesIn(effectiveScope)
      if (candidates.isEmpty() && effectiveScope != GlobalSearchScope.projectScope(project)) {
        candidates = filesIn(GlobalSearchScope.projectScope(project))
      }

      val normalizedSpec = specFilePath?.let { normalizePath(it) }
      return candidates.sortedWith(
        compareByDescending<PsiFile> { !isInIgnoredDirectory(it) }
          .thenByDescending { file ->
            val path = file.virtualFile?.let { normalizePath(it.path) }
            if (path != null && normalizedSpec != null) {
              path.commonPrefixWith(normalizedSpec).length
            } else 0
          }
      )
    }

    private fun isInIgnoredDirectory(file: PsiFile): Boolean {
      val path = file.virtualFile?.path ?: return false
      return path.contains("/node_modules/") || path.contains("/dist/") || path.contains("/.gradle/")
    }

    private fun findInGherkinFile(
      file: PsiFile,
      featureTitle: String,
      scenarioTitle: String?,
    ): Location<*>? {
      val features = PsiTreeUtil.findChildrenOfType(file, GherkinFeature::class.java)
      for (feature in features) {
        if (!titlesMatch(feature.featureName, featureTitle)) continue

        if (scenarioTitle == null) {
          return PsiLocation.fromPsiElement(feature)
        }

        val scenarios = PsiTreeUtil.findChildrenOfType(feature, GherkinStepsHolder::class.java)

        // 1. Exact title match
        val exact = scenarios.firstOrNull { titlesMatch(it.scenarioName, scenarioTitle) }
        if (exact != null) {
          return PsiLocation.fromPsiElement(exact)
        }

        // 2. Scenario Outline placeholder match (e.g. "[product]" matches "shoes")
        val outline = scenarios.firstOrNull { matchesOutline(it.scenarioName, scenarioTitle) }
        if (outline != null) {
          return PsiLocation.fromPsiElement(outline)
        }

        // Fall back to feature definition if scenario wasn't found under it
        return PsiLocation.fromPsiElement(feature)
      }
      return null
    }

    private fun findInMarkdownFile(
      file: PsiFile,
      featureTitle: String,
      scenarioTitle: String?,
    ): Location<*>? {
      val headings = YaddaMarkdown.headings(file)
      val featureHeading = headings.firstOrNull {
        it.marker.kind == YaddaGrammar.MarkerKind.FEATURE && titlesMatch(it.marker.title, featureTitle)
      } ?: return null

      if (scenarioTitle == null) {
        val element = file.findElementAt(featureHeading.offset) ?: file
        return PsiLocation.fromPsiElement(element)
      }

      val scenarioHeadings = headings
        .dropWhile { it !== featureHeading }
        .drop(1)
        .takeWhile { it.marker.kind != YaddaGrammar.MarkerKind.FEATURE && it.marker.kind != YaddaGrammar.MarkerKind.RULE }
        .filter { it.marker.kind == YaddaGrammar.MarkerKind.SCENARIO }

      // 1. Exact title match
      val exact = scenarioHeadings.firstOrNull { titlesMatch(it.marker.title, scenarioTitle) }
      if (exact != null) {
        val element = file.findElementAt(exact.offset) ?: file
        return PsiLocation.fromPsiElement(element)
      }

      // 2. Scenario Outline placeholder match
      val outline = scenarioHeadings.firstOrNull { matchesOutline(it.marker.title, scenarioTitle) }
      if (outline != null) {
        val element = file.findElementAt(outline.offset) ?: file
        return PsiLocation.fromPsiElement(element)
      }

      val fallback = file.findElementAt(featureHeading.offset) ?: file
      return PsiLocation.fromPsiElement(fallback)
    }

    fun titlesMatch(a: String?, b: String?): Boolean {
      if (a == null || b == null) return false
      return normalizeTitle(a).equals(normalizeTitle(b), ignoreCase = true)
    }

    fun matchesOutline(outlineName: String?, testTitle: String?): Boolean {
      if (outlineName == null || testTitle == null) return false
      val trimmedOutline = normalizeTitle(outlineName)
      val trimmedTest = normalizeTitle(testTitle)
      if (!OUTLINE_PLACEHOLDER.containsMatchIn(trimmedOutline)) return false

      val parts = OUTLINE_PLACEHOLDER.split(trimmedOutline)
      val regexPattern = "^" + parts.joinToString(".*") { Regex.escape(it) } + "$"
      return Regex(regexPattern, RegexOption.IGNORE_CASE).matches(trimmedTest)
    }

    private fun normalizeTitle(title: String): String =
      title.trim()
        .replace(Regex("""\s+"""), " ")
        .removeSurrounding("\"")
        .removeSurrounding("'")
  }
}
