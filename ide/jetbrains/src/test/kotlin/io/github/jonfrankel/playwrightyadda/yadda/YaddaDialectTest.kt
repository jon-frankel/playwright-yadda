package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.cucumber.inspections.CucumberStepInspection
import org.jetbrains.plugins.cucumber.psi.GherkinFileType
import org.jetbrains.plugins.cucumber.psi.GherkinLanguage
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinPystring
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import com.intellij.openapi.command.WriteCommandAction

class YaddaDialectTest : BasePlatformTestCase() {
  private val fenced = """
    Feature: F
      Scenario: S
        Then the transactions start with:
        ---
        | payee | debit |
        | Ikea  | 100.00 |
        ---
        And the cart is empty
  """.trimIndent()

  private fun steps() {
    myFixture.addFileToProject(
      "steps.ts",
      """
      export const s = createStepLibrary();
      s.then(/^the transactions start with:(?:\n([\s\S]+))?$/, function () {});
      s.then("the cart is empty", function () {});
      """.trimIndent(),
    )
  }

  private fun yadda(text: String = fenced): PsiFile = myFixture.configureByText("rules.yadda.feature", text)

  private fun problems(): List<String> {
    myFixture.enableInspections(CucumberStepInspection::class.java)
    return myFixture.doHighlighting().filter { it.description != null && it.severity.myVal >= 300 }
      .map { "${it.description} :: '${myFixture.file.text.substring(it.startOffset, it.endOffset)}'" }
  }

  fun `test a name ending in yadda dot feature is a Yadda file, and a plain feature stays Gherkin`() {
    assertEquals(YaddaFileType, yadda().fileType)
    assertEquals(GherkinFileType.INSTANCE, myFixture.configureByText("rules.feature", fenced).fileType)
  }

  fun `test the rows between fences are the table of the step above`() {
    val table = PsiTreeUtil.findChildOfType(yadda(), GherkinTable::class.java)
    assertNotNull("no GherkinTable in the tree", table)
    assertEquals("the transactions start with:", (table!!.parent as GherkinStep).name)
    assertEquals(1, table.dataRows.size)
  }

  fun `test fences are not errors, steps resolve, and the step after the table still parses`() {
    steps()
    yadda()
    assertEquals(emptyList<String>(), problems())
    assertEquals(2, PsiTreeUtil.findChildrenOfType(myFixture.file, GherkinStep::class.java).size)
  }

  fun `test the same text in a plain feature file still reports the fences`() {
    steps()
    myFixture.configureByText("rules.feature", fenced)
    assertTrue(problems().any { "Unexpected element" in it })
  }

  fun `test reformatting aligns the columns of a fenced table`() {
    val file = yadda()
    WriteCommandAction.runWriteCommandAction(project) {
      CodeStyleManager.getInstance(project).reformat(file)
    }
    assertTrue(file.text, "| payee | debit  |" in file.text)
  }

  private val blocks = """
    Feature: F
      Scenario: S
        Given a payload
        ---
        {"a": 1,
         "b": [1, 2]}
        ---
        When the rows
        ---
        id,name
        1,Ann
        ---
        Then a table
        ---
        | a | b |
        | 1 | 2 |
        ---
        And the last step has no closing fence
        ---
        plain text
  """.trimIndent()

  private fun errors(file: PsiFile): List<String> {
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    return myFixture.doHighlighting().filter { it.description != null && it.severity.myVal >= 400 }
      .map { "${it.description} :: '${myFixture.file.text.substring(it.startOffset, it.endOffset)}'" }
  }

