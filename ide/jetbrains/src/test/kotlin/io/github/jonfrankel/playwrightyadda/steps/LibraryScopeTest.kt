package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.cucumber.inspections.CucumberStepInspection
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference
import java.io.File

/** Two libraries, `storefront` and `admin`, that define the same step text ("they remove the item"). */
class LibraryScopeTest : BasePlatformTestCase() {
  private fun add(name: String, text: String): PsiFile =
    myFixture.addFileToProject(name, text.trimIndent())

  private fun libraries(
    registryOptions: String = "",
    registryKeys: List<String> = KEYS,
    sharedStep: String? = null,
  ): PsiFile {
    val shared = sharedStep?.let { "\n%s.given(\"$it\", function () {});" } ?: ""
    add("common.ts", "export const commonSteps = createStepLibrary();" + shared.format("commonSteps"))
    add(
      "storefront.ts",
      """
      export const storefrontSteps = createStepLibrary();
      storefrontSteps.when("they remove the item", function () {});
      """ + shared.format("storefrontSteps"),
    )
    add(
      "admin.ts",
      """
      export const adminSteps = createStepLibrary();
      adminSteps.when("they remove the item", function () {});
      """,
    )
    val (common, storefront, admin) = registryKeys
    return add(
      "registry.ts",
      """
      import { createLibraryRegistry } from "playwright-yadda";
      import { commonSteps } from "./common.js";
      import { storefrontSteps } from "./storefront.js";
      import { adminSteps } from "./admin.js";
      export const registry = createLibraryRegistry({
        $common: commonSteps, $storefront: storefrontSteps, $admin: adminSteps,
      }$registryOptions);
      """,
    )
  }

  private fun feature(name: String, text: String): PsiFile = add("features/$name.feature", text)

  private fun openAt(file: PsiFile, needle: String, shift: Int = 0) {
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val offset = file.text.indexOf(needle)
    assertTrue("'$needle' not found in ${file.name}", offset >= 0)
    myFixture.editor.caretModel.moveToOffset(offset + shift)
  }

  /** Find Usages on the definition under the caret: the feature files whose steps run it. */
  private fun usedBy(): List<String> =
    myFixture.findUsages(myFixture.elementAtCaret).map { it.file!!.name }.sorted()

