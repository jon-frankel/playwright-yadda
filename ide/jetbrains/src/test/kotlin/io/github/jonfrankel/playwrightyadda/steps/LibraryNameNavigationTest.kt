package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.jonfrankel.playwrightyadda.libraries.LibraryNames

class LibraryNameNavigationTest : BasePlatformTestCase() {
  private val registry = """
    import { createLibraryRegistry } from "playwright-yadda";
    import { storefrontSteps } from "./storefront.js";
    import { wpCommonSteps } from "./wp-common.js";
    export const registry = createLibraryRegistry({
      storefront: storefrontSteps,
      "wp-common": wpCommonSteps,
    });
  """.trimIndent()

  private fun register(featureText: String) {
    myFixture.addFileToProject("storefront.ts", "export const storefrontSteps = createStepLibrary();")
    myFixture.addFileToProject("wp-common.ts", "export const wpCommonSteps = createStepLibrary();")
    myFixture.addFileToProject("registry.ts", registry)
    myFixture.configureByText("a.feature", featureText.trimIndent())
  }

  /** Cmd+B with the caret just before [needle]: the registry lines it goes to. */
  private fun goesTo(needle: String, shift: Int = 0): List<String> {
    myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf(needle) + shift)
    return GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)
      .map { target -> target.containingFile.name + ": " + target.text.lines().first().trim().removeSuffix(",") }
  }

  fun `test a library name goes to the registry property that defines it`() {
    register("@Libraries=storefront\nFeature: F\n  Scenario: S\n    Given x")
    assertEquals(listOf("registry.ts: storefront: storefrontSteps"), goesTo("storefront", 3))
  }

  fun `test each name in a list goes to its own property, including quoted keys and mixed case`() {
    register("@Libraries=Storefront, wp-common\nFeature: F\n  Scenario: S\n    Given x")
    assertEquals(listOf("registry.ts: storefront: storefrontSteps"), goesTo("Storefront", 2))
    assertEquals(listOf("registry.ts: \"wp-common\": wpCommonSteps"), goesTo("wp-common", 2))
  }

  fun `test a scenario's own tag works the same`() {
    register("Feature: F\n  @Libraries=storefront\n  Scenario: S\n    Given x")
    assertEquals(listOf("registry.ts: storefront: storefrontSteps"), goesTo("storefront", 3))
  }

  fun `test a name no registry defines goes nowhere`() {
    register("@Libraries=storefornt\nFeature: F\n  Scenario: S\n    Given x")
    assertEquals(emptyList<String>(), goesTo("storefornt", 3))
  }

  fun `test the tag name and other tags are not library names`() {
    register("@Libraries=storefront\n@storefront\nFeature: F\n  Scenario: S\n    Given x")
    assertEquals(emptyList<String>(), goesTo("Libraries", 2))
    assertEquals(emptyList<String>(), goesTo("@storefront\nFeature", 3))
  }

  fun `test the name under the caret is found by offset`() {
    val text = "@Libraries=a, bb ,c\nFeature: F"
    fun at(needle: String, shift: Int) = LibraryNames.nameAt(text, text.indexOf(needle) + shift)
    assertEquals("a", at("a,", 0))
    assertEquals("bb", at("bb", 1))
    assertEquals("c", at("c\n", 0))
    assertNull(LibraryNames.nameAt(text, 3))
    assertNull(LibraryNames.nameAt(text, text.indexOf("Feature") + 2))
  }
}

/** The other direction: from the registry property to the `@Libraries=` names that use it. */
class LibraryNameUsagesTest : BasePlatformTestCase() {
  private fun register() {
    myFixture.addFileToProject("storefront.ts", "export const storefrontSteps = createStepLibrary();")
    myFixture.addFileToProject("admin.ts", "export const adminSteps = createStepLibrary();")
    myFixture.addFileToProject(
      "features/a.feature",
      "@Libraries=Storefront, admin\nFeature: A\n  Scenario: S\n    Given x\n  @Libraries=storefront\n  Scenario: T\n    Given x\n",
    )
    myFixture.addFileToProject(
      "features/b.feature",
      "Feature: B\n  @Libraries=admin\n  Scenario: S\n    Given x\n  @Libraries=storefront\n  Scenario: T\n    Given x\n",
    )
    myFixture.addFileToProject("features/c.feature", "@Libraries=admin\nFeature: C\n  Scenario: S\n    Given x\n")
    myFixture.configureByText(
      "registry.ts",
      """
      import { createLibraryRegistry } from "playwright-yadda";
      import { storefrontSteps } from "./storefront.js";
      import { adminSteps } from "./admin.js";
      export const registry = createLibraryRegistry({
        <caret>storefront: storefrontSteps,
        admin: adminSteps,
      });
      const unrelated = { storefront: 1 };
      """.trimIndent(),
    )
  }

  private fun usedBy(): List<String> =
    myFixture.findUsages(myFixture.elementAtCaret)
      .filter { it.file?.name?.endsWith(".feature") == true }
      .map { it.file!!.name + ":" + (it.file!!.text.substring(it.navigationRange.startOffset, it.navigationRange.endOffset)) }
      .sorted()

  fun `test a registry key lists the feature and scenario tags that name it`() {
    register()
    assertEquals(listOf("a.feature:Storefront", "a.feature:storefront", "b.feature:storefront"), usedBy())
  }

  fun `test a key in some other object is not a library`() {
    register()
    val offset = myFixture.file.text.indexOf("{ storefront: 1") + 2
    myFixture.editor.caretModel.moveToOffset(offset)
    assertEquals(emptyList<String>(), usedBy())
  }

  fun `test renaming a registry key renames every use of the name`() {
    register()
    myFixture.renameElementAtCaret("shop")

    assertTrue(myFixture.file.text.contains("shop: storefrontSteps"))
    fun textOf(path: String) = FileDocumentManager.getInstance().getDocument(myFixture.findFileInTempDir(path))!!.text
    // Two names in one file are both rewritten, though the first rewrite moves the second.
    assertEquals(listOf("@Libraries=shop, admin", "  @Libraries=shop"), textOf("features/a.feature").lines().filter { "@" in it })
    assertTrue(textOf("features/b.feature").contains("@Libraries=shop\n"))
    assertFalse(textOf("features/b.feature").contains("storefront"))
  }
}
