package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinPystring
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FencedBlockTest : junit.framework.TestCase() {
  private fun content(text: String): String? = FencedBlock.of(text)?.let { text.substring(it.content.startOffset, it.content.endOffset) }

  fun `test the remark is whatever follows the dashes`() {
    assertEquals("json", FencedBlock.of("--- json\n{}\n---")!!.remark)
    assertEquals("json", FencedBlock.of("---json\n{}\n---")!!.remark)
    assertEquals("the response body", FencedBlock.of("-----  the response body \n x\n-----")!!.remark)
    assertEquals("", FencedBlock.of("---\nx\n---")!!.remark)
    assertEquals("sql", FencedBlock.of("|--- sql\nx\n|---")!!.remark)
  }

  fun `test the content is the lines between the fences`() {
    assertEquals("{}\n", content("--- json\n{}\n---"))
    assertEquals("  a\n  b\n", content("--- json\n  a\n  b\n  ---"))
    assertEquals("", content("---\n---"))
  }

  fun `test an unclosed block runs to the end and a lone fence has no content`() {
    assertEquals("a\nb", content("---\na\nb"))
    assertEquals("", content("---"))
    assertTrue(FencedBlock.of("---")!!.content.isEmpty)
  }

  fun `test text that does not open with a fence is not a block`() {
    assertNull(FencedBlock.of("\"\"\"\nx\n\"\"\""))
  }
}

class YaddaInjectionTest : BasePlatformTestCase() {
  private fun yadda(blocks: String): PsiFile = myFixture.configureByText(
    "a.yadda.feature",
    "Feature: F\n  Scenario: S\n    Given a step\n$blocks".trimIndent(),
  )

  private fun host(file: PsiFile = myFixture.file): GherkinPystring =
    PsiTreeUtil.findChildOfType(file, GherkinPystring::class.java)!!

  /** The languages and texts injected into the file's first block. */
  private fun injected(file: PsiFile = myFixture.file): List<String> =
    InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host(file))
      .orEmpty().map { (it.first as PsiFile).let { f -> "${f.language.id}:${f.text}" } }

  fun `test a remark that names a language injects it into the content`() {
    yadda("      --- json\n      {\"a\": 1}\n      ---")
    assertEquals(listOf("JSON:      {\"a\": 1}\n"), injected())
  }

  fun `test the dashes can be any length and the remark can touch them`() {
    yadda("      ----json\n      [1]\n      ----")
    assertEquals(listOf("JSON:      [1]\n"), injected())
  }

  fun `test a remark touching three dashes is injected once, though Gherkin's own injector reads it too`() {
    yadda("      ---json\n      [1]\n      ---")
    assertEquals(listOf("JSON"), injected().map { it.substringBefore(':') })
  }

  fun `test an unclosed block injects through the end`() {
    yadda("      --- json\n      [1]")
    assertEquals(listOf("JSON:      [1]"), injected())
  }

  fun `test the language is matched whatever its case`() {
    yadda("      --- JSON\n      [1]\n      ---")
    assertEquals(1, injected().size)
  }

  fun `test a block with no remark, or a remark that is not a language, injects nothing`() {
    yadda("      ---\n      [1]\n      ---")
    assertEquals(emptyList<String>(), injected())
    yadda("      --- the response body\n      [1]\n      ---")
    assertEquals(emptyList<String>(), injected())
  }

  fun `test a Gherkin doc string in a Yadda file is left to Gherkin`() {
    yadda("      \"\"\"\n      [1]\n      \"\"\"")
    assertEquals(emptyList<String>(), injected())
  }

  fun `test a mistake in the injected JSON is reported in the feature`() {
    yadda("      --- json\n      {\"a\": }\n      ---")
    val errors = myFixture.doHighlighting().filter { it.description != null && it.severity.myVal >= 400 }
    assertTrue("no error for the JSON", errors.isNotEmpty())
    assertTrue(errors.joinToString { it.description }, errors.all { it.startOffset > myFixture.file.text.indexOf("{") - 1 })
  }

  fun `test valid injected JSON is clean`() {
    yadda("      --- json\n      {\n        \"a\": [1, 2]\n      }\n      ---\n    And more")
    assertEquals(emptyList<String>(), myFixture.doHighlighting().filter { it.description != null && it.severity.myVal >= 300 }.map { it.description })
  }

  private fun orders(): PsiFile {
    val text = java.io.File("../../examples/basic/features/orders.feature").readText()
    return myFixture.configureByText("orders.yadda.feature", text)
  }

  fun `test examples-basic's orders feature injects JSON into its order blocks and is clean`() {
    myFixture.enableInspections(YaddaCsvBlockInspection::class.java)
    val file = orders()
    val manager = InjectedLanguageManager.getInstance(project)
    val languages = PsiTreeUtil.findChildrenOfType(file, GherkinPystring::class.java)
      .flatMap { host -> manager.getInjectedPsiFiles(host).orEmpty().map { (it.first as PsiFile).language.id } }
    assertEquals(listOf("JSON", "JSON"), languages)
    assertEquals(
      emptyList<String>(),
      myFixture.doHighlighting().filter { it.description != null && it.severity.myVal >= 300 }.map { it.description },
    )
  }

  fun `test reformatting leaves the JSON in a block as written`() {
    val file = orders()
    val jsonBefore = jsonBlocks(file.text)
    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
      com.intellij.psi.codeStyle.CodeStyleManager.getInstance(project).reformat(file)
    }
    assertEquals(jsonBefore, jsonBlocks(file.text))
    assertEquals(2, jsonBefore.size)
  }

  private fun jsonBlocks(text: String): List<String> =
    Regex("""--- json\n(.*?)\n\s*---""", RegexOption.DOT_MATCHES_ALL).findAll(text).map { it.groupValues[1] }.toList()
}

