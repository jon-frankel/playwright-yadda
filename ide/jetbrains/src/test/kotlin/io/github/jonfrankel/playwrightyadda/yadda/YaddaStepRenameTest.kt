package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.util.Disposer
import io.github.jonfrankel.playwrightyadda.steps.LibraryScope
import io.github.jonfrankel.playwrightyadda.steps.StepPattern
import io.github.jonfrankel.playwrightyadda.steps.StepPatternParse
import io.github.jonfrankel.playwrightyadda.steps.StepRenamer
import io.github.jonfrankel.playwrightyadda.steps.YaddaMarkdownStepReference
import io.github.jonfrankel.playwrightyadda.steps.YaddaRenameHandlerRegistry
import io.github.jonfrankel.playwrightyadda.steps.YaddaStepRenameDialog
import io.github.jonfrankel.playwrightyadda.steps.YaddaStepRenameHandler
import io.github.jonfrankel.playwrightyadda.steps.YaddaSteps
import org.jetbrains.plugins.cucumber.psi.GherkinStep

/** How a definition's pattern is split into the text a rename changes and the groups it keeps. */
class StepPatternTest : junit.framework.TestCase() {
  private fun parse(source: String): StepPattern = (StepPattern.parse(source) as StepPatternParse.Ok).pattern

  private fun unsupported(source: String): String = (StepPattern.parse(source) as StepPatternParse.Unsupported).reason

  fun `test plain text has no groups`() {
    val pattern = parse("the moon is full")
    assertEquals(listOf("the moon is full"), pattern.texts)
    assertEquals(emptyList<String>(), pattern.groups)
  }

  fun `test the text around a group is split from it, and anchors are noted`() {
    val pattern = parse("^the total is ([\\d.]+) dollars$")
    assertEquals(listOf("the total is ", " dollars"), pattern.texts)
    assertEquals(listOf("([\\d.]+)"), pattern.groups)
    assertTrue(pattern.anchoredStart && pattern.anchoredEnd)
  }

  fun `test a group keeps its quantifier and a group that holds a group is one`() {
    val pattern = parse("^the rows are:(?:\\n([\\s\\S]+))?$")
    assertEquals(listOf("the rows are:", ""), pattern.texts)
    assertEquals(listOf("(?:\\n([\\s\\S]+))?"), pattern.groups)
  }

  fun `test an escaped character is literal text`() {
    assertEquals(listOf("it costs \$5.00 (really)"), parse("it costs \\\$5\\.00 \\(really\\)").texts)
  }

  fun `test a pattern with the pieces of a regex outside a group can't be split`() {
    assertTrue(unsupported("the moon is full.").contains("'.'"))
    assertTrue(unsupported("I have \\d items").contains("\\d"))
    assertTrue(unsupported("a|b").contains("'|'"))
    assertTrue(unsupported("cukes?").contains("'?'"))
    assertTrue(unsupported("the [abc]").contains("'['"))
    assertTrue(unsupported("the (open").contains("never closed"))
  }

  fun `test text is written back with what a pattern needs escaped`() {
    val pattern = parse("^the total is ([\\d.]+)$")
    assertEquals("^the sum \\(really\\)\\? is ([\\d.]+)$", pattern.source(listOf("the sum (really)? is ", ""), regexLiteral = false))
    assertEquals("^a\\/b ([\\d.]+)$", pattern.source(listOf("a/b ", ""), regexLiteral = true))
    assertEquals("^a/b ([\\d.]+)$", pattern.source(listOf("a/b ", ""), regexLiteral = false))
  }

  fun `test a step is matched anywhere an unanchored pattern fits, and whole when anchored`() {
    val anchored = parse("^the total is ([\\d.]+)$")
    assertNotNull(anchored.find("the total is 80.00"))
    assertNull(anchored.find("the total is 80.00 now"))
    val loose = parse("the total is ([\\d.]+)")
    val match = loose.find("so the total is 80.00 now")!!
    assertEquals(3, match.start)
    assertEquals(listOf(16..20), match.groups)
  }
}

/** Renaming a step renames its definition and every step that runs it. */
class YaddaStepRenameTest : BasePlatformTestCase() {
  private val definitions = """
    export const lib = createStepLibrary();
    lib.given("the moon is full", function () {});
    lib.then(/^the total is ([\d.]+)${'$'}/, function () {});
    lib.given(/^the cart holds:\n([\s\S]+)${'$'}/, function (block) {});
    lib.when("they leave", function () {});
  """.trimIndent()

