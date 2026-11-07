package io.github.jonfrankel.playwrightyadda.run

import com.intellij.aqua.runners.playwright.js.PlaywrightRunConfiguration
import com.intellij.aqua.runners.playwright.js.PlaywrightRunConfigurationProducer
import com.intellij.aqua.runners.playwright.js.PlaywrightRunSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.javascript.testing.runScope.JsTestRunScopeKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.execution.ParametersListUtil
import io.github.jonfrankel.playwrightyadda.yadda.YaddaFileType
import io.github.jonfrankel.playwrightyadda.yadda.YaddaGrammar
import io.github.jonfrankel.playwrightyadda.yadda.YaddaMarkdown
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinScenario
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

/**
 * Creates Playwright run configurations for:
 * 1. Gutter run icons on `Feature:` or `Scenario:` lines in `.feature` and Markdown files.
 * 2. Right-clicking a test file (`.feature`, Markdown feature, or `.spec.ts` / `.test.ts`) to run all tests in the file.
 * 3. Right-clicking a directory in the Project explorer to run all tests in that directory.
 */
class YaddaFeatureRunConfigurationProducer : LazyRunConfigurationProducer<PlaywrightRunConfiguration>() {
  override fun getConfigurationFactory(): ConfigurationFactory = playwright().configurationFactory

  private fun playwright(): PlaywrightRunConfigurationProducer =
    RunConfigurationProducer.getInstance(PlaywrightRunConfigurationProducer::class.java)

  override fun setupConfigurationFromContext(
    configuration: PlaywrightRunConfiguration,
    context: ConfigurationContext,
    sourceElement: Ref<PsiElement>,
  ): Boolean {
    val target = targetOf(context.psiLocation) ?: return false
    val (settings, source) = settingsFor(configuration, target) ?: return false

    configuration.settings = settings
    configuration.name = target.name
    sourceElement.set(source)
    return true
  }

  override fun isConfigurationFromContext(
    configuration: PlaywrightRunConfiguration,
    context: ConfigurationContext,
  ): Boolean {
    val target = targetOf(context.psiLocation) ?: return false
    val (expected, _) = settingsFor(configuration, target) ?: return false
    val actual = configuration.settings
    if (actual.cliOptions != expected.cliOptions) return false
    if (actual.scope.kind != expected.scope.kind) return false
    return when (actual.scope.kind) {
      JsTestRunScopeKind.DIRECTORY -> actual.scope.testDirectoryPath == expected.scope.testDirectoryPath
      else -> actual.scope.testFilePath == expected.scope.testFilePath
    }
  }

  private sealed class Target(val name: String, val source: PsiElement) {
    class Directory(
      name: String,
      source: PsiElement,
      val directory: VirtualFile,
    ) : Target(name, source)

    class FileOrGrep(
      name: String,
      source: PsiElement,
      val spec: VirtualFile,
      val grep: String = "",
    ) : Target(name, source)
  }

  private fun targetOf(element: PsiElement?): Target? {
    element ?: return null

    // 1. Directory right-clicked in Project tree
    if (element is PsiDirectory) {
      return directoryTargetOf(element)
    }

    val file = (element as? PsiFile) ?: element.containingFile ?: return null
    val virtualFile = file.virtualFile ?: return null

    // 2. Spec file (*.spec.ts, *.test.ts, etc.)
    if (isSpecFile(virtualFile)) {
      return Target.FileOrGrep(file.name, file, virtualFile, "")
    }

    // 3. Markdown feature file
    if (YaddaMarkdown.isFeature(file)) {
      return markdownTargetOf(element, file, virtualFile)
    }

    // 4. Gherkin feature file
    if (isFeatureFile(virtualFile)) {
      return gherkinTargetOf(element, file, virtualFile)
    }

    return null
  }

