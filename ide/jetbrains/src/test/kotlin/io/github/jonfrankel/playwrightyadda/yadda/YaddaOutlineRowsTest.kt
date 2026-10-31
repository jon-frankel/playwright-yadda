package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.jonfrankel.playwrightyadda.steps.YaddaStepGotoDeclarationHandler
import org.jetbrains.plugins.cucumber.inspections.CucumberStepInspection

/** Yadda runs an outline once per examples row, so the IDE reads a step for every row, not only the first. */
class YaddaOutlineRowsTest : BasePlatformTestCase() {
  private fun project() {
    myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "1" } }""")
    myFixture.addFileToProject(
      "steps.ts",
      """
      export const s = createStepLibrary();
      s.then(/^the total is ([\d.]+)$/, function () {});
      s.when(/^I pay with card$/, function () {});
      s.when(/^I pay with cash$/, function () {});
      """.trimIndent(),
    )
  }

  private fun feature(vararg rows: String, step: String = "Then the total is [total]", heading: String = "total") =
    "Feature: F\n  Scenario: S [$heading]\n    $step\n  Examples:\n    | $heading |\n" + rows.joinToString("") { "    | $it |\n" }

  private fun problems(name: String, text: String): List<String> {
    project()
    myFixture.enableInspections(CucumberStepInspection::class.java, YaddaExampleRowInspection::class.java)
    myFixture.configureByText(name, text)
    return myFixture.doHighlighting().filter { it.description != null && it.severity.myVal >= 200 }
      .map { "${it.description} :: ${myFixture.file.text.substring(it.startOffset, it.endOffset).trim()}" }
  }

  fun `test a step a later row fits is defined, and the first row that does not is named`() {
    assertEquals(
      listOf("No step definition matches \"the total is many\", which Yadda runs for example row 1 :: Then the total is [total]"),
      problems("a.feature", feature("many", "80.00")),
    )
  }

  fun `test every row fitting leaves nothing to report`() {
    assertEquals(emptyList<String>(), problems("a.feature", feature("80.00", "160.00")))
  }

  fun `test a step that no row fits is the plain undefined step, reported once`() {
    assertEquals(
      listOf("Undefined step reference: the total is [total] :: the total is [total]"),
      problems("a.feature", feature("many", "few")),
    )
  }

  fun `test the row that does not fit is named, and the rest are counted`() {
    assertEquals(
      listOf("No step definition matches \"the total is few\", which Yadda runs for example row 2 and 1 more :: Then the total is [total]"),
      problems("a.feature", feature("80.00", "few", "none")),
    )
  }

  fun `test a step with one row has nothing to compare`() {
    assertEquals(emptyList<String>(), problems("a.feature", feature("80.00")))
  }

  fun `test a plain Gherkin project is not checked`() {
    myFixture.addFileToProject("steps.ts", "export const s = createStepLibrary();\ns.then(/^the total is ([\\d.]+)\$/, function () {});")
    myFixture.enableInspections(YaddaExampleRowInspection::class.java)
    myFixture.configureByText("a.feature", feature("many", "80.00"))
    assertEquals(emptyList<String>(), myFixture.doHighlighting().filter { it.description?.startsWith("No step definition") == true }.map { it.description })
  }

  fun `test rows that run different definitions go to all of them`() {
    project()
    val file = myFixture.configureByText("a.feature", feature("card", "cash", step = "When I pay with [method]", heading = "method"))
    myFixture.editor.caretModel.moveToOffset(file.text.indexOf("I pay with") + 2)
    val targets = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)
      .map { it.text.lines().first().trim().removeSuffix(",").takeLast(24) }.sorted()
    assertEquals(2, targets.size)
    val handler = YaddaStepGotoDeclarationHandler().getGotoDeclarationTargets(myFixture.file.findElementAt(myFixture.caretOffset), myFixture.caretOffset, myFixture.editor)
    assertEquals(2, handler?.size)
  }

  fun `test a row inspection and the resolution agree on a markdown feature`() {
    project()
    myFixture.enableInspections(YaddaMarkdownUndefinedStepInspection::class.java, YaddaMarkdownExampleRowInspection::class.java)
    val file = markdown("many", "80.00")
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    assertEquals(
      listOf("No step definition matches \"the total is many\", which Yadda runs for example row 1 :: Then the total is [total]"),
      myFixture.doHighlighting().filter { it.description != null && it.severity.myVal >= 200 }
        .map { "${it.description} :: ${myFixture.file.text.substring(it.startOffset, it.endOffset).trim()}" },
    )
  }

  fun `test a markdown outline step finds the definitions of every row`() {
    project()
    val file = myFixture.addFileToProject(
      "features/pay.md",
      "# Feature: F\n## Scenario: S [method]\n- When I pay with [method]\n\n### Examples:\n\n| method |\n| ------ |\n| card |\n| cash |\n",
    )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.editor.caretModel.moveToOffset(file.text.indexOf("I pay with") + 2)
    val targets = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)
    assertEquals(2, targets.size)
  }

  private fun markdown(vararg rows: String): PsiFile =
    myFixture.addFileToProject(
      "features/total.md",
      "# Feature: F\n## Scenario: S [total]\n- Then the total is [total]\n\n### Examples:\n\n| total |\n| ----- |\n" + rows.joinToString("") { "| $it |\n" },
    )
}
