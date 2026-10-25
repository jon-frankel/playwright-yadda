package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.jonfrankel.playwrightyadda.libraries.FeatureTags
import io.github.jonfrankel.playwrightyadda.steps.YaddaStepCreator
import org.jetbrains.plugins.cucumber.inspections.CucumberStepInspection
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes
import junit.framework.TestCase

/** The words of Yadda's localisations, without the IDE. */
class VocabularyTest : TestCase() {
  private fun vocabulary(name: String) = Vocabulary.byName(name)!!

  fun `test English comes first, and the languages Yadda can't build a parser for are left out`() {
    assertEquals("English", Vocabulary.all.first().name)
    assertTrue(Vocabulary.all.map { it.name }.containsAll(listOf("French", "German", "Russian", "Chinese", "Spanish")))
    // Yadda 3.2's Dutch scenario word is not a valid regex.
    assertNull(Vocabulary.byName("Dutch"))
    assertNull(Vocabulary.byName("Klingon"))
  }

  fun `test a heading is matched with the words of its language, whatever the case`() {
    val french = vocabulary("French")
    assertEquals(Vocabulary.Heading.FEATURE to "Le panier", french.match("  Fonctionnalité: Le panier")?.let { it.heading to it.title })
    assertEquals(Vocabulary.Heading.SCENARIO, french.match("SCÉNARIO: x")?.heading)
    assertEquals(Vocabulary.Heading.SCENARIO, french.match("Plan du scénario: x")?.heading)
    assertEquals(Vocabulary.Heading.EXAMPLES, french.match("Où:")?.heading)
    assertNull(french.match("Feature: F"))
    assertEquals(Vocabulary.Heading.FEATURE, Vocabulary.ENGLISH.match("FEATURE: f")?.heading)
    assertNull(Vocabulary.ENGLISH.match("Fonctionnalité: F"))
  }

  fun `test the keyword at the start of a line is found up to its colon`() {
    assertEquals(Vocabulary.Heading.SCENARIO to 16, Vocabulary.ENGLISH.keywordAt("Scenario Outline: S"))
    assertEquals(Vocabulary.Heading.EXAMPLES to 5, Vocabulary.ENGLISH.keywordAt("Where: x"))
    assertEquals(Vocabulary.Heading.FEATURE to 14, vocabulary("French").keywordAt("Fonctionnalité: F"))
    assertNull(vocabulary("French").keywordAt("Feature: F"))
    assertNull(Vocabulary.ENGLISH.keywordAt("Scenario S"))
  }

  fun `test a step keyword picks the method of its language, and a word for all three is neither`() {
    fun method(vocabulary: Vocabulary, text: String) = vocabulary.stepKeyword(text)?.let { it.method to it.rest }
    assertEquals("given" to "a cart", method(Vocabulary.ENGLISH, "Given a cart"))
    assertEquals("given" to "a cart", method(Vocabulary.ENGLISH, "With a cart"))
    assertEquals("when" to "a cart", method(Vocabulary.ENGLISH, "If a cart"))
    assertEquals(null to "a cart", method(Vocabulary.ENGLISH, "And a cart"))
    assertNull(method(Vocabulary.ENGLISH, "the cart"))
    // The word lists give either case for the first letter, and no other.
    assertEquals("given" to "a cart", method(Vocabulary.ENGLISH, "given a cart"))
    assertNull(method(Vocabulary.ENGLISH, "GIVEN a cart"))

    val french = vocabulary("French")
    assertEquals("given" to "un panier", method(french, "Soit un panier"))
    assertEquals("given" to "un panier", method(french, "Étant donné un panier"))
    assertEquals("when" to "il part", method(french, "Quand il part"))
    assertEquals("then" to "il est vide", method(french, "Alors il est vide"))
    assertEquals(null to "il part", method(french, "Et il part"))
    assertNull(method(french, "Given a cart"))
  }

  fun `test a keyword with a suffix of its own is taken whole`() {
    assertEquals("x", vocabulary("Russian").withoutStepKeyword("Допустим я x"))
    assertEquals("x", vocabulary("Russian").withoutStepKeyword("Допустим x"))
    assertEquals("x", vocabulary("Chinese").withoutStepKeyword("假如 x"))
  }