  private fun directoryTargetOf(dir: PsiDirectory): Target? {
    val virtualDir = dir.virtualFile
    if (virtualDir.name in IGNORED_DIR_NAMES) return null

    // Directory contains spec files (*.spec.ts, *.test.ts)
    if (containsSpecFiles(dir)) {
      return Target.Directory("Tests in ${dir.name}/", dir, virtualDir)
    }

    // Directory contains feature files (*.feature, *.md)
    val featureTitles = collectFeatureTitles(dir)
    if (featureTitles.isNotEmpty()) {
      val spec = YaddaFeatureSpecs.specFor(virtualDir, dir.project)?.virtualFile ?: return null
      val allFeatures = collectAllFeatureTitles(dir.project)
      val grep = if (allFeatures.isNotEmpty() && featureTitles.size >= allFeatures.size) {
        ""
      } else {
        PlaywrightGrep.forFeatures(featureTitles)
      }
      return Target.FileOrGrep("Tests in ${dir.name}/", dir, spec, grep)
    }

    return null
  }

  private fun markdownTargetOf(element: PsiElement, file: PsiFile, virtualFile: VirtualFile): Target? {
    val spec = YaddaFeatureSpecs.specFor(virtualFile, file.project)?.virtualFile ?: return null
    if (element is PsiFile) {
      val titles = featureTitlesIn(file)
      return Target.FileOrGrep(file.name, file, spec, PlaywrightGrep.forFeatures(titles))
    }
    val target = YaddaMarkdownRuns.targetAt(file, element.textRange.startOffset) ?: return null
    val source = target.heading.let { file.findElementAt(it.offset) } ?: file
    return Target.FileOrGrep(target.name, source, spec, target.grep)
  }

  private fun gherkinTargetOf(element: PsiElement, file: PsiFile, virtualFile: VirtualFile): Target? {
    val spec = YaddaFeatureSpecs.specFor(virtualFile, file.project)?.virtualFile ?: return null
    if (element is PsiFile) {
      val titles = featureTitlesIn(file)
      return Target.FileOrGrep(file.name, file, spec, PlaywrightGrep.forFeatures(titles))
    }

    val feature = PsiTreeUtil.getParentOfType(element, GherkinFeature::class.java, false) ?: return null
    val featureTitle = feature.featureName.takeIf { it.isNotBlank() } ?: return null
    val scenario = PsiTreeUtil.getParentOfType(element, GherkinStepsHolder::class.java, false)

    return when {
      scenario is GherkinScenarioOutline ->
        Target.FileOrGrep(scenario.scenarioName, scenario, spec, PlaywrightGrep.forScenarioOutline(featureTitle, scenario.scenarioName))
      scenario is GherkinScenario && scenario.scenarioName.isNotBlank() ->
        Target.FileOrGrep(scenario.scenarioName, scenario, spec, PlaywrightGrep.forScenario(featureTitle, scenario.scenarioName))
      scenario == null ->
        Target.FileOrGrep(featureTitle, feature, spec, PlaywrightGrep.forFeature(featureTitle))
      else -> null
    }
  }

  private fun settingsFor(
    configuration: PlaywrightRunConfiguration,
    target: Target,
  ): Pair<PlaywrightRunSettings, PsiElement>? {
    val base = configuration.settings
    return when (target) {
      is Target.Directory -> {
        val root = playwrightRoot(target.directory)
        val scope = base.scope.builder().apply {
          kind = JsTestRunScopeKind.DIRECTORY
          testDirectoryPath = target.directory.path
        }.build()
        val settings = base.builder().apply {
          this.scope = scope
          cliOptions = ""
          if (base.workingDir.isBlank()) workingDir = root.directory.path
          if (base.configFilePath.isBlank()) root.config?.let { configFilePath = it.path }
        }.build()
        settings to target.source
      }
      is Target.FileOrGrep -> {
        val root = playwrightRoot(target.spec)
        val scope = base.scope.builder().apply {
          kind = JsTestRunScopeKind.TEST_FILE
          testFilePath = target.spec.path
        }.build()
        val settings = base.builder().apply {
          this.scope = scope
          cliOptions = if (target.grep.isNotBlank()) ParametersListUtil.join("--grep", target.grep) else ""
          if (base.workingDir.isBlank()) workingDir = root.directory.path
          if (base.configFilePath.isBlank()) root.config?.let { configFilePath = it.path }
        }.build()
        settings to target.source
      }
    }
  }

