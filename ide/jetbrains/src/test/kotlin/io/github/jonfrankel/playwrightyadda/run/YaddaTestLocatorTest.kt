package io.github.jonfrankel.playwrightyadda.run

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.cucumber.psi.GherkinFeature
import org.jetbrains.plugins.cucumber.psi.GherkinScenario
import org.jetbrains.plugins.cucumber.psi.GherkinStepsHolder

class YaddaTestLocatorTest : BasePlatformTestCase() {

  private fun addSpec(path: String = "e2e/features.spec.ts") {
    myFixture.addFileToProject("package.json", """{ "dependencies": { "playwright-yadda": "*" } }""")
    myFixture.addFileToProject(
      path,
      """
      import { test } from "@playwright/test";
      import { defineFeatureSuite } from "playwright-yadda";
      defineFeatureSuite({ test, registry, features: "../features" });
      """.trimIndent(),
    )
  }

  fun `test scenario in Gherkin feature jumps to scenario definition`() {
    addSpec()
    myFixture.addFileToProject(
      "features/admin.feature",
      """
      Feature: Admin Inventory
        As an administrator
        I want to manage products

        Scenario: Administrator deletes product
          Given the test session is active
          When they remove the item
          Then the product is deleted
      """.trimIndent(),
    )

    val root = SMTestProxy.SMRootTestProxy()
    val spec = SMTestProxy("features.spec.ts", true, "suite:///src/e2e/features.spec.ts")
    val feature = SMTestProxy("Feature: Admin Inventory", true, "suite:///src/e2e/features.spec.ts.Feature: Admin Inventory")
    val scenario = SMTestProxy("Scenario: Administrator deletes product", false, "test:///src/e2e/features.spec.ts.Feature: Admin Inventory.Scenario: Administrator deletes product")

    root.addChild(spec)
    spec.addChild(feature)
    feature.addChild(scenario)

    YaddaTestStatusListener.install(scenario)

    val loc = requireNotNull(scenario.getLocation(project, GlobalSearchScope.allScope(project)))
    val element = loc.psiElement
    assertTrue(element is GherkinScenario)
    assertEquals("Administrator deletes product", (element as GherkinScenario).scenarioName)
    assertEquals("admin.feature", element.containingFile.name)
  }

  fun `test feature suite in Gherkin feature jumps to feature definition`() {
    addSpec()
    myFixture.addFileToProject(
      "features/admin.feature",
      """
      Feature: Admin Inventory
        Scenario: Administrator deletes product
          Given the test session is active
      """.trimIndent(),
    )

    val root = SMTestProxy.SMRootTestProxy()
    val spec = SMTestProxy("features.spec.ts", true, "suite:///src/e2e/features.spec.ts")
    val feature = SMTestProxy("Feature: Admin Inventory", true, "suite:///src/e2e/features.spec.ts.Feature: Admin Inventory")

    root.addChild(spec)
    spec.addChild(feature)

    YaddaTestStatusListener.install(feature)

    val loc = requireNotNull(feature.getLocation(project, GlobalSearchScope.allScope(project)))
    val element = loc.psiElement
    assertTrue(element is GherkinFeature)
    assertEquals("Admin Inventory", (element as GherkinFeature).featureName)
    assertEquals("admin.feature", element.containingFile.name)
  }

  fun `test scenario outline with examples jumps to scenario outline definition`() {
    addSpec()
    myFixture.addFileToProject(
      "features/orders.feature",
      """
      Feature: Orders
        Scenario Outline: A cart with [qty] keyboards
          Given the cart holds [qty] items
          Examples:
            | qty |
            | 1   |
            | 5   |
      """.trimIndent(),
    )

    val root = SMTestProxy.SMRootTestProxy()
    val spec = SMTestProxy("features.spec.ts", true, "suite:///src/e2e/features.spec.ts")
    val feature = SMTestProxy("Feature: Orders", true, "suite:///src/e2e/features.spec.ts.Feature: Orders")
    val scenario = SMTestProxy("Scenario: A cart with 1 keyboards", false, "test:///src/e2e/features.spec.ts.Feature: Orders.Scenario: A cart with 1 keyboards")

    root.addChild(spec)
    spec.addChild(feature)
    feature.addChild(scenario)

    YaddaTestStatusListener.install(scenario)

    val loc = requireNotNull(scenario.getLocation(project, GlobalSearchScope.allScope(project)))
    val element = loc.psiElement
    assertTrue(element is GherkinStepsHolder)
    assertEquals("A cart with [qty] keyboards", (element as GherkinStepsHolder).scenarioName)
    assertEquals("orders.feature", element.containingFile.name)
  }

