package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.jonfrankel.playwrightyadda.steps.YaddaCreateAllStepsIntention
import io.github.jonfrankel.playwrightyadda.steps.YaddaCreateStepIntention
import io.github.jonfrankel.playwrightyadda.steps.YaddaStepCreator
import org.jetbrains.plugins.cucumber.inspections.CucumberStepInspection
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/** What `StepSkeleton` writes for a step, without the IDE. */
class StepSkeletonTest : junit.framework.TestCase() {
  private fun skeleton(text: String, typed: Boolean = false, name: String = "a.feature"): String? {
    val steps = YaddaGrammar.analyze(text.trimIndent()).steps.values.sortedBy { it.line }
    return StepSkeleton.of(steps.last())?.render("lib", typed)
  }

  private fun scenario(vararg steps: String) = "Feature: F\n  Scenario: S\n" + steps.joinToString("") { "    $it\n" }

  fun `test a step of plain words is a string and the keyword picks the method`() {
    assertEquals("lib.given(\"the moon is full\", async function () {\n  \n});", skeleton(scenario("Given the moon is full")))
    assertTrue(skeleton(scenario("When they leave"))!!.startsWith("lib.when(\"they leave\""))
    assertTrue(skeleton(scenario("Then it is dark"))!!.startsWith("lib.then(\"it is dark\""))
  }

  fun `test other English keywords map to given, when and then`() {
    assertTrue(skeleton(scenario("With a cart"))!!.startsWith("lib.given("))
    assertTrue(skeleton(scenario("If a cart"))!!.startsWith("lib.when("))
    assertTrue(skeleton(scenario("Expect a cart"))!!.startsWith("lib.then("))
  }

  fun `test And and But take the method of the step before them`() {
    assertTrue(skeleton(scenario("When they leave", "And the door closes"))!!.startsWith("lib.when(\"the door closes\""))
    assertTrue(skeleton(scenario("Then it is dark", "But the lights are on"))!!.startsWith("lib.then(\"the lights are on\""))
    assertTrue(skeleton(scenario("And nothing came before"))!!.startsWith("lib.given("))
  }

  fun `test text with regex characters is a regex with them escaped`() {
    assertEquals(
      "lib.given(/^the total is 5\\.00 \\(approx\\)$/, async function () {\n  \n});",
      skeleton(scenario("Given the total is 5.00 (approx)")),
    )
  }

  fun `test a fenced block is a parameter`() {
    val text = scenario("Given the cart holds:", "  ---", "  sku | qty", "  ---")
    assertEquals(
      "lib.given(/^the cart holds:\\n([\\s\\S]+)\$/, async function (block: string) {\n  \n});",
      skeleton(text, typed = true),
    )
  }

  fun `test an outline placeholder is a capture group named after it`() {
    val text = "Feature: F\n  Scenario: S [qty]\n    Given a cart of [qty] keyboards at [row.index]\n  Examples:\n    | qty | total |\n    | 1 | 80 |\n"
    assertEquals(
      "lib.given(/^a cart of (.+) keyboards at \\[row\\.index\\]\$/, async function (qty: string) {\n  \n});",
      skeleton(text, typed = true),
    )
  }

  fun `test brackets outside an outline are text`() {
    assertEquals(
      "lib.given(/^the list \\[a\\] is shown$/, async function () {\n  \n});",
      skeleton(scenario("Given the list [a] is shown")),
    )
  }

  fun `test a placeholder that is a reserved word does not become a parameter name`() {
    val text = "Feature: F\n  Scenario: S\n    Given a [class] thing\n  Examples:\n    | class |\n    | x |\n"
    assertTrue(skeleton(text, typed = true)!!.contains("(valueClass: string)"))
  }

  fun `test a step with no keyword has no skeleton`() {
    assertNull(skeleton(scenario("the moon is full")))
    assertNull(skeleton(scenario("Given")))
  }
}