  private class PlaywrightRoot(val directory: VirtualFile, val config: VirtualFile?)

  /** Where Playwright runs from: the nearest directory above [fileOrDir] with a Playwright config, else a `package.json`. */
  private fun playwrightRoot(fileOrDir: VirtualFile): PlaywrightRoot {
    val start = if (fileOrDir.isDirectory) fileOrDir else fileOrDir.parent
    val directories = generateSequence(start) { it.parent }.toList()
    for (directory in directories) {
      val config = CONFIG_FILE_NAMES.firstNotNullOfOrNull { directory.findChild(it) }
      if (config != null) return PlaywrightRoot(directory, config)
    }
    val withPackage = directories.firstOrNull { it.findChild("package.json") != null }
    return PlaywrightRoot(withPackage ?: start, null)
  }

  private fun isSpecFile(file: VirtualFile): Boolean =
    !file.isDirectory && file.name.contains(SPEC_FILE_PATTERN)

  private fun isFeatureFile(file: VirtualFile): Boolean =
    !file.isDirectory && (file.extension == "feature" || file.name.endsWith(".yadda.feature"))

  private fun containsSpecFiles(dir: PsiDirectory): Boolean {
    var found = false
    VfsUtilCore.iterateChildrenRecursively(dir.virtualFile, { file ->
      if (file.isDirectory) file.name !in IGNORED_DIR_NAMES else true
    }, { file ->
      if (!file.isDirectory && isSpecFile(file)) {
        found = true
        false
      } else {
        true
      }
    })
    return found
  }

  private fun collectFeatureTitles(dir: PsiDirectory): List<String> {
    val psiManager = PsiManager.getInstance(dir.project)
    val titles = mutableListOf<String>()
    VfsUtilCore.iterateChildrenRecursively(dir.virtualFile, { file ->
      if (file.isDirectory) file.name !in IGNORED_DIR_NAMES else true
    }, { file ->
      if (!file.isDirectory) {
        if (isFeatureFile(file) || file.extension == "md" || file.extension == "markdown") {
          val psi = psiManager.findFile(file)
          if (psi != null) {
            titles.addAll(featureTitlesIn(psi))
          }
        }
      }
      true
    })
    return titles.distinct()
  }

  private fun featureTitlesIn(file: PsiFile): List<String> {
    if (YaddaMarkdown.isFeature(file)) {
      return YaddaMarkdown.headings(file)
        .filter { it.marker.kind == YaddaGrammar.MarkerKind.FEATURE }
        .mapNotNull { it.marker.title.trim().takeIf { t -> t.isNotEmpty() } }
    }
    val virtualFile = file.virtualFile
    if (virtualFile != null && isFeatureFile(virtualFile)) {
      return PsiTreeUtil.findChildrenOfType(file, GherkinFeature::class.java)
        .mapNotNull { it.featureName.trim().takeIf { t -> t.isNotEmpty() } }
    }
    return emptyList()
  }

  private fun collectAllFeatureTitles(project: Project): List<String> {
    val searchScope = GlobalSearchScope.projectScope(project)
    val gherkin = listOf(GherkinFileType.INSTANCE, YaddaFileType)
      .flatMap { FileTypeIndex.getFiles(it, searchScope) }
    val markdown = YaddaMarkdown.featureFiles(project, searchScope).mapNotNull { it.virtualFile }
    val psiManager = PsiManager.getInstance(project)
    return (gherkin + markdown).distinct().flatMap { file ->
      val psi = psiManager.findFile(file)
      if (psi != null) featureTitlesIn(psi) else emptyList()
    }.distinct()
  }

  private companion object {
    val CONFIG_FILE_NAMES = listOf("ts", "js", "mts", "mjs", "cts", "cjs").map { "playwright.config.$it" }
    val SPEC_FILE_PATTERN = Regex("""\.(spec|test)\.[cm]?[jt]sx?$""", RegexOption.IGNORE_CASE)
    val IGNORED_DIR_NAMES = setOf(
      "node_modules", ".git", "dist", "build", ".gradle", ".idea", ".pnpm-store"
    )
  }
}
