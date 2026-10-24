package io.github.jonfrankel.playwrightyadda.run

import com.intellij.psi.PsiFile
import io.github.jonfrankel.playwrightyadda.yadda.YaddaGrammar
import io.github.jonfrankel.playwrightyadda.yadda.YaddaMarkdown

/**
 * What can be run from a place in a Markdown feature: the scenario it is in, or the whole feature
 * from its own heading. A `Background:` has no run of its own, and playwright-yadda defines no test
 * for what is under a `Rule:`, so neither of those has one.
 */
object YaddaMarkdownRuns {
  class Target(val name: String, val grep: String, val heading: YaddaMarkdown.Heading)

  fun targetAt(file: PsiFile, offset: Int): Target? {
    val headings = YaddaMarkdown.headings(file)
    // An Examples: heading belongs to the scenario above it.
    val current = headings.lastOrNull { it.offset <= offset && it.marker.kind != YaddaGrammar.MarkerKind.EXAMPLES } ?: return null
    val container = headings.lastOrNull {
      it.offset <= current.offset && (it.marker.kind == YaddaGrammar.MarkerKind.FEATURE || it.marker.kind == YaddaGrammar.MarkerKind.RULE)
    } ?: return null
    if (container.marker.kind != YaddaGrammar.MarkerKind.FEATURE) return null
    val featureTitle = container.marker.title.takeIf { it.isNotBlank() } ?: return null

    return when (current.marker.kind) {
      YaddaGrammar.MarkerKind.FEATURE -> Target(featureTitle, PlaywrightGrep.forFeature(featureTitle), current)
      YaddaGrammar.MarkerKind.SCENARIO -> {
        val title = current.marker.title.takeIf { it.isNotBlank() } ?: return null
        // An outline's scenarios are named after its rows, so only the start of its title is fixed.
        val isOutline = headings.dropWhile { it !== current }.getOrNull(1)?.marker?.kind == YaddaGrammar.MarkerKind.EXAMPLES
        Target(title, if (isOutline) PlaywrightGrep.forScenarioOutline(featureTitle, title) else PlaywrightGrep.forScenario(featureTitle, title), current)
      }
      else -> null
    }
  }
}
