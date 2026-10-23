package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.cucumber.inspections.CucumberStepInspection
import org.jetbrains.plugins.cucumber.inspections.CucumberTableInspection
import org.jetbrains.plugins.cucumber.inspections.GherkinScenarioToScenarioOutlineInspection
import java.io.File

/**
 * `examples/basic/ide-playground` is for trying the plugin by hand, and each file says what the IDE
 * should show. This opens them with the example's real step definitions and checks that it does.
 */
class YaddaPlaygroundTest : BasePlatformTestCase() {
  private val example = File("../../examples/basic")

  private fun open(name: String) {
    myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "1" } }""")
    for (step in File(example, "steps").walkTopDown().filter { it.isFile && it.extension == "ts" }) {
      myFixture.addFileToProject("steps/" + step.relativeTo(File(example, "steps")).path, step.readText())
    }
    myFixture.enableInspections(
      YaddaParseInspection::class.java,
      YaddaRuleInspection::class.java,
      YaddaUnusedColumnInspection::class.java,
      CucumberStepInspection::class.java,
      CucumberTableInspection::class.java,
      GherkinScenarioToScenarioOutlineInspection::class.java,
    )
    myFixture.configureByText(name, File(example, "ide-playground/$name").readText())
  }

  private fun problems() = myFixture.doHighlighting()
    .filter { it.description != null && it.severity.myVal >= 200 }
    .map { "${it.description} :: ${myFixture.file.text.substring(it.startOffset, it.endOffset).trim()}" }
    .sorted()

  fun `test the orders feature that runs is clean with its mandatory-block definitions and strict regex`() {
    myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "1" } }""")
    for (step in File(example, "steps").walkTopDown().filter { it.isFile && it.extension == "ts" }) {
      myFixture.addFileToProject("steps/" + step.relativeTo(File(example, "steps")).path, step.readText())
    }
    myFixture.enableInspections(YaddaParseInspection::class.java, YaddaUnusedColumnInspection::class.java, CucumberStepInspection::class.java)
    myFixture.configureByText("orders.feature", File(example, "features/orders.feature").readText())
    assertEquals(emptyList<String>(), problems())
  }

  fun `test unused-column greys price and only price`() {
    open("unused-column.feature")
    assertEquals(listOf("Nothing in the scenario uses this column :: price"), problems().filter { "column" in it })
  }

  fun `test rule warns on its line`() {
    open("rule.feature")
    assertEquals(listOf("playwright-yadda doesn't run the scenarios under a Rule :: Rule: Large carts get a discount"), problems().filter { "Rule" in it })
  }

  fun `test empty scenario is an error on its heading`() {
    open("empty-scenario.feature")
    assertEquals(listOf("Yadda can't parse this: Scenario requires one or more steps :: Scenario: Nothing yet"), problems().filter { "Yadda" in it })
  }

  fun `test two tags on a line is an error on that line`() {
    open("two-tags.feature")
    assertEquals(listOf("Yadda can't parse this: Invalid annotation: a @b :: @a @b"), problems().filter { "Yadda" in it })
  }

  fun `test a short example row is an error on that row`() {
    open("row-width.feature")
    assertEquals(
      listOf("Yadda can't parse this: Incorrect number of fields in example table. Expected 4 but found 3 :: | 1 |"),
      problems().filter { "Yadda" in it },
    )
  }

  fun `test block steps resolve except the two undefined on purpose`() {
    open("block-steps.feature")
    assertEquals(
      listOf(
        "Undefined step reference: nobody has written this step :: nobody has written this step",
        "Undefined step reference: the cart holds: :: the cart holds:",
      ),
      problems().filter { "step reference" in it },
    )
  }

  fun `test the typing file has nothing to report`() {
    open("typing.feature")
    assertEquals(emptyList<String>(), problems().filter { "Yadda" in it || "Unused" in it || "Examples" in it })
  }

  fun `test Gherkin's table and outline checks stay quiet on the unused-column file`() {
    open("unused-column.feature")
    assertTrue(problems().none { "Unused table column" in it || "Scenario with Examples" in it })
  }
}
