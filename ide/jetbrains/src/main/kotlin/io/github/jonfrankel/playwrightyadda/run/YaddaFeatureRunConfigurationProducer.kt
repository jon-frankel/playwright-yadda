package io.github.jonfrankel.playwrightyadda.run

import com.intellij.aqua.runners.playwright.js.PlaywrightRunConfiguration
import com.intellij.aqua.runners.playwright.js.PlaywrightRunConfigurationProducer
import com.intellij.aqua.runners.playwright.js.PlaywrightRunSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.javascript.testing.runScope.JsTestRunScopeKind
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinScenarioOutline
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder
import org.jetbrains.plugins.cucumber.psi.GherkinScenario

/**
 * Makes the run icon next to a `Feature:` or `Scenario:` line in a `.feature` file work.
 *
 * WebStorm's Cucumber.js plugin only offers to run `cucumber-js`. playwright-yadda runs through
 * Playwright, whose own run configurations attach to `test(...)` calls in the source, and the
 * scenarios here are generated at runtime, so nothing offered a run. This producer finds the spec
 * file that calls `defineFeatureSuite` and creates a Playwright run configuration for it, narrowed
 * to the feature or scenario with `--grep`. The test tree, re-run and debugging are Playwright's.
 */
class YaddaFeatureRunConfigurationProducer : LazyRunConfigurationProducer<PlaywrightRunConfiguration>() {
  // The type itself is internal to the JavaScript plugin, but its own producer hands out the factory.
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
    return actual.cliOptions == expected.cliOptions &&
      actual.scope.testFilePath == expected.scope.testFilePath &&
      actual.scope.kind == expected.scope.kind
  }

  /** The Feature or Scenario [element] is in, with the `--grep` that selects it. */
  private class Target(val name: String, val grep: String, val file: com.intellij.psi.PsiFile)

  private fun targetOf(element: PsiElement?): Target? {
    element ?: return null
    val file = element.containingFile ?: return null
    if (file.virtualFile?.extension != "feature") return null

    val feature = PsiTreeUtil.getParentOfType(element, GherkinFeature::class.java, false) ?: return null
    val featureTitle = feature.featureName.takeIf { it.isNotBlank() } ?: return null

    val scenario = PsiTreeUtil.getParentOfType(element, GherkinStepsHolder::class.java, false)
    return when {
      scenario is GherkinScenarioOutline ->
        Target(scenario.scenarioName, PlaywrightGrep.forScenarioOutline(featureTitle, scenario.scenarioName), file)
      scenario is GherkinScenario && scenario.scenarioName.isNotBlank() ->
        Target(scenario.scenarioName, PlaywrightGrep.forScenario(featureTitle, scenario.scenarioName), file)
      // The Feature line itself, or its description: run every scenario.
      scenario == null -> Target(featureTitle, PlaywrightGrep.forFeature(featureTitle), file)
      else -> null
    }
  }

  /** The spec file's settings, narrowed with a `--grep`. Null when there is no spec to run. */
  private fun settingsFor(configuration: PlaywrightRunConfiguration, target: Target): Pair<PlaywrightRunSettings, PsiElement>? {
    val spec = YaddaFeatureSpecs.specFor(target.file.virtualFile, target.file.project)?.virtualFile ?: return null
    val base = configuration.settings
    val root = playwrightRoot(spec)

    // Playwright's own producer would do this, but it only offers files that visibly contain `test(...)`.
    val scope = base.scope.builder().apply {
      kind = JsTestRunScopeKind.TEST_FILE
      testFilePath = spec.path
    }.build()
    val settings = base.builder().apply {
      this.scope = scope
      cliOptions = ParametersListUtil.join("--grep", target.grep)
      if (base.workingDir.isBlank()) workingDir = root.directory.path
      if (base.configFilePath.isBlank()) root.config?.let { configFilePath = it.path }
    }.build()
    return settings to target.file
  }

  private class PlaywrightRoot(val directory: VirtualFile, val config: VirtualFile?)

  /** Where Playwright runs from: the nearest directory above [spec] with a Playwright config, else a `package.json`. */
  private fun playwrightRoot(spec: VirtualFile): PlaywrightRoot {
    val directories = generateSequence(spec.parent) { it.parent }.toList()
    for (directory in directories) {
      val config = CONFIG_FILE_NAMES.firstNotNullOfOrNull { directory.findChild(it) }
      if (config != null) return PlaywrightRoot(directory, config)
    }
    val withPackage = directories.firstOrNull { it.findChild("package.json") != null }
    return PlaywrightRoot(withPackage ?: spec.parent, null)
  }

  private companion object {
    // The names Playwright itself looks for. The IDE's own list is not accessible from here.
    val CONFIG_FILE_NAMES = listOf("ts", "js", "mts", "mjs", "cts", "cjs").map { "playwright.config.$it" }
  }
}
