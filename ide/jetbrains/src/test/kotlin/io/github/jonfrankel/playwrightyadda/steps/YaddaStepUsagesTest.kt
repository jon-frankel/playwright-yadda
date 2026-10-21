package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usageView.UsageViewUtil
import org.jetbrains.plugins.cucumber.psi.GherkinStep

class YaddaStepUsagesTest : BasePlatformTestCase() {
  private val steps =
    """
    import { createStepLibrary } from "playwright-yadda";

    export const storefrontSteps = createStepLibrary();

    storefrontSteps.given("a customer is on the storefront", function () {});
    storefrontSteps.when(/they remove the "([^"]+)" item/, function (item: string) {});
    storefrontSteps.then("the cart is empty", function () {});
    storefrontSteps.notAStep("a customer is on the storefront");
    """
      .trimIndent()

  private val feature =
    """
    @Libraries=storefront
    Feature: Storefront
      Scenario: Clear cart
        Given a customer is on the storefront
        When they remove the "keyboard" item
        Then the cart is empty

      Scenario: Second visit
        Given a customer is on the storefront
    """
      .trimIndent()

  /** Opens steps.ts with the caret [shift] characters into the first occurrence of [needle]. */
  private fun openStepsWithCaretAt(needle: String, shift: Int = 0) {
    myFixture.addFileToProject("features/store.feature", feature)
    val offset = steps.indexOf(needle)
    assertTrue("'$needle' not found in fixture", offset >= 0)
    val caret = offset + shift
    myFixture.configureByText("steps.ts", steps.substring(0, caret) + "<caret>" + steps.substring(caret))
  }

  private fun usageTexts(): List<String> {
    val target = myFixture.elementAtCaret
    assertTrue("expected a Yadda step declaration, got $target", isYaddaStep(target))
    return myFixture.findUsages(target).map { it.file!!.name + ": " + it.element!!.text.trim() }.sorted()
  }

  private fun isYaddaStep(element: Any?) =
    element is PomTargetPsiElement && element.target is YaddaStepPomTarget

  private fun targetAtCaret() =
    TargetElementUtil.getInstance()
      .findTargetElement(
        myFixture.editor,
        TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED,
        myFixture.caretOffset,
      )

  fun `test find usages from the step text`() {
    openStepsWithCaretAt("a customer is on", shift = 4)

    assertEquals(
      listOf(
        "store.feature: Given a customer is on the storefront",
        "store.feature: Given a customer is on the storefront",
      ),
      usageTexts(),
    )
  }

  fun `test find usages from the method name`() {
    openStepsWithCaretAt(".given(", shift = 2)

    assertEquals(2, usageTexts().size)
  }

  fun `test regex step definition finds steps with parameters`() {
    openStepsWithCaretAt("they remove", shift = 3)

    assertEquals(listOf("store.feature: When they remove the \"keyboard\" item"), usageTexts())
  }

  fun `test only the matching definition's steps are reported`() {
    openStepsWithCaretAt("the cart is empty", shift = 2)

    assertEquals(listOf("store.feature: Then the cart is empty"), usageTexts())
  }

  fun `test cmd-b on a step definition shows usages instead of failing`() {
    openStepsWithCaretAt(".given(", shift = 2)

    val outcome =
      GotoDeclarationOrUsageHandler2.testGTDUOutcome(myFixture.editor, myFixture.file, myFixture.caretOffset)
    assertEquals(GotoDeclarationOrUsageHandler2.GTDUOutcome.SU, outcome)
  }

  fun `test cmd-b on the step text shows usages`() {
    openStepsWithCaretAt("a customer is on", shift = 4)

    val outcome =
      GotoDeclarationOrUsageHandler2.testGTDUOutcome(myFixture.editor, myFixture.file, myFixture.caretOffset)
    assertEquals(GotoDeclarationOrUsageHandler2.GTDUOutcome.SU, outcome)
  }

  fun `test the usages window labels the target by its step text`() {
    openStepsWithCaretAt("a customer is on", shift = 4)

    val target = myFixture.elementAtCaret
    assertEquals("step definition", UsageViewUtil.getType(target))
    assertEquals("a customer is on the storefront", UsageViewUtil.getLongName(target))
  }

  fun `test a regex definition is labelled by its regex source`() {
    openStepsWithCaretAt("they remove", shift = 3)

    assertEquals(
      """/they remove the "([^"]+)" item/""",
      UsageViewUtil.getLongName(myFixture.elementAtCaret),
    )
  }

  fun `test plain javascript with capitalised keywords`() {
    myFixture.addFileToProject("features/store.feature", feature)
    myFixture.configureByText(
      "steps.js",
      """
      import { Given } from "./library.js";
      Given("a customer is on the <caret>storefront", function () {});
      """
        .trimIndent(),
    )

    assertEquals(2, usageTexts().size)
  }

  // With no registry to read, which library defines a step is unknown, so nothing is ruled out.
  // (With a registry the two are told apart: see LibraryScopeTest.)
  fun `test identical steps in two libraries are not told apart without a registry`() {
    openStepsWithCaretAt("a customer is on", shift = 4)
    myFixture.addFileToProject(
      "steps/admin.ts",
      """
      export const adminSteps = createStepLibrary();
      adminSteps.given("a customer is on the storefront", function () {});
      """
        .trimIndent(),
    )
    val adminFeature =
      myFixture.addFileToProject(
        "features/admin.feature",
        "@Libraries=admin\nFeature: Admin\n  Scenario: S\n    Given a customer is on the storefront\n",
      )

    // Forward: the admin step resolves to both definitions.
    val step = PsiTreeUtil.findChildOfType(adminFeature, GherkinStep::class.java)!!
    val reference = step.references.filterIsInstance<PsiPolyVariantReference>().single()
    assertEquals(2, reference.multiResolve(false).size)

    // Reverse: usages of the storefront definition include the admin scenario's step.
    assertEquals(3, usageTexts().size)
  }

  fun `test the receiver is not a step declaration`() {
    openStepsWithCaretAt("storefrontSteps.given", shift = 4)

    assertFalse(isYaddaStep(targetAtCaret()))
  }

  fun `test a call that is not a step keyword is not a declaration`() {
    openStepsWithCaretAt("notAStep", shift = 2)

    assertFalse(isYaddaStep(targetAtCaret()))
  }
}
