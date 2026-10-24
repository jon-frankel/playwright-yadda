package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import io.github.jonfrankel.playwrightyadda.libraries.FeatureTags
import io.github.jonfrankel.playwrightyadda.libraries.LibraryRegistries
import io.github.jonfrankel.playwrightyadda.libraries.enabledLibraries
import io.github.jonfrankel.playwrightyadda.yadda.StepSkeleton
import io.github.jonfrankel.playwrightyadda.yadda.YaddaFile
import io.github.jonfrankel.playwrightyadda.yadda.YaddaGrammar
import io.github.jonfrankel.playwrightyadda.yadda.YaddaMarkdown
import org.jetbrains.plugins.cucumber.psi.GherkinStep
import org.jetbrains.plugins.cucumber.steps.reference.CucumberStepReference

/**
 * Writes the step definition for a step of a Yadda feature, in the way this library's steps are written:
 * `orderSteps.given(/^…$/, async function () {})`, in the file of the `createStepLibrary()` variable the
 * step's scenario runs. The Cucumber.js plugin's own "Create step definition" writes `Given(…)` with an
 * import from `cucumber`, which isn't a Yadda step, and it fails on a step with no keyword.
 */
object YaddaStepCreator {
  /** A step of a feature file with no definition, which [skeleton] can be written for. */
  class Undefined(val step: YaddaGrammar.Step, val skeleton: StepSkeleton, val anchor: PsiFile, val range: TextRange)

  /** A `createStepLibrary()` variable a definition can be added to. */
  class Target(val variable: JSVariable) {
    val receiver: String get() = variable.name.orEmpty()
    val label: String
      get() {
        val file = variable.containingFile
        val base = variable.project.basePath
        val path = file.virtualFile?.path?.let { if (base != null) it.removePrefix("$base/") else it } ?: file.name
        return "$receiver ($path)"
      }
  }

  /** How a choice between several targets is asked. A test replaces it. */
  fun interface Chooser {
    fun choose(project: Project, editor: Editor?, targets: List<Target>, onChosen: (Target) -> Unit)
  }

  var chooser: Chooser = Chooser { project, editor, targets, onChosen ->
    val step = object : BaseListPopupStep<Target>("Add Step Definition To", targets) {
      override fun getTextFor(value: Target): String = value.label

      override fun onChosen(selectedValue: Target, finalChoice: Boolean): PopupStep<*>? {
        onChosen(selectedValue)
        return PopupStep.FINAL_CHOICE
      }
    }
    val popup = JBPopupFactory.getInstance().createListPopup(step)
    if (editor != null) popup.showInBestPositionFor(editor) else popup.showCenteredInCurrentWindow(project)
  }

  /** The step at [offset] of [file] if it has no definition and a skeleton can be written for it. */
  fun undefinedAt(file: PsiFile, offset: Int): Undefined? {
    if (file is YaddaFile) {
      val step = file.findElementAt(offset)?.let { PsiTreeUtil.getParentOfType(it, GherkinStep::class.java, false) } ?: return null
      return undefinedOf(file, step)
    }
    if (!YaddaMarkdown.isFeature(file)) return null
    val site = YaddaMarkdown.siteAt(file, offset) ?: return null
    return undefinedOf(file, site)
  }

  /** Every such step of [file], in order. */
  fun undefinedIn(file: PsiFile): List<Undefined> {
    if (file is YaddaFile) {
      return PsiTreeUtil.findChildrenOfType(file, GherkinStep::class.java).mapNotNull { undefinedOf(file, it) }
    }
    if (!YaddaMarkdown.isFeature(file)) return emptyList()
    return YaddaMarkdown.sites(file).mapNotNull { undefinedOf(file, it) }
  }

  private fun undefinedOf(file: YaddaFile, step: GherkinStep): Undefined? {
    val line = file.viewProvider.document?.getLineNumber(step.textOffset)?.plus(1) ?: return null
    val yadda = file.grammar.steps[line] ?: return null
    val skeleton = StepSkeleton.of(yadda) ?: return null
    val reference = step.references.filterIsInstance<CucumberStepReference>().firstOrNull() ?: return null
    if (reference.multiResolve(false).isNotEmpty()) return null
    return Undefined(yadda, skeleton, file, step.textRange)
  }

