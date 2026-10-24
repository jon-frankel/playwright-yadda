package io.github.jonfrankel.playwrightyadda.run

import com.intellij.icons.AllIcons
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.psi.PsiElement
import io.github.jonfrankel.playwrightyadda.yadda.YaddaMarkdown

/**
 * The run icon next to `# Feature:` and `## Scenario:` in a Markdown feature. For a `.feature` file the
 * Cucumber plugin puts it there and [YaddaFeatureRunConfigurationProducer] decides what it runs; a
 * Markdown file has nothing to put it there, so this does, on the first leaf of the keyword.
 */
class YaddaMarkdownRunLineMarkerContributor : RunLineMarkerContributor() {
  override fun getInfo(element: PsiElement): Info? {
    if (element.firstChild != null) return null
    val file = element.containingFile ?: return null
    if (!YaddaMarkdown.isFeature(file)) return null

    // One icon per heading: on the leaf its keyword starts in.
    val range = element.textRange
    val heading = YaddaMarkdown.headings(file).firstOrNull { it.offset >= range.startOffset && it.offset < range.endOffset } ?: return null
    if (YaddaMarkdownRuns.targetAt(file, heading.offset)?.heading !== heading) return null
    return Info(AllIcons.RunConfigurations.TestState.Run, ExecutorAction.getActions(0)) { "Run" }
  }
}
