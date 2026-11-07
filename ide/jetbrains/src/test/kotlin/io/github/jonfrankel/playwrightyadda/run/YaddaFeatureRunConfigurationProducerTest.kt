package io.github.jonfrankel.playwrightyadda.run

import com.intellij.aqua.runners.playwright.js.PlaywrightRunConfiguration
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.javascript.testing.runScope.JsTestRunScopeKind
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
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

  private fun addSpec(path: String = "e2e/features.spec.ts"): PsiFile {
    myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "1" } }""")
    return myFixture.addFileToProject(
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
    return runFromElement(element)
  }

  private fun runFromElement(element: PsiElement): PlaywrightRunConfiguration? =
    ConfigurationContext(element).configurationsFromContext.orEmpty()
      .map { it.configuration }
      .filterIsInstance<PlaywrightRunConfiguration>()
      .firstOrNull()

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

  fun `test right-clicking a feature file runs all tests in that file named after the file`() {
    addSpec()
    val file = myFixture.addFileToProject("features/storefront.feature", feature)

    val run = runFromElement(file)!!
    assertEquals("storefront.feature", run.name)
    assertEquals(JsTestRunScopeKind.TEST_FILE, run.settings.scope.kind)
    assertTrue(run.settings.scope.testFilePath!!.endsWith("e2e/features.spec.ts"))
    assertEquals(PlaywrightGrep.forFeature("Storefront Shopping"), grepOf(run))
  }

  fun `test right-clicking a spec file runs that spec file`() {
    val spec = addSpec()

    val run = runFromElement(spec)!!
    assertEquals("features.spec.ts", run.name)
    assertEquals(JsTestRunScopeKind.TEST_FILE, run.settings.scope.kind)
    assertTrue(run.settings.scope.testFilePath!!.endsWith("e2e/features.spec.ts"))
    assertTrue(run.settings.cliOptions.isBlank())
  }

  fun `test right-clicking a directory containing spec files runs all tests in that directory`() {
    addSpec("e2e/features.spec.ts")
    val e2eDir = myFixture.psiManager.findDirectory(myFixture.findFileInTempDir("e2e"))!!

    val run = runFromElement(e2eDir)!!
    assertEquals("Tests in e2e/", run.name)
    assertEquals(JsTestRunScopeKind.DIRECTORY, run.settings.scope.kind)
    assertTrue(run.settings.scope.testDirectoryPath!!.endsWith("e2e"))
  }

  fun `test right-clicking a directory containing feature files runs all tests in that directory`() {
    addSpec()
    myFixture.addFileToProject("features/storefront.feature", feature)
    val featuresDir = myFixture.psiManager.findDirectory(myFixture.findFileInTempDir("features"))!!

    val run = runFromElement(featuresDir)!!
    assertEquals("Tests in features/", run.name)
    assertEquals(JsTestRunScopeKind.TEST_FILE, run.settings.scope.kind)
    assertTrue(run.settings.scope.testFilePath!!.endsWith("e2e/features.spec.ts"))
  }

  fun `test right-clicking a sub-directory of feature files narrows to those features`() {
    addSpec()
    myFixture.addFileToProject("features/storefront.feature", feature)
    val adminFeature = """
      Feature: Admin Inventory
        Scenario: Admin checks stock
          Given stock is checked
    """.trimIndent()
    myFixture.addFileToProject("features/admin/admin.feature", adminFeature)
    val adminDir = myFixture.psiManager.findDirectory(myFixture.findFileInTempDir("features/admin"))!!

    val run = runFromElement(adminDir)!!
    assertEquals("Tests in admin/", run.name)
    assertEquals(JsTestRunScopeKind.TEST_FILE, run.settings.scope.kind)
    assertTrue(run.settings.scope.testFilePath!!.endsWith("e2e/features.spec.ts"))
    assertEquals(PlaywrightGrep.forFeature("Admin Inventory"), grepOf(run))
  }

  fun `test right-clicking a non-test directory produces no run configuration`() {
    myFixture.addFileToProject("package.json", "{}")
    myFixture.addFileToProject("src/utils/helpers.ts", "export const foo = 1;")
    val utilsDir = myFixture.psiManager.findDirectory(myFixture.findFileInTempDir("src/utils"))!!

    assertNull(runFromElement(utilsDir))
  }

  fun `test right-clicking a markdown feature file runs all tests in that file`() {
    addSpec()
    val mdFile = myFixture.addFileToProject(
      "features/checkout.md",
      """
      # Feature: Markdown Checkout

      ## Scenario: Customer checks out
      - Given the cart has items
      """.trimIndent(),
    )

    val run = runFromElement(mdFile)!!
    assertEquals("checkout.md", run.name)
    assertEquals(JsTestRunScopeKind.TEST_FILE, run.settings.scope.kind)
    assertTrue(run.settings.scope.testFilePath!!.endsWith("e2e/features.spec.ts"))
    assertEquals(PlaywrightGrep.forFeature("Markdown Checkout"), grepOf(run))
  }

  fun `test there is nothing to run without a spec that calls defineFeatureSuite`() {
    myFixture.addFileToProject("package.json", "{}")
    myFixture.addFileToProject("e2e/other.spec.ts", """import { test } from "@playwright/test"; test("x", () => {});""")
    val file = myFixture.addFileToProject("features/storefront.feature", feature)

    assertNull(runFrom(file, "Scenario:"))
  }
}