  private fun undefinedOf(file: PsiFile, site: YaddaMarkdown.Site): Undefined? {
    val skeleton = StepSkeleton.of(site.step) ?: return null
    if (YaddaMarkdownSteps.definitions(file, site).isNotEmpty()) return null
    return Undefined(site.step, skeleton, file, site.range)
  }

  /**
   * The libraries [undefined] can be added to, the likeliest first: those the scenario names, then the
   * registry's defaults. When the scenario's libraries can't be read, every `createStepLibrary()` in the
   * project, since nothing says which one runs it.
   */
  fun targetsFor(undefined: Undefined): List<Target> {
    val file = undefined.anchor
    val project = file.project
    val tags = if (file is YaddaFile) FeatureTags.parse(file.text) else FeatureTags.parseMarkdown(file.text)
    val libraries = tags.librariesAt(undefined.step.line - 1)
    val registries = LibraryRegistries.all(project)

    val named = mutableListOf<PsiElement>()
    if (libraries != null) {
      for (registry in registries) {
        val defaults = registry.defaultLibraries ?: continue
        val inheritance = registry.inheritance ?: continue
        val enabled = enabledLibraries(defaults, inheritance, libraries.feature, libraries.scenario)
        val explicit = (libraries.scenario + libraries.feature).map { it.trim().lowercase() }.filter { it in enabled }
        val order = (explicit + enabled.filter { it !in explicit }).distinct()
        named += registry.librariesFor(order)
      }
    }
    val variables = named.filterIsInstance<JSVariable>().ifEmpty { LibraryRegistries.stepLibraries(project) }
    val unique = mutableListOf<JSVariable>()
    for (variable in variables) {
      if (unique.none { it.manager.areElementsEquivalent(it, variable) }) unique += variable
    }
    return unique.map(::Target)
  }

  /** Adds the definition for each of [steps], asking which library once for each set of candidates. */
  fun create(project: Project, editor: Editor?, steps: List<Undefined>) {
    val pending = steps.map { it to targetsFor(it) }.filter { it.second.isNotEmpty() }
    if (pending.isEmpty()) return

    val groups = pending.groupBy { (_, targets) -> targets.map { it.variable.containingFile.name + ":" + it.receiver } }.values.toList()
    fun next(index: Int, chosen: List<Pair<Undefined, Target>>) {
      if (index == groups.size) {
        write(project, chosen)
        return
      }
      val group = groups[index]
      val targets = group.first().second
      val choose = { target: Target -> next(index + 1, chosen + group.map { (undefined, _) -> undefined to target }) }
      if (targets.size == 1) choose(targets.single()) else chooser.choose(project, editor, targets, choose)
    }
    next(0, emptyList())
  }

  private fun write(project: Project, chosen: List<Pair<Undefined, Target>>) {
    var last: Pair<PsiFile, Int>? = null
    WriteCommandAction.runWriteCommandAction(project, "Create Step Definition", null, Runnable {
      val documents = PsiDocumentManager.getInstance(project)
      for ((undefined, target) in chosen) {
        val file = target.variable.containingFile
        val document = documents.getDocument(file) ?: continue
        val typed = file.name.substringAfterLast('.').lowercase().let { it == "ts" || it == "tsx" || it == "mts" || it == "cts" }
        val text = undefined.skeleton.render(target.receiver, typed)
        val separator = when {
          document.textLength == 0 -> ""
          document.text.endsWith("\n\n") -> ""
          document.text.endsWith("\n") -> "\n"
          else -> "\n\n"
        }
        val start = document.textLength
        document.insertString(start, "$separator$text\n")
        documents.commitDocument(document)
        CodeStyleManager.getInstance(project).reformatText(file, start, document.textLength)
        documents.commitDocument(document)
        last = file to document.text.lastIndexOf("\n});").coerceAtLeast(0)
      }
    })
    last?.let { (file, offset) ->
      file.virtualFile?.let { OpenFileDescriptor(project, it, offset).navigate(true) }
    }
  }
}
