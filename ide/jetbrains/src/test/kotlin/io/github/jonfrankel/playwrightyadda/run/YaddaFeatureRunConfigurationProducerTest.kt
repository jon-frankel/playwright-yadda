package io.github.jonfrankel.playwrightyadda.run

import com.intellij.aqua.runners.playwright.js.PlaywrightRunConfiguration
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.execution.ParametersListUtil

class YaddaFeatureRunConfigurationProducerTest : BasePlatformTestCase() {
  private val feature = """
    Feature: Storefront Shopping
      As a customer

      Scenario: Customer clears cart
        Given the test session is active
  """.trimIndent()

  private fun addSpec(path: String = "e2e/features.spec.ts") {
    myFixture.addFileToProject("package.json", "{}")
    myFixture.addFileToProject(
      path,
      """
      import { test } from "@playwright/test";
      import { defineFeatureSuite } from "playwright-yadda";
      defineFeatureSuite({ test, registry, features: "../features" });
      """.trimIndent(),
    )
  }

  /** What the run icon would run for the element at [needle]: the Playwright configuration, or null. */
  private fun runFrom(file: PsiFile, needle: String): PlaywrightRunConfiguration? {
    val element = file.findElementAt(file.text.indexOf(needle))!!
    return ConfigurationContext(element).configurationsFromContext.orEmpty()
      .map { it.configuration }
      .filterIsInstance<PlaywrightRunConfiguration>()
      .firstOrNull { "--grep" in ParametersListUtil.parse(it.settings.cliOptions) }
  }

  private fun grepOf(run: PlaywrightRunConfiguration): String =
    ParametersListUtil.parse(run.settings.cliOptions).let { it[it.indexOf("--grep") + 1] }

  fun `test the scenario line runs that scenario through the spec`() {
    addSpec()
    val file = myFixture.addFileToProject("features/storefront.feature", feature)

    val run = runFrom(file, "Scenario:")!!
    assertEquals(PlaywrightGrep.forScenario("Storefront Shopping", "Customer clears cart"), grepOf(run))
    assertTrue(run.settings.scope.testFilePath!!.endsWith("e2e/features.spec.ts"))
    assertEquals("Customer clears cart", run.name)
  }

  fun `test a step runs its scenario`() {
    addSpec()
    val file = myFixture.addFileToProject("features/storefront.feature", feature)

    val run = runFrom(file, "Given")!!
    assertEquals(PlaywrightGrep.forScenario("Storefront Shopping", "Customer clears cart"), grepOf(run))
  }

  fun `test the feature line runs every scenario of the feature`() {
    addSpec()
    val file = myFixture.addFileToProject("features/storefront.feature", feature)

    val run = runFrom(file, "Feature:")!!
    assertEquals(PlaywrightGrep.forFeature("Storefront Shopping"), grepOf(run))
    assertEquals("Storefront Shopping", run.name)
  }

  fun `test there is nothing to run without a spec that calls defineFeatureSuite`() {
    myFixture.addFileToProject("package.json", "{}")
    myFixture.addFileToProject("e2e/other.spec.ts", """import { test } from "@playwright/test"; test("x", () => {});""")
    val file = myFixture.addFileToProject("features/storefront.feature", feature)

    assertNull(runFrom(file, "Scenario:"))
  }
}