  fun `test the text without its keyword is what a definition is written against`() {
    assertEquals("the cart is empty", Vocabulary.ENGLISH.withoutStepKeyword("With the cart is empty"))
    assertEquals("the cart is empty", Vocabulary.ENGLISH.withoutStepKeyword("the cart is empty"))
    assertEquals("Soit un panier", Vocabulary.ENGLISH.withoutStepKeyword("Soit un panier"))
  }

  fun `test tags are found under the headings of the language`() {
    val text = "@Libraries=a\nFonctionnalité: F\n  @Libraries=b\n  Scénario: S\n    Soit x\n"
    val french = FeatureTags.parse(text, vocabulary("French"))
    assertEquals(listOf("a"), french.librariesAt(4)!!.feature)
    assertEquals(listOf("b"), french.librariesAt(4)!!.scenario)
    // Read as English, no heading is found, so nothing is known about the scenario.
    assertNull(FeatureTags.parse(text).librariesAt(4))
  }
}

/** A project whose features are written in another language: the setting, the lexer, and what hangs off them. */
class YaddaLocalisationTest : BasePlatformTestCase() {
  private val settings get() = YaddaSettings.of(project)

  override fun tearDown() {
    try {
      settings.language = YaddaSettings.ENGLISH
    } finally {
      super.tearDown()
    }
  }

  private val french = """
    @Libraries=panier
    Fonctionnalité: Le panier
      Contexte:
        Soit un panier vide
      Scénario: Ajouter un article [n]
        Quand le client ajoute [n] clavier
        Alors le panier contient [n] article
      Exemples:
        n
        1
        2
  """.trimIndent()

  private fun steps() {
    myFixture.addFileToProject(
      "panier.ts",
      """
      export const panierSteps = createStepLibrary(undefined, "bound", Yadda.localisation.French);
      panierSteps.given(/^un panier vide$/, function () {});
      panierSteps.when(/^le client ajoute (\d+) clavier$/, function (n) {});
      panierSteps.then(/^le panier contient (\d+) article$/, function (n) {});
      """.trimIndent(),
    )
  }