  private fun project(extra: String = "") {
    myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "1" } }""")
    myFixture.addFileToProject("steps.ts", definitions + extra)
  }

  private fun text(path: String): String = FileDocumentManager.getInstance().getDocument(myFixture.findFileInTempDir(path))!!.text

  private fun stepIn(file: PsiFile, containing: String): GherkinStep =
    PsiTreeUtil.findChildrenOfType(file, GherkinStep::class.java).first { it.text.contains(containing) }

  private fun context(step: GherkinStep): DataContext =
    SimpleDataContext.builder().add(CommonDataKeys.PSI_ELEMENT, step).add(CommonDataKeys.PROJECT, project).build()

  /** Shift+F6 on [needle], answered with [newText]: the hint the IDE would show instead, if there is one. */
  private fun rename(path: String, needle: String, newText: String): String? {
    val file = myFixture.psiManager.findFile(myFixture.findFileInTempDir(path))!!
    val step = stepIn(file, needle)
    YaddaStepRenameHandler.answerWith { newText }
    return try {
      YaddaStepRenameHandler().invoke(project, null, null, context(step))
      null
    } catch (hint: CommonRefactoringUtil.RefactoringErrorHintException) {
      hint.message
    } finally {
      YaddaStepRenameHandler.answerWith(null)
    }
  }

  private fun feature(path: String, text: String) = myFixture.addFileToProject(path, text)

  fun `test a step and its string definition are renamed together, in every feature file`() {
    project()
    feature("features/a.feature", "Feature: A\n  Scenario: S\n    Given the moon is full\n")
    feature("features/b.yadda.feature", "Feature: B\n  Scenario: S\n    Given the moon is full\n    And the moon is full\n")
    assertNull(rename("features/a.feature", "the moon is full", "the moon is up"))
    assertEquals("Feature: A\n  Scenario: S\n    Given the moon is up\n", text("features/a.feature"))
    assertEquals("Feature: B\n  Scenario: S\n    Given the moon is up\n    And the moon is up\n", text("features/b.yadda.feature"))
    assertTrue(text("steps.ts").contains("lib.given(\"the moon is up\", function () {});"))
  }

  fun `test a regex definition keeps its flags and its group, and the steps keep what the group matched`() {
    project()
    feature("features/a.feature", "Feature: A\n  Scenario: S\n    Then the total is 80.00\n    Then the total is 5\n")
    assertNull(rename("features/a.feature", "the total is 80.00", "the sum is ([\\d.]+)"))
    assertEquals("Feature: A\n  Scenario: S\n    Then the sum is 80.00\n    Then the sum is 5\n", text("features/a.feature"))
    assertTrue(text("steps.ts").contains("lib.then(/^the sum is ([\\d.]+)$/, function () {});"))
  }

  fun `test a step with a fenced block is renamed and its block is left as it is`() {
    project()
    val before = "Feature: A\n  Scenario: S\n    Given the cart holds:\n      ---\n      | sku | qty |\n      | KB-1 | 1 |\n      ---\n    When they leave\n"
    feature("features/a.yadda.feature", before)
    assertNull(rename("features/a.yadda.feature", "the cart holds:", "the basket holds:\\n([\\s\\S]+)"))
    assertEquals(before.replace("the cart holds:", "the basket holds:"), text("features/a.yadda.feature"))
    assertTrue(text("steps.ts").contains("lib.given(/^the basket holds:\\n([\\s\\S]+)$/, function (block) {});"))
  }

  fun `test an outline step keeps its placeholder`() {
    project()
    val outline = "Feature: A\n  Scenario: S [total]\n    Then the total is [total]\n  Examples:\n    | total |\n    | 80.00 |\n    | 160.00 |\n"
    feature("features/a.yadda.feature", outline)
    assertNull(rename("features/a.yadda.feature", "the total is [total]", "the sum is ([\\d.]+)"))
    assertEquals(outline.replace("Then the total is", "Then the sum is"), text("features/a.yadda.feature"))
    assertTrue(text("steps.ts").contains("/^the sum is ([\\d.]+)$/"))
  }

  fun `test a step with no keyword is renamed`() {
    project()
    feature("features/a.yadda.feature", "Feature: A\n  Scenario: S\n    the moon is full\n")
    assertNull(rename("features/a.yadda.feature", "the moon is full", "the moon is up"))
    assertEquals("Feature: A\n  Scenario: S\n    the moon is up\n", text("features/a.yadda.feature"))
  }

  fun `test only the steps of the scenarios that enable the definition's library are renamed`() {
    myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "1" } }""")
    myFixture.addFileToProject("storefront.ts", "export const storefrontSteps = createStepLibrary();\nstorefrontSteps.when(\"they remove the item\", function () {});")
    myFixture.addFileToProject("admin.ts", "export const adminSteps = createStepLibrary();\nadminSteps.when(\"they remove the item\", function () {});")
    myFixture.addFileToProject(
      "registry.ts",
      """
      import { createLibraryRegistry } from "playwright-yadda";
      import { storefrontSteps } from "./storefront.js";
      import { adminSteps } from "./admin.js";
      export const registry = createLibraryRegistry({ storefront: storefrontSteps, admin: adminSteps });
      """.trimIndent(),
    )
    feature(
      "features/a.yadda.feature",
      "Feature: A\n  @Libraries=storefront\n  Scenario: One\n    When they remove the item\n  @Libraries=admin\n  Scenario: Two\n    When they remove the item\n",
    )
    assertNull(rename("features/a.yadda.feature", "they remove the item", "they delete the item"))
    assertEquals(
      "Feature: A\n  @Libraries=storefront\n  Scenario: One\n    When they delete the item\n  @Libraries=admin\n  Scenario: Two\n    When they remove the item\n",
      text("features/a.yadda.feature"),
    )
    assertTrue(text("storefront.ts").contains("\"they delete the item\""))
    assertTrue(text("admin.ts").contains("\"they remove the item\""))
  }

  fun `test a step of a Markdown feature is renamed with the rest`() {
    project()
    feature("features/a.yadda.feature", "Feature: A\n  Scenario: S\n    Given the moon is full\n")
    val markdown = "# Feature: M\n\n## Scenario: S\n\n- Given the moon is full\n"
    feature("features/m.md", markdown)
    assertNull(rename("features/a.yadda.feature", "the moon is full", "the moon is up"))
    assertEquals("# Feature: M\n\n## Scenario: S\n\n- Given the moon is up\n", text("features/m.md"))
    assertEquals("Feature: A\n  Scenario: S\n    Given the moon is up\n", text("features/a.yadda.feature"))
  }

  fun `test a step is renamed from a Markdown feature too`() {
    project()
    feature("features/a.yadda.feature", "Feature: A\n  Scenario: S\n    Given the moon is full\n")
    val file = myFixture.configureByText("m.md", "# Feature: M\n\n## Scenario: S\n\n- Given the moon is full\n")
    myFixture.editor.caretModel.moveToOffset(file.text.indexOf("moon"))
    val data = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.PSI_FILE, file).add(CommonDataKeys.EDITOR, myFixture.editor).build()
    val handler = RenameHandlerRegistry.getInstance().getRenameHandler(data)
    assertTrue(handler.toString(), handler is YaddaStepRenameHandler)
    YaddaStepRenameHandler.answerWith { "the moon is up" }
    try {
      handler!!.invoke(project, myFixture.editor, file, data)
    } finally {
      YaddaStepRenameHandler.answerWith(null)
    }
    assertEquals("- Given the moon is up", myFixture.editor.document.text.lines().last { it.isNotBlank() })
    assertEquals("Feature: A\n  Scenario: S\n    Given the moon is up\n", text("features/a.yadda.feature"))
  }

  fun `test nothing is changed if a step can't be rewritten`() {
    project()
    val broken = "Feature: A\n  Scenario: S\n    Given the moon is full\n"
    feature("features/a.yadda.feature", broken)
    // The new pattern has to keep the groups it had, and has to say something new.
    assertEquals("That is the name it already has.", rename("features/a.yadda.feature", "the moon is full", "the moon is full"))
    assertTrue(rename("features/a.yadda.feature", "the moon is full", "the moon (is) full")!!.contains("groups"))
    assertTrue(rename("features/a.yadda.feature", "the moon is full", "the moon is full.")!!.contains("plain text"))
    assertEquals(broken, text("features/a.yadda.feature"))
    assertTrue(text("steps.ts").contains("lib.given(\"the moon is full\""))
  }

  fun `test one step that can't be rewritten stops the whole rename`() {
    project("\nlib.then(/^the (\\d+)\\.(\\d+) total${'$'}/, function () {});\n")
    // A row's 80.50 is where the two groups meet, which is inside the placeholder, not at its edge.
    val plain = "Feature: A\n  Scenario: S\n    Then the 3.4 total\n"
    val outline = "Feature: B\n  Scenario: S [total]\n    Then the [total] total\n  Examples:\n    | total |\n    | 80.50 |\n"
    feature("features/a.yadda.feature", plain)
    feature("features/b.yadda.feature", outline)
    val message = rename("features/a.yadda.feature", "the 3.4 total", "a (\\d+)\\.(\\d+) sum")
    assertNotNull(message)
    assertTrue(message!!, message.contains("can't be rewritten"))
    assertEquals(plain, text("features/a.yadda.feature"))
    assertEquals(outline, text("features/b.yadda.feature"))
    assertTrue(text("steps.ts").contains("/^the (\\d+)\\.(\\d+) total$/"))
  }

  fun `test a definition written with regex syntax outside a group is explained, not rewritten`() {
    project("\nlib.then(/^there are (\\d+) cukes?${'$'}/, function () {});\n")
    feature("features/a.yadda.feature", "Feature: A\n  Scenario: S\n    Then there are 3 cukes\n")
    val message = rename("features/a.yadda.feature", "cukes", "there were ")
    assertTrue(message!!, message.contains("by hand"))
    assertEquals("Feature: A\n  Scenario: S\n    Then there are 3 cukes\n", text("features/a.yadda.feature"))
  }

  fun `test a step with no definition says so`() {
    project()
    feature("features/a.yadda.feature", "Feature: A\n  Scenario: S\n    Given nothing defines this\n")
    assertEquals("This step has no step definition to rename.", rename("features/a.yadda.feature", "nothing defines", "x"))
  }

  fun `test the IDE offers this handler alone, for a step with a keyword and for one without`() {
    project()
    val file = feature("features/a.yadda.feature", "Feature: A\n  Scenario: S\n    Given the moon is full\n    the sun is out\n")
    for (needle in listOf("Given the moon", "the sun")) {
      val data = context(stepIn(file, needle))
      val handlers = RenameHandlerRegistry.getInstance().getRenameHandlers(data)
      assertEquals(handlers.toString(), 1, handlers.size)
      assertTrue(handlers.toString(), handlers.single() is YaddaStepRenameHandler)
    }
  }

  /** Shift+F6 with the caret on [needle] in steps.ts, as the editor sends it: the handler the IDE picks, then answered. */
  private fun renameFromDefinition(needle: String, newText: String): Pair<Boolean, String?> {
    val file = myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("steps.ts")).let { myFixture.file }
    myFixture.editor.caretModel.moveToOffset(file.text.indexOf(needle))
    val data = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.PSI_FILE, file).add(CommonDataKeys.EDITOR, myFixture.editor).build()
    val handlers = RenameHandlerRegistry.getInstance().getRenameHandlers(data)
    val ours = handlers.singleOrNull() is YaddaStepRenameHandler
    if (!ours) return false to null
    YaddaStepRenameHandler.answerWith { newText }
    return try {
      handlers.single().invoke(project, myFixture.editor, file, data)
      true to null
    } catch (hint: CommonRefactoringUtil.RefactoringErrorHintException) {
      true to hint.message
    } finally {
      YaddaStepRenameHandler.answerWith(null)
    }
  }

  fun `test renaming from the definition's text renames it and its steps`() {
    project()
    feature("features/a.yadda.feature", "Feature: A\n  Scenario: S\n    Given the moon is full\n")
    feature("features/m.md", "# Feature: M\n\n## Scenario: S\n\n- Given the moon is full\n")
    assertEquals(true to null, renameFromDefinition("moon is full", "the moon is up"))
    assertTrue(text("steps.ts").contains("lib.given(\"the moon is up\", function () {});"))
    assertEquals("Feature: A\n  Scenario: S\n    Given the moon is up\n", text("features/a.yadda.feature"))
    assertEquals("# Feature: M\n\n## Scenario: S\n\n- Given the moon is up\n", text("features/m.md"))
  }

  fun `test renaming from the given of a regex definition keeps its group`() {
    project()
    feature("features/a.yadda.feature", "Feature: A\n  Scenario: S\n    Then the total is 80.00\n")
    assertEquals(true to null, renameFromDefinition("then(/^the total", "the sum is ([\\d.]+)"))
    assertTrue(text("steps.ts").contains("lib.then(/^the sum is ([\\d.]+)$/, function () {});"))
    assertEquals("Feature: A\n  Scenario: S\n    Then the sum is 80.00\n", text("features/a.yadda.feature"))
  }

  /** The data context of an editor whose caret is in a regex definition: the IDE passes the injected regular expression, not the file. */
  private fun injectedContext(needle: String): DataContext {
    myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("steps.ts"))
    myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf(needle))
    val injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myFixture.editor, myFixture.file)
    assertNotSame("the regex should be an injected fragment", myFixture.editor, injectedEditor)
    val injectedFile = PsiDocumentManager.getInstance(project).getPsiFile(injectedEditor.document)!!
    assertEquals("JSRegexp", injectedFile.language.id)
    return SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.PSI_FILE, injectedFile).add(CommonDataKeys.EDITOR, injectedEditor).build()
  }

  fun `test the caret inside a regex definition, which the IDE passes as an injected file, still renames it`() {
    project()
    feature("features/a.yadda.feature", "Feature: A\n  Scenario: S\n    Then the total is 80.00\n")
    val data = injectedContext("the total is")
    val handlers = RenameHandlerRegistry.getInstance().getRenameHandlers(data)
    assertEquals(handlers.toString(), 1, handlers.size)
    assertTrue(handlers.toString(), handlers.single() is YaddaStepRenameHandler)
    YaddaStepRenameHandler.answerWith { "the sum is ([\\d.]+)" }
    try {
      handlers.single().invoke(project, CommonDataKeys.EDITOR.getData(data), CommonDataKeys.PSI_FILE.getData(data), data)
    } finally {
      YaddaStepRenameHandler.answerWith(null)
    }
    assertTrue(text("steps.ts").contains("lib.then(/^the sum is ([\\d.]+)$/, function () {});"))
    assertEquals("Feature: A\n  Scenario: S\n    Then the sum is 80.00\n", text("features/a.yadda.feature"))
  }

  fun `test renaming a definition no step runs still renames the definition`() {
    project()
    assertEquals(true to null, renameFromDefinition("they leave", "they go"))
    assertTrue(text("steps.ts").contains("lib.when(\"they go\", function () {});"))
  }

  fun `test elsewhere in a step definition's file the IDE's own rename is kept`() {
    project()
    assertEquals(false, renameFromDefinition("export const lib", "x").first)
  }

  fun `test a definition in a project that doesn't use playwright-yadda is left to the IDE`() {
    myFixture.addFileToProject("steps.ts", definitions)
    assertEquals(false, renameFromDefinition("moon is full", "x").first)
  }

  fun `test a Markdown step follows the platform's rename of a definition without throwing`() {
    project()
    feature("features/m.md", "# Feature: M\n\n## Scenario: S\n\n- Given the moon is full\n")
    val literal = PsiTreeUtil.findChildrenOfType(myFixture.psiManager.findFile(myFixture.findFileInTempDir("steps.ts")), com.intellij.lang.javascript.psi.JSLiteralExpression::class.java).first()
    val reference = com.intellij.psi.search.searches.ReferencesSearch.search(literal).findAll().filterIsInstance<YaddaMarkdownStepReference>().single()
    // The platform's rename of a definition calls this on every reference to it; a Markdown file has no manipulator to do it with.
    assertSame(reference.element, reference.handleElementRename("the moon is up"))
    assertEquals("- Given the moon is full", text("features/m.md").lines().last { it.isNotBlank() })
  }

  fun `test the dialog starts from the definition's pattern`() {
    project()
    val file = feature("features/a.yadda.feature", "Feature: A\n  Scenario: S\n    Then the total is 80.00\n")
    val definition = LibraryScope.candidateDefinitions(stepIn(file, "the total")).definitions.single()
    val target = StepRenamer.targetOf(YaddaSteps.stepLiteralOf(definition)!!)
    val dialog = YaddaStepRenameDialog(project, target)
    try {
      assertEquals("the total is ([\\d.]+)", dialog.text)
    } finally {
      Disposer.dispose(dialog.disposable)
    }
  }

  fun `test the registry is the one this plugin provides`() {
    assertTrue(RenameHandlerRegistry.getInstance().javaClass.name, RenameHandlerRegistry.getInstance() is YaddaRenameHandlerRegistry)
  }

  fun `test a step of a plain Gherkin project keeps the Cucumber plugin's rename`() {
    myFixture.addFileToProject("steps.ts", definitions)
    val file = myFixture.addFileToProject("features/a.feature", "Feature: A\n  Scenario: S\n    Given the moon is full\n")
    val handlers = RenameHandlerRegistry.getInstance().getRenameHandlers(context(stepIn(file, "the moon")))
    assertFalse(handlers.toString(), handlers.any { it is YaddaStepRenameHandler })
  }
}