  /**
   * Cmd+B on the feature step under the caret: the files holding the definitions it goes to.
   * Like the platform (GotoDeclarationOrUsageHandler2), go-to handlers get the first say and the
   * step's own references are the fallback.
   */
  private fun goesTo(): List<String> {
    val fromHandlers =
      GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)
    val targets =
      if (fromHandlers.isNotEmpty()) fromHandlers.toList()
      else {
        val step =
          PsiTreeUtil.getParentOfType(
            myFixture.file.findElementAt(myFixture.caretOffset),
            GherkinStep::class.java,
          )!!
        step.references
          .filterIsInstance<CucumberStepReference>()
          .single()
          .multiResolve(false)
          .map { it.element!! }
      }
    return targets.map { it.containingFile.name }.sorted()
  }

  private fun definition(file: String): PsiFile =
    myFixture.psiManager.findFile(myFixture.findFileInTempDir(file)!!)!!

  private val storefrontFeature =
    """
    @Libraries=storefront
    Feature: Storefront
      Scenario: Remove
        Given they remove the item
    """

  private val adminFeature =
    """
    @Libraries=admin
    Feature: Admin
      Scenario: Remove
        Given they remove the item
    """

  fun `test usages of a definition only list scenarios that enable its library`() {
    libraries()
    feature("storefront", storefrontFeature)
    feature("admin", adminFeature)

    openAt(definition("storefront.ts"), "they remove", shift = 2)
    assertEquals(listOf("storefront.feature"), usedBy())

    openAt(definition("admin.ts"), "they remove", shift = 2)
    assertEquals(listOf("admin.feature"), usedBy())
  }

  fun `test cmd-b from a feature step goes only to the enabled definition`() {
    libraries()
    val admin = feature("admin", adminFeature)
    val storefront = feature("storefront", storefrontFeature)

    openAt(storefront, "they remove", shift = 2)
    assertEquals(listOf("storefront.ts"), goesTo())

    openAt(admin, "they remove", shift = 2)
    assertEquals(listOf("admin.ts"), goesTo())
  }

  fun `test a scenario tag adds to the feature tag`() {
    libraries()
    val feature =
      feature(
        "both",
        """
        @Libraries=storefront
        Feature: Both
          @Libraries=admin
          Scenario: Remove
            Given they remove the item
        """,
      )

    openAt(feature, "they remove", shift = 2)
    assertEquals(listOf("admin.ts", "storefront.ts"), goesTo())
  }

  fun `test override inheritance lets the scenario tag replace the feature tag`() {
    libraries(registryOptions = """, { inheritance: "override" }""")
    val feature =
      feature(
        "override",
        """
        @Libraries=storefront
        Feature: Override
          @Libraries=admin
          Scenario: Remove
            Given they remove the item
        """,
      )

    openAt(feature, "they remove", shift = 2)
    assertEquals(listOf("admin.ts"), goesTo())
  }

  fun `test default libraries are enabled for every scenario`() {
    libraries(registryOptions = """, { defaultLibraries: ["common", "admin"] }""")
    val storefront = feature("storefront", storefrontFeature)

    openAt(storefront, "they remove", shift = 2)
    assertEquals(listOf("admin.ts", "storefront.ts"), goesTo())
  }

  fun `test tag names and registry keys match regardless of case, spacing and quoting`() {
    libraries(
      registryOptions = """, { defaultLibraries: ["WP-Common"] }""",
      registryKeys = listOf("\"wp-common\"", "\"WP-Storefront\"", "\"wp-admin\""),
    )
    val feature =
      feature(
        "keys",
        """
        @LIBRARIES =  WP-Admin
        Feature: Keys
          Scenario: Remove
            Given they remove the item
        """,
      )

    openAt(feature, "they remove", shift = 2)
    assertEquals(listOf("admin.ts"), goesTo())
  }

  fun `test a suite level defaultLibraries overrides the registry's`() {
    libraries()
    add(
      "suite.spec.ts",
      """
      import { registry } from "./registry.js";
      defineFeatureSuite({ test, registry, features: "features", defaultLibraries: ["admin"] });
      """,
    )
    val storefront = feature("storefront", storefrontFeature)

    openAt(storefront, "they remove", shift = 2)
    assertEquals(listOf("admin.ts", "storefront.ts"), goesTo())
  }

  fun `test nothing is ruled out when the defaults cannot be read`() {
    libraries()
    add(
      "suite.spec.ts",
      """
      import { registry } from "./registry.js";
      const defaults = ["admin"];
      defineFeatureSuite({ test, registry, features: "features", defaultLibraries: defaults });
      """,
    )
    val storefront = feature("storefront", storefrontFeature)

    openAt(storefront, "they remove", shift = 2)
    assertEquals(listOf("admin.ts", "storefront.ts"), goesTo())
  }

  fun `test when every definition would be ruled out all of them are still offered`() {
    libraries()
    val feature =
      feature(
        "neither",
        """
        @Libraries=common
        Feature: Neither
          Scenario: Remove
            Given they remove the item
        """,
      )

    openAt(feature, "they remove", shift = 2)
    assertEquals(listOf("admin.ts", "storefront.ts"), goesTo())
  }

  fun `test nothing is ruled out for background steps`() {
    libraries()
    val feature =
      feature(
        "background",
        """
        @Libraries=storefront
        Feature: Background
          Background:
            Given they remove the item
          Scenario: S
            Given they remove the item
        """,
      )

    openAt(feature, "they remove", shift = 2)
    assertEquals(listOf("admin.ts", "storefront.ts"), goesTo())
  }

  fun `test nothing is ruled out without a readable registry`() {
    add("storefront.ts", "export const storefrontSteps = createStepLibrary();\nstorefrontSteps.when(\"they remove the item\", function () {});")
    add("admin.ts", "export const adminSteps = createStepLibrary();\nadminSteps.when(\"they remove the item\", function () {});")
    val storefront = feature("storefront", storefrontFeature)

    openAt(storefront, "they remove", shift = 2)
    assertEquals(listOf("admin.ts", "storefront.ts"), goesTo())
  }

  /** What the Cucumber step inspection says about the open feature file. */
  private fun stepWarnings(file: PsiFile): List<String> {
    myFixture.enableInspections(CucumberStepInspection::class.java)
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    return myFixture.doHighlighting().mapNotNull { it.description }.filter { "step reference" in it }
  }

  fun `test a step that the scenario's libraries resolve to one definition is not ambiguous`() {
    libraries()
    val admin = feature("admin", adminFeature)
    val storefront = feature("storefront", storefrontFeature)

    assertEquals(emptyList<String>(), stepWarnings(admin))
    assertEquals(emptyList<String>(), stepWarnings(storefront))
  }

  fun `test a step that is still ambiguous after narrowing keeps its warning`() {
    libraries()
    val both = feature("both", storefrontFeature.replace("@Libraries=storefront", "@Libraries=storefront, admin"))

    assertEquals(
      listOf("Ambiguous step reference with 2 definitions: they remove the item"),
      stepWarnings(both),
    )
  }

  fun `test the ambiguity warning stays when libraries cannot be read`() {
    add("storefront.ts", "export const storefrontSteps = createStepLibrary();\nstorefrontSteps.when(\"they remove the item\", function () {});")
    add("admin.ts", "export const adminSteps = createStepLibrary();\nadminSteps.when(\"they remove the item\", function () {});")
    val storefront = feature("storefront", storefrontFeature)

    assertEquals(
      listOf("Ambiguous step reference with 2 definitions: they remove the item"),
      stepWarnings(storefront),
    )
  }

  fun `test a step defined in two enabled libraries is ambiguous`() {
    // common is a default library, so a second definition in storefront is a real clash.
    libraries(registryOptions = """, { defaultLibraries: ["common"] }""", sharedStep = "a clash with common")
    val feature =
      feature(
        "clash",
        """
        @Libraries=storefront
        Feature: Clash
          Scenario: S
            Given a clash with common
        """,
      )

    assertEquals(
      listOf("Ambiguous step reference with 2 definitions: a clash with common"),
      stepWarnings(feature),
    )
  }

  fun `test the chooser for an ambiguous step shows which file each definition is in`() {
    libraries(registryOptions = """, { defaultLibraries: ["common"] }""", sharedStep = "a clash with common")
    val feature =
      feature(
        "clash",
        """
        @Libraries=storefront
        Feature: Clash
          Scenario: S
            Given a clash with common
        """,
      )
    openAt(feature, "a clash", shift = 2)

    val targets =
      GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)
    // The same text twice: the rows only differ by the path next to them, which is what the popup renders.
    val rows = targets.map { targetPresentation(it).let { p -> p.presentableText to p.containerText } }
    assertEquals(
      listOf("\"a clash with common\"" to "common.ts", "\"a clash with common\"" to "storefront.ts"),
      rows.sortedBy { it.second },
    )
    // ...and each row still goes to its own definition.
    assertEquals(
      listOf("common.ts", "storefront.ts"),
      targets.map { it.navigationElement.containingFile.name }.sorted(),
    )
    assertTrue(targets.all { it.navigationElement is JSLiteralExpression })
  }

  fun `test an undefined step is still reported`() {
    libraries()
    val feature =
      feature(
        "undefined",
        """
        @Libraries=storefront
        Feature: Undefined
          Scenario: Nothing defines this
            Given nobody wrote this step
        """,
      )

    assertEquals(listOf("Undefined step reference: nobody wrote this step"), stepWarnings(feature))
  }

  /** Copies steps/ and features/ of a real example project into the fixture, keeping relative paths. */
  private fun copyExample(name: String): Map<String, PsiFile> {
    val root = File("../../examples/$name")
    assertTrue("missing ${root.absolutePath}", root.isDirectory)
    return listOf("steps", "features")
      .flatMap { dir -> File(root, dir).listFiles { f -> f.isFile }!!.map { "$dir/${it.name}" to it } }
      .associate { (path, file) -> path to myFixture.addFileToProject(path, file.readText()) }
  }

  fun `test examples-basic keeps the colliding step text apart`() {
    val files = copyExample("basic")

    openAt(files["features/storefront.feature"]!!, "they remove", shift = 2)
    assertEquals(listOf("storefront.ts"), goesTo())
    openAt(files["features/admin.feature"]!!, "they remove", shift = 2)
    assertEquals(listOf("admin.ts"), goesTo())

    // (Anchored on the call: the same phrase also appears in a comment above each definition.)
    openAt(files["steps/admin.ts"]!!, """.when("they remove""", shift = 10)
    assertEquals(listOf("admin.feature"), usedBy())
    openAt(files["steps/storefront.ts"]!!, """.when("they remove""", shift = 10)
    assertEquals(listOf("storefront.feature"), usedBy())
  }

  fun `test examples-wordpress never rules out a definition its scenario uses`() {
    val files = copyExample("wordpress")
    val feature = files["features/manage_posts.feature"]!!

    // @Libraries=wp-common, wp-auth, wp-posts on one line, quoted hyphenated registry keys, and
    // defaultLibraries: ["wp-common"]: every step's definition must stay visible.
    val steps = PsiTreeUtil.findChildrenOfType(feature, GherkinStep::class.java)
    assertEquals(6, steps.size)
    for (step in steps) {
      val definitions =
        step.references.filterIsInstance<CucumberStepReference>().single().multiResolve(false).map { it.element }
      assertEquals("'${step.name}' should resolve to one definition", 1, definitions.size)
      assertTrue(
        "'${step.name}' must stay enabled",
        LibraryScope.isEnabledFor(definitions.single() as JSLiteralExpression, step),
      )
    }
  }

  companion object {
    private val KEYS = listOf("common", "storefront", "admin")
  }
}
