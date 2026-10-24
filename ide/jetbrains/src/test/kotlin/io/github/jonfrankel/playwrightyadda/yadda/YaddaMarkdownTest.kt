package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.aqua.runners.playwright.js.PlaywrightRunConfiguration
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.actionSystem.impl.rearrangeByPromotersImpl
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.keymap.KeymapManager
import java.awt.event.KeyEvent
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.execution.ParametersListUtil
import io.github.jonfrankel.playwrightyadda.run.PlaywrightGrep
import io.github.jonfrankel.playwrightyadda.run.YaddaMarkdownRunLineMarkerContributor

/** Yadda's Markdown features: a `.md` file the IDE keeps as Markdown and the plugin reads as a feature. */
class YaddaMarkdownTest : BasePlatformTestCase() {
  private val feature = """
    @Libraries=storefront

    # Feature: Storefront

    ## Scenario: Remove

    - When they remove the item
  """.trimIndent()

  private fun project(depends: Boolean = true) {
    if (depends) myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "1" } }""")
    myFixture.addFileToProject("common.ts", "export const commonSteps = createStepLibrary();")
    myFixture.addFileToProject(
      "storefront.ts",
      """
      export const storefrontSteps = createStepLibrary();
      storefrontSteps.when("they remove the item", function () {});
      storefrontSteps.given(/^the cart holds:\n([\s\S]+)$/, function (block) {});
      storefrontSteps.then(/^the total is ([\d.]+)$/, function (total) {});
      """.trimIndent(),
    )
    myFixture.addFileToProject(
      "admin.ts",
      "export const adminSteps = createStepLibrary();\nadminSteps.when(\"they remove the item\", function () {});",
    )
    myFixture.addFileToProject(
      "registry.ts",
      """
      import { createLibraryRegistry } from "playwright-yadda";
      import { commonSteps } from "./common.js";
      import { storefrontSteps } from "./storefront.js";
      import { adminSteps } from "./admin.js";
      export const registry = createLibraryRegistry({
        common: commonSteps,
        storefront: storefrontSteps,
        admin: adminSteps,
      });
      """.trimIndent(),
    )
  }

  private fun markdown(text: String = feature, path: String = "features/store.md"): PsiFile = myFixture.addFileToProject(path, text)

  private fun open(file: PsiFile, needle: String, shift: Int = 0) {
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.editor.caretModel.moveToOffset(file.text.indexOf(needle) + shift)
  }

  private fun goesTo(): List<String> =
    GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)
      .map { it.containingFile.name + ": " + it.text.lines().first().trim().removeSuffix(",") }.sorted()

  private fun problems(file: PsiFile): List<String> {
    myFixture.enableInspections(
      YaddaMarkdownParseInspection::class.java,
      YaddaMarkdownRuleInspection::class.java,
      YaddaMarkdownUnusedColumnInspection::class.java,
      YaddaMarkdownUndefinedStepInspection::class.java,
    )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    return myFixture.doHighlighting()
      .filter { it.description != null && it.severity.myVal >= 200 }
      .map { "${it.description} :: ${myFixture.file.text.substring(it.startOffset, it.endOffset).trim()}" }
      .sorted()
  }

  // Which files are features.

  fun `test a md file with a Feature heading in a project that runs playwright-yadda is a feature, and stays Markdown`() {
    project()
    val file = markdown()
    assertTrue(YaddaMarkdown.isFeature(file))
    assertEquals("Markdown", file.language.id)
  }

  fun `test a README, a file outside such a project and a Feature inside a fence are not features`() {
    project()
    assertFalse(YaddaMarkdown.isFeature(markdown("# Project\n\n- Given a thing\n\nSome words.\n", "README.md")))
    assertFalse(YaddaMarkdown.isFeature(markdown("# Docs\n\n```\n# Feature: F\n## Scenario: S\n- Given a\n```\n", "docs/syntax.md")))
    assertFalse(YaddaMarkdown.isFeature(markdown("<!--\n# Feature: F\n-->\n", "docs/hidden.md")))
    assertFalse(YaddaMarkdown.isFeature(markdown("plain\n", "plain.md")))
  }

  fun `test without a dependency on playwright-yadda only a yadda md file is a feature`() {
    project(depends = false)
    assertFalse(YaddaMarkdown.isFeature(markdown()))
    assertTrue(YaddaMarkdown.isFeature(markdown(feature, "features/store.yadda.md")))
  }

  fun `test a gherkin file is no markdown feature`() {
    project()
    assertFalse(YaddaMarkdown.isFeature(myFixture.addFileToProject("features/a.feature", "Feature: F\n  Scenario: S\n    Given a\n")))
  }

  // Navigation.

  fun `test Cmd+B on a step goes to the definition its libraries enable`() {
    project()
    open(markdown(), "they remove", 2)
    assertEquals(listOf("storefront.ts: \"they remove the item\""), goesTo())
  }

  fun `test Cmd+B works from the bullet and from the end of the line`() {
    project()
    val file = markdown(feature + "\n")
    open(file, "- When")
    assertEquals(listOf("storefront.ts: \"they remove the item\""), goesTo())
    open(file, "the item\n", 8)
    assertEquals(listOf("storefront.ts: \"they remove the item\""), goesTo())
  }

  fun `test Cmd+B on a step with no libraries tag offers every definition, each in its file`() {
    project()
    open(markdown(feature.removePrefix("@Libraries=storefront\n\n")), "they remove", 2)
    assertEquals(listOf("admin.ts: \"they remove the item\"", "storefront.ts: \"they remove the item\""), goesTo())
  }

  fun `test Cmd+B on a heading, a description or a comment goes nowhere`() {
    project()
    val file = markdown(feature.replace("# Feature: Storefront", "# Feature: Storefront\n\nthey remove the item, said in passing.\n\n<!-- they remove the item -->"))
    open(file, "Scenario: Remove", 3)
    assertEquals(emptyList<String>(), goesTo())
    open(file, "said in passing", 2)
    assertEquals(emptyList<String>(), goesTo())
    open(file, "<!-- they remove", 8)
    assertEquals(emptyList<String>(), goesTo())
  }

  fun `test a step with a table resolves to a definition that requires the block`() {
    project()
    val file = markdown(
      """
      @Libraries=storefront
      # Feature: F
      ## Scenario: S
      - Given the cart holds:

        | sku  | qty |
        | ---- | --- |
        | KB-1 | 1   |

      - Then the total is 80.00
      """.trimIndent(),
    )
    open(file, "the cart holds", 2)
    assertEquals(listOf("storefront.ts: /^the cart holds:\\n([\\s\\S]+)\$/"), goesTo())
    open(file, "the total is", 2)
    assertEquals(listOf("storefront.ts: /^the total is ([\\d.]+)\$/"), goesTo())
  }

  fun `test an outline step is read with the first row of its examples filled in`() {
    project()
    val file = markdown(
      """
      @Libraries=storefront
      # Feature: F
      ## Scenario: S [total]
      - Then the total is [total]

      ### Examples:

      | total |
      | ----- |
      | 80.00 |
      | 90.00 |
      """.trimIndent(),
    )
    open(file, "the total is", 2)
    assertEquals(listOf("storefront.ts: /^the total is ([\\d.]+)\$/"), goesTo())
  }

  fun `test usages of a definition are the steps of Markdown scenarios that enable its library`() {
    project()
    markdown()
    markdown(feature.replace("@Libraries=storefront", "@Libraries=admin"), "features/admin.md")
    myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("storefront.ts")!!)
    myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("they remove") + 2)
    val usages = myFixture.findUsages(myFixture.elementAtCaret)
    assertEquals(listOf("store.md"), usages.map { it.file!!.name })
    val usage = usages.single()
    // The step is its whole line, keyword included.
    assertEquals("When they remove the item", usage.file!!.text.substring(usage.navigationRange.startOffset, usage.navigationRange.endOffset))
  }

  fun `test usages find a step with a table as well`() {
    project()
    markdown("@Libraries=storefront\n# Feature: F\n## Scenario: S\n- Given the cart holds:\n\n  | a |\n  | 1 |\n")
    myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("storefront.ts")!!)
    myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("the cart holds") + 2)
    assertEquals(listOf("store.md"), myFixture.findUsages(myFixture.elementAtCaret).map { it.file!!.name })
  }

  fun `test Cmd+B on a library name goes to its registry property, and usages of the property list the Markdown tag`() {
    project()
    val file = markdown()
    open(file, "storefront", 2)
    assertEquals(listOf("registry.ts: storefront: storefrontSteps"), goesTo())

    markdown("# Feature: F\n\n```\n@Libraries=storefront\n```\n\n## Scenario: S\n- When a\n", "features/fenced.md")
    myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("registry.ts")!!)
    myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("storefront:") + 2)
    assertEquals(listOf("store.md"), myFixture.findUsages(myFixture.elementAtCaret).map { it.file!!.name })
  }

  // What the IDE says about a feature.

  fun `test a feature that resolves and parses has no problems`() {
    project()
    assertEquals(emptyList<String>(), problems(markdown()))
  }

  fun `test a step nothing defines is undefined, and so is prose in a scenario`() {
    project()
    val file = markdown(feature + "\n- Then the moon is full\n\nA stray paragraph.\n")
    assertEquals(
      listOf("Undefined step :: A stray paragraph.", "Undefined step :: Then the moon is full"),
      problems(file),
    )
  }

  fun `test what Yadda would throw about is an error on its line`() {
    project()
    val file = markdown(
      "# Feature: F\n## Scenario: S [n]\n- When they remove the item\n### Examples:\n| n |\n| - |\n| 1 | 2 |\n",
    )
    assertEquals(
      listOf("Yadda can't parse this: Incorrect number of fields in example table. Expected 1 but found 2 :: | 1 | 2 |"),
      problems(file),
    )
  }

  fun `test a rule is a warning`() {
    project()
    val file = markdown("# Feature: F\n## Rule: R\n### Scenario: S\n- When they remove the item\n")
    assertEquals(listOf("playwright-yadda doesn't run the scenarios under a Rule :: ## Rule: R"), problems(file))
  }

  fun `test an examples column nothing uses is greyed, and one the title or a step uses is not`() {
    project()
    val file = markdown(
      "# Feature: F\n## Scenario: S [a]\n- Then the total is [b]\n\n### Examples:\n\n| a | b | c |\n| - | - | - |\n| 1 | 2 | 3 |\n",
    )
    assertEquals(listOf("Nothing in the scenario uses this column :: c"), problems(file).filter { "column" in it })
  }

  fun `test none of it applies to an ordinary Markdown file`() {
    project()
    val file = markdown("# Project\n\n- Given a thing\n\n## Scenario: not a feature\n\nSee the docs.\n", "README.md")
    assertEquals(emptyList<String>(), problems(file))
  }

  // Running.

  private fun specProject() {
    myFixture.addFileToProject("package.json", """{ "devDependencies": { "playwright-yadda": "1" } }""")
    myFixture.addFileToProject(
      "e2e/features.spec.ts",
      """
      import { test } from "@playwright/test";
      import { defineFeatureSuite } from "playwright-yadda";
      defineFeatureSuite({ test, registry, features: "../features" });
      """.trimIndent(),
    )
  }

  private fun grepFor(file: PsiFile, needle: String, shift: Int = 0): String? {
    val element = file.findElementAt(file.text.indexOf(needle) + shift)!!
    val run = ConfigurationContext(element).configurationsFromContext.orEmpty()
      .map { it.configuration }
      .filterIsInstance<PlaywrightRunConfiguration>()
      .firstOrNull { "--grep" in ParametersListUtil.parse(it.settings.cliOptions) }
      ?: return null
    return ParametersListUtil.parse(run.settings.cliOptions).let { it[it.indexOf("--grep") + 1] }
  }

  private val runnable = """
    # Feature: Storefront

    ## Background:

    - Given a

    ## Scenario: Remove

    - When they remove the item

    ## Scenario Outline: Remove [n]

    - When they remove [n]

    ### Examples:

    | n |
    | - |
    | 1 |

    ## Rule: R

    ### Scenario: Never run

    - When a
  """.trimIndent()

  fun `test the run icon runs a Markdown scenario, an outline or the whole feature through the spec`() {
    specProject()
    val file = markdown(runnable)
    assertEquals(PlaywrightGrep.forScenario("Storefront", "Remove"), grepFor(file, "Scenario: Remove", 2))
    assertEquals(PlaywrightGrep.forScenario("Storefront", "Remove"), grepFor(file, "they remove the item", 2))
    assertEquals(PlaywrightGrep.forScenarioOutline("Storefront", "Remove [n]"), grepFor(file, "Scenario Outline"))
    assertEquals(PlaywrightGrep.forScenarioOutline("Storefront", "Remove [n]"), grepFor(file, "| 1 |", 2))
    assertEquals(PlaywrightGrep.forFeature("Storefront"), grepFor(file, "Feature: Storefront"))
  }

  fun `test a background, a rule and its scenarios have nothing to run`() {
    specProject()
    val file = markdown(runnable)
    assertNull(grepFor(file, "Background:"))
    assertNull(grepFor(file, "Rule: R"))
    assertNull(grepFor(file, "Never run"))
  }

  fun `test only the Feature and Scenario headings get a run icon, one each`() {
    specProject()
    val file = markdown(runnable)
    val contributor = YaddaMarkdownRunLineMarkerContributor()
    val marked = mutableListOf<String>()
    file.accept(object : PsiRecursiveElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element.firstChild == null && contributor.getInfo(element) != null) {
          marked += file.text.substring(element.textRange.startOffset).lineSequence().first().trim()
        }
        super.visitElement(element)
      }
    })
    // Nothing under the Rule runs, so its scenario has no icon.
    assertEquals(listOf("Feature: Storefront", "Scenario: Remove", "Scenario Outline: Remove [n]"), marked)
  }

  fun `test the icon is in the editor's gutter`() {
    specProject()
    val file = markdown(runnable)
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.doHighlighting()
    assertEquals(3, myFixture.findAllGutters().count { it.tooltipText == "Run" })
  }

  fun `test no icon in an ordinary Markdown file`() {
    specProject()
    val file = markdown("# Project\n\n## Scenario: x\n", "README.md")
    val contributor = YaddaMarkdownRunLineMarkerContributor()
    assertNull(contributor.getInfo(file.findElementAt(file.text.indexOf("Scenario"))!!))
  }

  // The key itself. The Markdown plugin binds Cmd+B to Bold, and the handlers above are only reached
  // when Go to Declaration is the action the key runs.

  private val bold = "org.intellij.plugins.markdown.ui.actions.styling.ToggleBoldAction"

  private fun runsOnCmdB(needle: String, shift: Int = 0, select: Boolean = false): List<String> {
    val file = myFixture.findFileInTempDir("features/store.md")?.let { PsiManager.getInstance(project).findFile(it) } ?: markdown()
    open(file, needle, shift)
    if (select) myFixture.editor.selectionModel.setSelection(myFixture.caretOffset, myFixture.caretOffset + 4)
    val actions = ActionManager.getInstance()
    val candidates = listOf(bold, "GotoDeclaration").mapNotNull { actions.getAction(it) }
    // What the key dispatcher passes promoters: the UI-only snapshot, which has no PSI_FILE.
    val context = Utils.getUiOnlyDataContext((myFixture.editor as EditorEx).dataContext)
    val promoter = YaddaMarkdownActionPromoter()
    val suppressed = promoter.suppress(candidates, context)
    return (promoter.promote(candidates, context) + candidates.filter { it !in suppressed }).distinct().mapNotNull { actions.getId(it) }
  }

  fun `test the Markdown plugin gives Bold the Cmd+B key, which is why the key needs a promoter`() {
    val shortcuts = KeymapManager.getInstance().activeKeymap.getShortcuts(bold).filterIsInstance<KeyboardShortcut>()
    assertTrue(shortcuts.any { it.firstKeyStroke.keyCode == KeyEvent.VK_B })
  }

  fun `test the promoter is registered`() {
    assertTrue(ActionPromoter.EP_NAME.extensionList.any { it is YaddaMarkdownActionPromoter })
  }

  /** The first action the key dispatcher would run: the platform's own ordering, with every registered promoter. */
  private fun firstOnCmdB(needle: String, shift: Int, bothWays: Boolean = true, select: Boolean = false): List<String?> {
    val file = myFixture.findFileInTempDir("features/store.md")?.let { PsiManager.getInstance(project).findFile(it) } ?: markdown()
    open(file, needle, shift)
    if (select) myFixture.editor.selectionModel.setSelection(myFixture.caretOffset, myFixture.caretOffset + 4)
    val actions = ActionManager.getInstance()
    val bold = actions.getAction(bold)!!
    val declaration = actions.getAction("GotoDeclaration")!!
    val context = Utils.getUiOnlyDataContext((myFixture.editor as EditorEx).dataContext)
    val orders = if (bothWays) listOf(listOf(bold, declaration), listOf(declaration, bold)) else listOf(listOf(bold, declaration))
    return orders.map { rearrangeByPromotersImpl(it, context, ActionPromoter.EP_NAME.extensionList).firstOrNull()?.let(actions::getId) }
  }

  fun `test through the platform's own ordering, Go to Declaration comes first on a step whatever order the keymap lists them`() {
    project()
    assertEquals(listOf("GotoDeclaration", "GotoDeclaration"), firstOnCmdB("they remove", 2))
    assertEquals(listOf("GotoDeclaration", "GotoDeclaration"), firstOnCmdB("storefront", 2))
  }

  fun `test through the platform's own ordering, Bold stays first elsewhere and with a selection`() {
    project()
    assertEquals(listOf(bold, bold), firstOnCmdB("Scenario: Remove", 3))
    assertEquals(listOf(bold), firstOnCmdB("they remove", 2, bothWays = false, select = true))
  }

  fun `test on a step Cmd+B runs Go to Declaration and not Bold`() {
    project()
    assertEquals(listOf("GotoDeclaration"), runsOnCmdB("they remove", 2))
  }

  fun `test on the bullet and on the keyword of a step it is the same`() {
    project()
    assertEquals(listOf("GotoDeclaration"), runsOnCmdB("- When"))
    assertEquals(listOf("GotoDeclaration"), runsOnCmdB("When they", 1))
  }

  fun `test on a library name Cmd+B goes to declaration`() {
    project()
    assertEquals(listOf("GotoDeclaration"), runsOnCmdB("storefront", 2))
  }

  fun `test elsewhere in the file, and with a selection, nothing is set aside and Cmd+B stays Bold`() {
    project()
    assertTrue(bold in runsOnCmdB("Scenario: Remove", 3))
    assertTrue(bold in runsOnCmdB("they remove", 2, select = true))
  }

  fun `test in an ordinary Markdown file Cmd+B stays Bold`() {
    project()
    val file = markdown("# Project\n\n- When they remove the item\n", "README.md")
    open(file, "they remove", 2)
    val actions = ActionManager.getInstance()
    val candidates = listOf(bold, "GotoDeclaration").mapNotNull { actions.getAction(it) }
    val context = (myFixture.editor as EditorEx).dataContext
    assertEquals(emptyList<AnAction>(), YaddaMarkdownActionPromoter().suppress(candidates, context))
  }
}
