package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.cucumber.psi.GherkinElementVisitor
import org.jetbrains.plugins.cucumber.psi.GherkinPystring

/**
 * Checks the shape of a `--- csv` (or `--- tsv`) block: every record has as many fields as the
 * header, and no quoted field is left open. WebStorm has no CSV language to inject, so this is the
 * checking a `--- json` block gets from the JSON plugin.
 */
class YaddaCsvBlockInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    object : GherkinElementVisitor() {
      override fun visitPystring(pystring: GherkinPystring) {
        if (pystring.containingFile !is YaddaFile) return
        val text = pystring.text
        val block = FencedBlock.of(text) ?: return
        val delimiter = when (block.remark.split(Regex("""[\s,;]+""")).first().lowercase()) {
          "csv" -> ','
          "tsv" -> '\t'
          else -> return
        }
        val content = block.content
        val result = CsvRecords.parse(content.substring(text), delimiter)
        val header = result.records.firstOrNull() ?: return

        for (record in result.records.drop(1)) {
          if (record.fields == header.fields) continue
          holder.registerProblem(
            pystring,
            record.range.shiftRight(content.startOffset),
            "This row has ${count(record.fields)}, but the header has ${count(header.fields)}",
          )
        }
        result.unterminatedQuoteAt?.let { at ->
          val quote = content.startOffset + at
          holder.registerProblem(pystring, TextRange(quote, quote + 1), "This quoted field is never closed")
        }
      }
    }

  private fun count(fields: Int) = if (fields == 1) "1 field" else "$fields fields"
}