  fun `test Markdown feature scenario jumps to heading in markdown file`() {
    addSpec()
    myFixture.addFileToProject(
      "features/checkout.md",
      """
      # Feature: Storefront Checkout

      ### Scenario: Customer clears cart
      * Given the test session is active
      """.trimIndent(),
    )

    val root = SMTestProxy.SMRootTestProxy()
    val spec = SMTestProxy("features.spec.ts", true, "suite:///src/e2e/features.spec.ts")
    val feature = SMTestProxy("Feature: Storefront Checkout", true, "suite:///src/e2e/features.spec.ts.Feature: Storefront Checkout")
    val scenario = SMTestProxy("Scenario: Customer clears cart", false, "test:///src/e2e/features.spec.ts.Feature: Storefront Checkout.Scenario: Customer clears cart")

    root.addChild(spec)
    spec.addChild(feature)
    feature.addChild(scenario)

    YaddaTestStatusListener.install(scenario)

    val loc = requireNotNull(scenario.getLocation(project, GlobalSearchScope.allScope(project)))
    assertEquals("checkout.md", loc.virtualFile?.name)
    val text = loc.psiElement!!.containingFile.text
    val offset = loc.psiElement!!.textOffset
    assertTrue(text.substring(offset).startsWith("Scenario: Customer clears cart") || text.substring(offset).startsWith("Customer clears cart"))
  }

  fun `test monorepo selects feature file closest to spec file`() {
    myFixture.addFileToProject("package.json", """{ "dependencies": { "playwright-yadda": "*" } }""")
    myFixture.addFileToProject(
      "packages/pkgA/features/admin.feature",
      """
      Feature: Admin Inventory
        Scenario: Administrator deletes product
          Given pkgA
      """.trimIndent(),
    )
    myFixture.addFileToProject(
      "packages/pkgB/features/admin.feature",
      """
      Feature: Admin Inventory
        Scenario: Administrator deletes product
          Given pkgB
      """.trimIndent(),
    )

    val specPath = myFixture.findFileInTempDir("packages/pkgB")!!.path + "/e2e/features.spec.ts"
    val root = SMTestProxy.SMRootTestProxy()
    val spec = SMTestProxy("features.spec.ts", true, "suite://$specPath")
    val feature = SMTestProxy("Feature: Admin Inventory", true, "suite://$specPath.Feature: Admin Inventory")
    val scenario = SMTestProxy("Scenario: Administrator deletes product", false, "test://$specPath.Feature: Admin Inventory.Scenario: Administrator deletes product")

    root.addChild(spec)
    spec.addChild(feature)
    feature.addChild(scenario)

    YaddaTestStatusListener.install(scenario)

    val loc = requireNotNull(scenario.getLocation(project, GlobalSearchScope.allScope(project)))
    val element = loc.psiElement!!
    assertTrue(element.containingFile.virtualFile.path.contains("packages/pkgB/features/admin.feature"))
  }

  fun `test non-yadda test falls back to delegate locator`() {
    addSpec()
    val dummyTarget = myFixture.addFileToProject("e2e/dummy.ts", "const x = 1;")
    val delegate = SMTestLocator { _, _, _, _ -> listOf(PsiLocation.fromPsiElement(dummyTarget)) }

    val root = SMTestProxy.SMRootTestProxy()
    val spec = SMTestProxy("unit.spec.ts", true, "suite:///src/unit.spec.ts")
    val suite = SMTestProxy("describe Auth", true, "suite:///src/unit.spec.ts.describe Auth")
    val test = SMTestProxy("regular unit test", false, "test:///src/unit.spec.ts.describe Auth.regular unit test")

    root.addChild(spec)
    spec.addChild(suite)
    suite.addChild(test)

    val locator = YaddaTestLocator(test, delegate)
    test.setLocator(locator)

    val loc = requireNotNull(test.getLocation(project, GlobalSearchScope.allScope(project)))
    assertEquals("dummy.ts", loc.virtualFile?.name)
  }

  fun `test resolution from location path without proxy`() {
    addSpec()
    myFixture.addFileToProject(
      "features/admin.feature",
      """
      Feature: Admin Inventory
        Scenario: Administrator deletes product
          Given the test session is active
      """.trimIndent(),
    )

    val locator = YaddaTestLocator(null, null)
    val path = "e2e/features.spec.ts.Feature: Admin Inventory.Scenario: Administrator deletes product"
    val locs = locator.getLocation("test", path, project, GlobalSearchScope.allScope(project))
    assertEquals(1, locs.size)
    val element = locs.first().psiElement!!
    assertTrue(element is GherkinScenario)
    assertEquals("Administrator deletes product", (element as GherkinScenario).scenarioName)
  }
}