/** "Create Yadda step definition", in place of the Cucumber.js plugin's. */
class YaddaCreateStepTest : BasePlatformTestCase() {
  private fun project(depends: Boolean = true) {
    if (depends) myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "1" } }""")
    myFixture.addFileToProject("common.ts", "export const commonSteps = createStepLibrary();\n")
    myFixture.addFileToProject(
      "storefront.ts",
      "export const storefrontSteps = createStepLibrary();\nstorefrontSteps.when(\"they remove the item\", function () {});\n",
    )
    myFixture.addFileToProject(
      "registry.ts",
      """
      import { createLibraryRegistry } from "playwright-yadda";
      import { commonSteps } from "./common.js";
      import { storefrontSteps } from "./storefront.js";
      export const registry = createLibraryRegistry({
        common: commonSteps,
        storefront: storefrontSteps,
      });
      """.trimIndent(),
    )
  }

  private fun open(path: String, text: String, caretAt: String): PsiFile {
    val file = myFixture.addFileToProject(path, text)
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.editor.caretModel.moveToOffset(text.indexOf(caretAt))
    return file
  }

  private fun text(path: String) = FileDocumentManager.getInstance().getDocument(myFixture.findFileInTempDir(path))!!.text

  /**
   * Runs the intention called [name] at the caret. Not through `launchAction`, which afterwards compares
   * the PSI with one re-created from the file's type: that is Gherkin's for a `.feature` file of a Yadda
   * project, whose PSI is Yadda's.
   */
  private fun create(name: String) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    myFixture.findSingleIntention(name).invoke(project, myFixture.editor, myFixture.file)
  }

  private val feature = "@Libraries=storefront\nFeature: F\n  Scenario: S\n    Given the moon is full\n    And the sun is out\n"

  private fun intentions() = myFixture.availableIntentions.map { it.text }

  fun `test the definition goes into the library the scenario enables, in the Yadda style`() {
    project()
    open("features/a.feature", feature, "the moon")
    create("Create Yadda step definition")
    assertEquals(
      "export const storefrontSteps = createStepLibrary();\n" +
        "storefrontSteps.when(\"they remove the item\", function () {});\n\n" +
        "storefrontSteps.given(\"the moon is full\", async function () {\n\n});\n",
      text("storefront.ts"),
    )
  }

  fun `test several candidates are offered the scenario's own library first`() {
    project()
    var offered: List<String> = emptyList()
    YaddaStepCreator.chooser = YaddaStepCreator.Chooser { _, _, targets, onChosen ->
      offered = targets.map { it.receiver }
      onChosen(targets.last())
    }
    try {
      open("features/a.feature", feature, "the moon")
      create("Create Yadda step definition")
    } finally {
      YaddaStepCreator.chooser = YaddaStepCreator.Chooser { _, _, targets, onChosen -> onChosen(targets.first()) }
    }
    assertEquals(listOf("storefrontSteps", "commonSteps"), offered)
    assertTrue(text("common.ts").contains("commonSteps.given(\"the moon is full\""))
    assertFalse(text("storefront.ts").contains("the moon"))
  }

  fun `test a step with no definition gets a fix only from this plugin`() {
    project()
    myFixture.enableInspections(CucumberStepInspection::class.java)
    open("features/a.feature", feature, "the moon")
    myFixture.doHighlighting()
    val offered = intentions()
    assertTrue(offered.toString(), "Create Yadda step definition" in offered)
    assertTrue(offered.toString(), "Create all Yadda step definitions" in offered)
    assertFalse(offered.toString(), "Create step definition" in offered)
    assertFalse(offered.toString(), "Create all step definitions" in offered)
  }

  fun `test outside a Yadda project the Cucumber fixes stay and this one is not offered`() {
    project(depends = false)
    myFixture.enableInspections(CucumberStepInspection::class.java)
    open("features/a.feature", feature, "the moon")
    myFixture.doHighlighting()
    val offered = intentions()
    assertTrue(offered.toString(), "Create step definition" in offered)
    assertFalse(offered.toString(), "Create Yadda step definition" in offered)
  }

  fun `test a step that has a definition is not offered one`() {
    project()
    open("features/a.feature", "@Libraries=storefront\nFeature: F\n  Scenario: S\n    When they remove the item\n", "they remove")
    assertFalse(intentions().toString(), "Create Yadda step definition" in intentions())
  }

  fun `test a step with no keyword is offered nothing and nothing throws`() {
    project()
    myFixture.enableInspections(CucumberStepInspection::class.java)
    open("features/a.feature", "@Libraries=storefront\nFeature: F\n  Scenario: S\n    the moon is full\n", "the moon")
    myFixture.doHighlighting()
    val offered = intentions()
    assertFalse(offered.toString(), offered.any { it.contains("step definition", ignoreCase = true) })
  }

  fun `test create all writes every missing definition`() {
    project()
    open("features/a.feature", "@Libraries=storefront\nFeature: F\n  Scenario: S\n    Given the moon is full\n    When they remove the item\n    Then the sun is out\n", "the moon")
    create("Create all Yadda step definitions")
    val written = text("storefront.ts")
    assertTrue(written, written.contains("storefrontSteps.given(\"the moon is full\""))
    assertTrue(written, written.contains("storefrontSteps.then(\"the sun is out\""))
    assertEquals(1, Regex("they remove the item").findAll(written).count())
  }

  fun `test a step of an outline with a block gets a typed regex`() {
    project()
    val text = "@Libraries=storefront\nFeature: F\n  Scenario: S [qty]\n    Given the cart holds [qty]:\n      ---\n      sku\n      ---\n  Examples:\n    | qty |\n    | 1 |\n"
    open("features/a.feature", text, "the cart")
    create("Create Yadda step definition")
    assertTrue(
      text("storefront.ts"),
      text("storefront.ts").contains("storefrontSteps.given(/^the cart holds (.+):\\n([\\s\\S]+)\$/, async function (qty: string, block: string) {"),
    )
  }

  fun `test after it is created the step resolves`() {
    project()
    val file = open("features/a.feature", "@Libraries=storefront\nFeature: F\n  Scenario: S\n    Given the moon is full\n", "the moon")
    create("Create Yadda step definition")
    val step = PsiTreeUtil.findChildOfType(file, GherkinStep::class.java)!!
    assertNotNull(YaddaStepCreator.undefinedAt(file, step.textOffset).let { if (it == null) "resolved" else null })
  }

  fun `test a Markdown feature gets the same fix`() {
    project()
    val md = "@Libraries=storefront\n\n# Feature: F\n\n## Scenario: S\n\n- Given the moon is full\n"
    open("features/a.md", md, "Given the moon")
    create("Create Yadda step definition")
    assertTrue(text("storefront.ts"), text("storefront.ts").contains("storefrontSteps.given(\"the moon is full\""))
  }

  fun `test a README in a Yadda project is offered nothing`() {
    project()
    open("README.md", "# Title\n\n- Given a thing\n", "Given")
    assertFalse(intentions().toString(), "Create Yadda step definition" in intentions())
  }

  fun `test the intentions have a description`() {
    assertEquals("Create Yadda step definition", YaddaCreateStepIntention().familyName)
    assertEquals("Create Yadda step definition", YaddaCreateAllStepsIntention().familyName)
  }
}