  private fun open(name: String, text: String): PsiFile {
    if ('/' in name) {
      myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject(name, text).virtualFile)
    } else {
      myFixture.configureByText(name, text)
    }
    return myFixture.file
  }

  private fun headingKinds(file: PsiFile): List<String> {
    val kinds = setOf(
      GherkinTokenTypes.FEATURE_KEYWORD,
      GherkinTokenTypes.BACKGROUND_KEYWORD,
      GherkinTokenTypes.SCENARIO_KEYWORD,
      GherkinTokenTypes.EXAMPLES_KEYWORD,
    )
    return PsiTreeUtil.collectElements(file) { it.node?.elementType in kinds }.map { it.text }
  }

  fun `test English is the default`() {
    assertEquals("English", settings.language)
    assertSame(Vocabulary.ENGLISH, settings.vocabulary)
    assertSame(Vocabulary.ENGLISH, YaddaSettings.vocabularyOf(null))
    // A name Yadda has no language for is English too.
    settings.language = "Klingon"
    assertSame(Vocabulary.ENGLISH, settings.vocabulary)
  }

  fun `test a French feature has its headings read when the project is French`() {
    settings.language = "French"
    val file = open("a.yadda.feature", french)
    assertEquals(listOf("Fonctionnalité", "Contexte", "Scénario", "Exemples"), headingKinds(file))
    val feature = (file as YaddaFile).features.single()
    assertEquals("Le panier", feature.featureName)
    assertEquals(listOf("Soit un panier vide"), PsiTreeUtil.findChildrenOfType(file, GherkinStep::class.java).take(1).map { it.name })
    assertEquals(3, PsiTreeUtil.findChildrenOfType(file, GherkinStep::class.java).size)
  }

  fun `test the same text is not French when the project is English`() {
    val file = open("a.yadda.feature", french)
    assertEquals(emptyList<String>(), headingKinds(file).filter { it == "Fonctionnalité" })
  }

  fun `test Yadda's own parser is asked in the project's language`() {
    myFixture.enableInspections(YaddaParseInspection::class.java)
    settings.language = "French"
    open("a.yadda.feature", french)
    assertEquals(emptyList<String>(), myFixture.doHighlighting().filter { it.severity.myVal >= 200 && "Yadda" in it.description.orEmpty() }.map { it.description })

    settings.language = "English"
    open("b.yadda.feature", french)
    val errors = myFixture.doHighlighting().filter { "Yadda" in it.description.orEmpty() }.map { it.description }
    assertEquals(listOf("Yadda can't parse this: A feature must contain one or more scenarios"), errors)
  }

  fun `test changing the language makes an open file read its headings again`() {
    myFixture.enableInspections(YaddaParseInspection::class.java)
    val file = open("a.yadda.feature", french)
    val before = (file as YaddaFile).grammar
    assertNotNull(before.issue)
    settings.language = "French"
    assertNull((file as YaddaFile).grammar.issue)
  }

  fun `test a step is found by the definition written without its keyword`() {
    steps()
    settings.language = "French"
    open("features/panier.yadda.feature", french)
    myFixture.enableInspections(CucumberStepInspection::class.java)
    val undefined = myFixture.doHighlighting().filter { "Undefined step" in it.description.orEmpty() }
    assertEquals(emptyList<String>(), undefined.map { myFixture.file.text.substring(it.startOffset, it.endOffset) })
  }

  fun `test Cmd+B goes from a French step to its definition`() {
    steps()
    settings.language = "French"
    open("features/panier.yadda.feature", french)
    myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("le client ajoute") + 3)
    val targets = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)
    assertEquals(listOf("panier.ts: panierSteps.when(/^le client ajoute (\\d+) clavier$/, function (n) {})"), targets.map { it.containingFile.name + ": " + it.parent.parent.text.lines().first().trim() })
  }

  fun `test a step with no definition is offered one with the method of its language's keyword`() {
    myFixture.addFileToProject("panier.ts", "export const panierSteps = createStepLibrary();")
    settings.language = "French"
    val file = open("features/panier.yadda.feature", "Fonctionnalité: F\n  Scénario: S\n    Quand le client part\n    Et il ferme la porte\n")
    myFixture.enableInspections(CucumberStepInspection::class.java)
    val steps = YaddaStepCreator.undefinedIn(file)
    assertEquals(listOf("lib.when(\"le client part\"", "lib.when(\"il ferme la porte\""), steps.map { it.skeleton.render("lib", false).substringBefore(",") })
  }

  private val frenchMarkdown = """
    # Fonctionnalité: Le panier

    ## Scénario: Ajouter un article

    - Soit un panier vide
    - Quand le client ajoute 2 clavier
    - Alors un article inconnu
  """.trimIndent()

  private fun markdownProblems(): List<String> {
    myFixture.enableInspections(
      YaddaMarkdownParseInspection::class.java,
      YaddaMarkdownUndefinedStepInspection::class.java,
    )
    return myFixture.doHighlighting()
      .filter { it.description != null && it.severity.myVal >= 200 }
      .map { "${it.description} :: ${myFixture.file.text.substring(it.startOffset, it.endOffset).trim()}" }
  }

  fun `test a Markdown feature is read in the project's language, and its steps found without their keyword`() {
    steps()
    settings.language = "French"
    open("features/panier.yadda.md", frenchMarkdown)
    assertTrue(YaddaMarkdown.isFeature(myFixture.file))
    // Only the step with no definition is undefined; the two with one are found whatever their keyword.
    assertEquals(listOf("Undefined step :: Alors un article inconnu"), markdownProblems().filter { "Undefined" in it })
    assertEquals(emptyList<String>(), markdownProblems().filter { "Yadda" in it })
  }

  fun `test the same Markdown in an English project is not a feature`() {
    open("features/panier.yadda.md", frenchMarkdown)
    // A .yadda.md file is always a feature, and then Yadda says what it makes of the French headings.
    assertEquals(listOf("Yadda can't parse this: A feature must contain one or more scenarios :: # Fonctionnalité: Le panier"), markdownProblems().filter { "Yadda" in it }.take(1))
  }

  fun `test the settings page is in Tools and sets the language`() {
    val extension = com.intellij.openapi.options.Configurable.PROJECT_CONFIGURABLE.getExtensions(project)
      .single { it.id == "io.github.jonfrankel.playwrightyadda.settings" }
    assertEquals("tools", extension.parentId)
    assertEquals("Playwright Yadda", extension.displayName)
    val configurable = extension.createConfigurable() as YaddaSettingsConfigurable
    assertNotNull(configurable.createComponent())
    configurable.reset()
    assertFalse(configurable.isModified)
    configurable.disposeUIResources()
  }
}
