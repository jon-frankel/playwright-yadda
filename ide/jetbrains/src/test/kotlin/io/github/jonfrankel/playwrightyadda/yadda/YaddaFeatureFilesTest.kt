package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.execution.ParametersListUtil
import io.github.jonfrankel.playwrightyadda.run.PlaywrightGrep
import org.jetbrains.plugins.cucumber.inspections.CucumberStepInspection
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference
import com.intellij.aqua.runners.playwright.js.PlaywrightRunConfiguration

/** Everything the plugin does for a `.feature` file, for a `.yadda.feature` file. */
class YaddaFeatureFilesTest : BasePlatformTestCase() {
  private val feature = """
    @Libraries=storefront
    Feature: Storefront
      Scenario: Remove
        When they remove the item
  """.trimIndent()

  private fun project() {
    myFixture.addFileToProject("common.ts", "export const commonSteps = createStepLibrary();")
    myFixture.addFileToProject(
      "storefront.ts",
      "export const storefrontSteps = createStepLibrary();\nstorefrontSteps.when(\"they remove the item\", function () {});",
    )
    myFixture.addFileToProject(
      "admin.ts",
      "export const adminSteps = createStepLibrary();\nadminSteps.when(\"they remove the item\", function () {});",
    )
    myFixture.addFileToProject(
      "registry.ts",
      """
      import { createLibraryRegistry } from "playwright-yadda";
      import { commonSteps } from "./common.js";
      import { storefrontSteps } from "./storefront.js";
      import { adminSteps } from "./admin.js";
      export const registry = createLibraryRegistry({
        common: commonSteps,
        storefront: storefrontSteps,
        admin: adminSteps,
      });
      """.trimIndent(),
    )
  }

  private fun yadda(text: String = feature): PsiFile = myFixture.addFileToProject("features/store.yadda.feature", text)

  private fun open(file: PsiFile, needle: String, shift: Int = 0) {
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.editor.caretModel.moveToOffset(file.text.indexOf(needle) + shift)
  }

  private fun goesTo(): List<String> {
    val targets = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)
    return targets.map { it.containingFile.name + ": " + it.text.lines().first().trim().removeSuffix(",") }.sorted()
  }

  fun `test Cmd+B on a step goes to the definition its libraries enable`() {
    project()
    open(yadda(), "they remove", 2)
    assertEquals(listOf("storefront.ts: \"they remove the item\""), goesTo())
  }

  fun `test the ambiguity warning is silenced when the libraries leave one definition`() {
    project()
    myFixture.configureFromExistingVirtualFile(yadda().virtualFile)
    myFixture.enableInspections(CucumberStepInspection::class.java)
    assertEquals(emptyList<String>(), myFixture.doHighlighting().mapNotNull { it.description }.filter { "step reference" in it })
  }

  fun `test usages of a definition are the Yadda scenarios that enable its library`() {
    project()
    yadda()
    myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("storefront.ts")!!)
    myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("they remove") + 2)
    assertEquals(listOf("store.yadda.feature"), myFixture.findUsages(myFixture.elementAtCaret).map { it.file!!.name })
  }

  fun `test usages of a regex definition include steps in a Yadda feature`() {
    myFixture.addFileToProject(
      "storefront.ts",
      "export const storefrontSteps = createStepLibrary();\nstorefrontSteps.when(/^they remove the (\\w+)\$/, function (item: string) {});",
    )
    yadda("@Libraries=storefront\nFeature: Storefront\n  Scenario: Remove\n    When they remove the item")
    myFixture.addFileToProject(
      "features/plain.feature",
      "@Libraries=storefront\nFeature: Plain\n  Scenario: Remove\n    When they remove the item",
    )
    myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("storefront.ts")!!)
    myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("they remove") + 2)
    assertEquals(
      listOf("plain.feature", "store.yadda.feature"),
      myFixture.findUsages(myFixture.elementAtCaret).map { it.file!!.name }.sorted(),
    )
  }

  fun `test Cmd+B on a library name goes to its registry property`() {
    project()
    open(yadda(), "storefront", 2)
    assertEquals(listOf("registry.ts: storefront: storefrontSteps"), goesTo())
  }

  fun `test usages of a registry property include a Yadda feature's library tag`() {
    project()
    yadda()
    myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("registry.ts")!!)
    myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("storefront:") + 2)
    assertEquals(listOf("store.yadda.feature"), myFixture.findUsages(myFixture.elementAtCaret).map { it.file!!.name })
  }

  fun `test the run icon runs a Yadda feature's scenario through the spec`() {
    myFixture.addFileToProject("package.json", "{}")
    myFixture.addFileToProject(
      "e2e/features.spec.ts",
      """
      import { test } from "@playwright/test";
      import { defineFeatureSuite } from "playwright-yadda";
      defineFeatureSuite({ test, registry, features: "../features" });
      """.trimIndent(),
    )
    val file = yadda()
    val element = file.findElementAt(file.text.indexOf("Scenario:"))!!
    val run = ConfigurationContext(element).configurationsFromContext.orEmpty()
      .map { it.configuration }
      .filterIsInstance<PlaywrightRunConfiguration>()
      .firstOrNull { "--grep" in ParametersListUtil.parse(it.settings.cliOptions) }
    assertNotNull("no run configuration for the Scenario line", run)
    val grep = ParametersListUtil.parse(run!!.settings.cliOptions).let { it[it.indexOf("--grep") + 1] }
    assertEquals(PlaywrightGrep.forScenario("Storefront", "Remove"), grep)
  }
}