  fun `test any fenced content is a doc string of its step, and tables stay tables`() {
    val file = yadda(blocks)
    val steps = PsiTreeUtil.findChildrenOfType(file, GherkinStep::class.java).toList()
    assertEquals(4, steps.size)

    val strings = steps.map { step -> PsiTreeUtil.getChildOfType(step, GherkinPystring::class.java)?.text }
    assertTrue(strings[0]!!, "\"b\": [1, 2]" in strings[0]!!)
    assertTrue(strings[1]!!, "1,Ann" in strings[1]!!)
    assertNull(strings[2])
    assertNotNull(PsiTreeUtil.getChildOfType(steps[2], GherkinTable::class.java))
    assertTrue(strings[3]!!, "plain text" in strings[3]!!)
    assertEquals(emptyList<String>(), errors(file))
  }

  fun `test a plain feature file stays Gherkin unless the project depends on playwright-yadda`() {
    myFixture.addFileToProject("package.json", """{ "name": "app", "dependencies": { "playwright": "1" } }""")
    val plain = myFixture.addFileToProject("features/a.feature", blocks)
    assertEquals(GherkinLanguage.INSTANCE, plain.language)
    assertTrue(errors(plain).isNotEmpty())
  }

  fun `test a plain feature file is Yadda when the project depends on playwright-yadda`() {
    myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "^0.0.10" } }""")
    val file = myFixture.addFileToProject("features/a.feature", blocks)
    assertEquals(YaddaLanguage, file.language)
    assertEquals(GherkinFileType.INSTANCE, file.fileType)
    assertEquals(emptyList<String>(), errors(file))
  }

  fun `test a package above the feature's own package counts`() {
    myFixture.addFileToProject("package.json", """{ "dependencies": { "playwright-yadda": "1" } }""")
    myFixture.addFileToProject("apps/web/package.json", """{ "name": "web" }""")
    val file = myFixture.addFileToProject("apps/web/features/a.feature", blocks)
    assertEquals(YaddaLanguage, file.language)
  }

  fun `test being named in a script or as the package itself is not depending on it`() {
    myFixture.addFileToProject("package.json", """{ "name": "playwright-yadda", "scripts": { "x": "pnpm add playwright-yadda" } }""")
    assertEquals(GherkinLanguage.INSTANCE, myFixture.addFileToProject("features/a.feature", blocks).language)
  }

  fun `test a package that is not valid JSON is not a dependency`() {
    myFixture.addFileToProject("package.json", "{ not json")
    assertEquals(GherkinLanguage.INSTANCE, myFixture.addFileToProject("features/a.feature", blocks).language)
  }

  /** The real `examples/basic` project, as the IDE sees it: package.json, steps and features. */
  private fun copyBasicExample(): Map<String, PsiFile> {
    val root = java.io.File("../../examples/basic")
    assertTrue("missing ${root.absolutePath}", root.isDirectory)
    val files = listOf("package.json") +
      listOf("steps", "features").flatMap { dir ->
        java.io.File(root, dir).listFiles { f -> f.isFile }!!.map { "$dir/${it.name}" }
      }
    return files.associateWith { myFixture.addFileToProject(it, java.io.File(root, it).readText()) }
  }

  fun `test examples-basic's orders feature parses as Yadda with every step resolved`() {
    val orders = copyBasicExample()["features/orders.feature"]!!
    assertEquals(YaddaLanguage, orders.language)

    myFixture.enableInspections(CucumberStepInspection::class.java)
    myFixture.configureFromExistingVirtualFile(orders.virtualFile)
    val problems = myFixture.doHighlighting().filter { it.description != null && it.severity.myVal >= 300 }
      .map { "${it.description} :: '${orders.text.substring(it.startOffset, it.endOffset)}'" }
    assertEquals(emptyList<String>(), problems)

    val steps = PsiTreeUtil.findChildrenOfType(orders, GherkinStep::class.java)
    assertEquals("the csv catalogue, the json order and the prose rejection", 4, steps.count { PsiTreeUtil.getChildOfType(it, GherkinPystring::class.java) != null })
    assertEquals("the cart and receipt tables, the cart table of the outline", 3, steps.count { PsiTreeUtil.getChildOfType(it, GherkinTable::class.java) != null })
  }
}