class YaddaBlockEditingTest : BasePlatformTestCase() {
  private val head = "Feature: F\n  Scenario: S\n    Given a step\n"

  private fun host(): GherkinPystring = PsiTreeUtil.findChildOfType(myFixture.file, GherkinPystring::class.java)!!

  fun `test Enter after an opening fence adds a matching closing fence, not quote marks`() {
    myFixture.configureByText("a.yadda.feature", head + "      --- json<caret>")
    myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER)
    assertEquals(head + "      --- json\n      \n      ---", myFixture.editor.document.text)
    assertEquals(head.length + "      --- json\n      ".length, myFixture.caretOffset)
  }

  fun `test the closing fence copies the opening dashes`() {
    myFixture.configureByText("a.yadda.feature", head + "    -----<caret>")
    myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER)
    assertEquals(head + "    -----\n    \n    -----", myFixture.editor.document.text)
  }

  fun `test Enter on a fence that is already closed, or on a closing fence, is a plain Enter`() {
    myFixture.configureByText("a.yadda.feature", head + "      --- json<caret>\n      [1]\n      ---\n")
    myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER)
    assertEquals(0, myFixture.editor.document.text.count { it == '"' })
    assertEquals(2, myFixture.editor.document.text.lines().count { it.trim().startsWith("---") })

    myFixture.configureByText("b.yadda.feature", head + "      --- json\n      [1]\n      ---<caret>")
    myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER)
    assertFalse(myFixture.editor.document.text, '"' in myFixture.editor.document.text)
  }

  fun `test Enter after an opening fence with steps below it closes the block above them`() {
    myFixture.configureByText("a.yadda.feature", head + "      --- json<caret>\n    And more")
    myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER)
    assertEquals(head + "      --- json\n      \n      ---\n    And more", myFixture.editor.document.text)
  }

  fun `test a block typed above other blocks is closed, because a step line follows it`() {
    val rest = "    When x\n      ---\n      | a |\n      ---\n    And y\n      --- json\n      [1]\n      ---"
    myFixture.configureByText("a.yadda.feature", head + "      --- json<caret>\n" + rest)
    myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER)
    assertEquals(head + "      --- json\n      \n      ---\n" + rest, myFixture.editor.document.text)
  }

  fun `test a block typed at the end of a scenario is closed above the next scenario`() {
    myFixture.configureByText("a.yadda.feature", head + "      --- json<caret>\n\n  Scenario: T\n    Given z")
    myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER)
    assertEquals(head + "      --- json\n      \n      ---\n\n  Scenario: T\n    Given z", myFixture.editor.document.text)
  }

  fun `test a block typed into the middle of examples-basic's orders feature is closed`() {
    val orders = java.io.File("../../examples/basic/features/orders.feature").readText()
    val marker = "    And the order total is 171.00\n"
    assertTrue(marker in orders)
    myFixture.configureByText("orders.yadda.feature", orders.replace(marker, marker + "      --- json<caret>\n"))
    myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER)
    assertEquals(marker + "      --- json\n      \n      ---\n", myFixture.editor.document.text.substringAfter(marker).let { marker + it.substringBefore("\n\n  Scenario") + "\n" })
  }

  fun `test an opening fence with content under it is not closed again`() {
    myFixture.configureByText("a.yadda.feature", head + "      --- json<caret>\n      [1]\n      ---\n    And y\n      ---\n      | a |\n      ---")
    myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_ENTER)
    assertEquals(3, myFixture.editor.document.text.lines().count { it.trim() == "---" })
  }

  fun `test editing the content of a block keeps its fences`() {
    myFixture.configureByText("a.yadda.feature", head + "      --- json\n      {}\n      ---\n    And more")
    val host = host()
    WriteCommandAction.runWriteCommandAction(project) { ElementManipulators.handleContentChange(host, "      [1, 2]\n") }
    assertEquals(head + "      --- json\n      [1, 2]\n      ---\n    And more", myFixture.file.text)
  }

  fun `test editing the content of an unclosed block`() {
    myFixture.configureByText("a.yadda.feature", head + "      ---json\n      {}")
    val host = host()
    WriteCommandAction.runWriteCommandAction(project) { ElementManipulators.handleContentChange(host, "      []") }
    assertEquals(head + "      ---json\n      []", myFixture.file.text)
  }

  fun `test a doc string in a plain Gherkin file is edited by Gherkin's manipulator`() {
    myFixture.configureByText("a.feature", head + "      \"\"\"\n      {}\n      \"\"\"")
    val host = host()
    WriteCommandAction.runWriteCommandAction(project) { ElementManipulators.handleContentChange(host, "\n      []\n      ") }
    assertEquals(head + "      \"\"\"\n      []\n      \"\"\"", myFixture.file.text)
  }
}

class CsvRecordsTest : junit.framework.TestCase() {
  private fun fields(text: String, delimiter: Char = ','): List<Int> = CsvRecords.parse(text, delimiter).records.map { it.fields }

  fun `test records are counted by their fields and blank lines are skipped`() {
    assertEquals(listOf(3, 3, 2), fields("a,b,c\n\n1,2,3\n  \n4,5\n"))
    assertEquals(listOf(3), fields("a,b,c"))
    assertEquals(emptyList<Int>(), fields("\n\n"))
  }

  fun `test a quoted field may hold the delimiter, quotes and line breaks`() {
    assertEquals(listOf(2, 2), fields("a,b\n\"x, y\",\"say \"\"hi\"\"\"\n"))
    assertEquals(listOf(2, 2), fields("a,b\n\"two\nlines\",z"))
  }

  fun `test a quote inside an unquoted field is just text`() {
    assertEquals(listOf(2), fields("5\" nail,b"))
  }

  fun `test an open quote is reported where it opened`() {
    val result = CsvRecords.parse("a,b\n1,\"oops\n2,3\n")
    assertEquals(6, result.unterminatedQuoteAt)
  }

  fun `test a tab delimited record and a comma inside it`() {
    assertEquals(listOf(2, 2), fields("a\tb\n1,5\t2", '\t'))
  }
}

class YaddaCsvBlockInspectionTest : BasePlatformTestCase() {
  private fun warnings(csv: String): List<String> {
    myFixture.enableInspections(YaddaCsvBlockInspection::class.java)
    myFixture.configureByText("a.yadda.feature", "Feature: F\n  Scenario: S\n    Given a step\n$csv")
    return myFixture.doHighlighting().filter { it.description != null && it.severity.myVal >= 300 }
      .map { "${it.description} :: '${myFixture.file.text.substring(it.startOffset, it.endOffset)}'" }
  }

  fun `test a row with the wrong number of fields is flagged`() {
    assertEquals(
      listOf("This row has 2 fields, but the header has 3 fields :: '1,Ann'"),
      warnings("      --- csv\n      id,name,age\n      1,Ann\n      2,Bo,30\n      ---"),
    )
  }

  fun `test a well formed block, a block of another kind and a quoted comma are clean`() {
    assertEquals(emptyList<String>(), warnings("      --- csv\n      a,b\n      \"x, y\",2\n      ---"))
    assertEquals(emptyList<String>(), warnings("      --- notes\n      a,b\n      1\n      ---"))
    assertEquals(emptyList<String>(), warnings("      ---\n      a,b\n      1\n      ---"))
  }

  fun `test an open quote is flagged`() {
    assertEquals(listOf("This quoted field is never closed :: '\"'"), warnings("      --- csv\n      a,b\n      1,\"x\n      ---"))
  }

  fun `test tsv is split on tabs`() {
    assertEquals(listOf("This row has 1 field, but the header has 2 fields :: '1,2'"), warnings("      --- tsv\n      a\tb\n      1,2\n      ---"))
  }

  fun `test a plain feature file is not checked`() {
    myFixture.enableInspections(YaddaCsvBlockInspection::class.java)
    myFixture.configureByText("a.feature", "Feature: F\n  Scenario: S\n    Given a step\n      --- csv\n      a,b\n      1\n      ---")
    assertEquals(emptyList<String>(), myFixture.doHighlighting().filter { it.description?.startsWith("This row") == true }.map { it.description })
  }
}
