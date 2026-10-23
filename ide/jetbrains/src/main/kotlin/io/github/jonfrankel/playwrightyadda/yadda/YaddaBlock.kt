package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.formatting.ASTBlock
import com.intellij.formatting.Alignment
import com.intellij.formatting.Block
import com.intellij.formatting.ChildAttributes
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.Wrap
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import java.util.regex.Pattern
import org.jetbrains.plugins.cucumber.psi.GherkinElementTypes
import org.jetbrains.plugins.cucumber.psi.GherkinParserDefinition
import org.jetbrains.plugins.cucumber.psi.GherkinTable
import org.jetbrains.plugins.cucumber.psi.GherkinTokenTypes

/**
 * The formatting block of `GherkinBlock` (from the Gherkin plugin, Apache License 2.0), with one
 * change: a table row can start with a cell. Gherkin's counts the pipes before a cell to find its
 * column, so the first cell of `qty | total` would be in column -1. Everything else is the same, so
 * the indent of a step, the alignment of a table and the lines left alone are as in a Gherkin file.
 */
class YaddaBlock(
  private val node: ASTNode,
  private val indent: Indent = Indent.getAbsoluteNoneIndent(),
) : ASTBlock {
  private val children: List<Block> by lazy { buildChildren() }

  override fun getNode(): ASTNode = node

  override fun getTextRange(): TextRange = node.textRange

  override fun getSubBlocks(): List<Block> = children

  private fun buildChildren(): List<Block> {
    val result = mutableListOf<Block>()
    for (child in node.getChildren(null)) {
      if (child.elementType == TokenType.WHITE_SPACE) continue
      // A cell with nothing in it is not a block.
      if (child.elementType == GherkinElementTypes.TABLE_CELL && child.getChildren(null).isEmpty()) continue

      val tagOfOutline = node.elementType == GherkinElementTypes.SCENARIO_OUTLINE &&
        child.elementType == GherkinElementTypes.TAG &&
        TreeUtil.findSibling(child, TokenSet.create(GherkinTokenTypes.SCENARIO_OUTLINE_KEYWORD)) == null
      var childIndent = if (child.elementType in INDENTED || tagOfOutline) Indent.getNormalIndent() else Indent.getNoneIndent()

      // A comment keeps the column it is in, except the dashes that group a row over several lines,
      // which go with the rows.
      if (child.elementType == GherkinTokenTypes.COMMENT) {
        val before = child.treePrev
        if (isFenceOfRows(child)) {
          // Before the table, at the table's column; in it, at its rows'.
          childIndent = if (child.treeParent?.elementType == GherkinElementTypes.TABLE) Indent.getNoneIndent() else Indent.getNormalIndent()
        } else if (before != null && (before.text.contains('\n') || before.treePrev == null)) {
          childIndent = Indent.getSpaceIndent(before.text.length - before.text.lastIndexOf('\n') - 1)
        }
      }

      // The line of a row written over several lines starts with a pipe, after an empty first cell.
      if (child.elementType == GherkinElementTypes.TABLE_ROW && startsWithEmptyCell(child)) {
        val table = node.psi as? GherkinTable
        if (table != null) childIndent = Indent.getSpaceIndent(table.getColumnWidth(0) + 1)
      }
      result.add(YaddaBlock(child, childIndent))
    }
    return result
  }

  /**
   * The dashes that fence a table: those between and after its rows, and those before it. They go
   * where the rows go; a comment otherwise keeps the column it is in.
   */
  private fun isFenceOfRows(comment: ASTNode): Boolean =
    comment.elementType == GherkinTokenTypes.COMMENT &&
      FENCE.matcher(comment.text).matches() &&
      (comment.treeParent?.elementType == GherkinElementTypes.TABLE || isBeforeTable(comment))

  private fun isBeforeTable(comment: ASTNode): Boolean {
    var next = comment.treeNext
    while (next != null && next.elementType == TokenType.WHITE_SPACE) next = next.treeNext
    return next?.elementType == GherkinElementTypes.TABLE
  }

  private fun startsWithEmptyCell(row: ASTNode): Boolean {
    val first = row.firstChildNode ?: return false
    return first.elementType == GherkinElementTypes.TABLE_CELL && first.textLength == 0
  }

  override fun getWrap(): Wrap? = null

  override fun getIndent(): Indent = indent

  override fun getAlignment(): Alignment? = null

  override fun getSpacing(child1: Block?, child2: Block): Spacing? {
    if (child1 == null) return null
    val first = (child1 as ASTBlock).node ?: return null
    val second = (child2 as ASTBlock).node ?: return null
    val type1 = first.elementType
    val type2 = second.elementType
    val parent1 = first.treeParent?.elementType

    return when {
      isFenceOfRows(second) -> null
      type2 in READ_ONLY -> Spacing.getReadOnlySpacing()
      type2 in GherkinElementTypes.SCENARIOS && type1 != GherkinTokenTypes.COMMENT && parent1 != GherkinElementTypes.RULE ->
        Spacing.createSpacing(0, 0, 2, true, 2)
      type1 == GherkinTokenTypes.PIPE && type2 == GherkinElementTypes.TABLE_CELL -> Spacing.createSpacing(1, 1, 0, false, 0)
      (type1 == GherkinElementTypes.TABLE_CELL || type1 == GherkinTokenTypes.PIPE) && type2 == GherkinTokenTypes.PIPE -> {
        val table = TreeUtil.findParent(first, GherkinElementTypes.TABLE) ?: return null
        val width = (table.psi as GherkinTable).getColumnWidth(columnOf(first))
        // After a pipe the cell is empty, and the pipe after it is a cell further on.
        val gap = width - first.text.trim().length + 1 + if (type1 == GherkinTokenTypes.PIPE) 2 else 0
        Spacing.createSpacing(gap, gap, 0, false, 0)
      }
      else -> null
    }
  }

  /**
   * The column of the cell [node], or for a pipe the column of the cell just before it. A row that
   * starts with a pipe has one more pipe than the column it has reached; one that starts with a cell
   * does not.
   */
  private fun columnOf(node: ASTNode): Int {
    var pipes = 0
    var at: ASTNode? = node
    var startsWithPipe = false
    while (at != null) {
      if (at.elementType == GherkinTokenTypes.PIPE) pipes++
      startsWithPipe = at.elementType == GherkinTokenTypes.PIPE
      at = at.treePrev
    }
    return if (startsWithPipe) pipes - 1 else pipes
  }

  override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
    val childIndent = if (node.elementType in INDENT_CHILDREN) Indent.getNormalIndent() else Indent.getNoneIndent()
    return ChildAttributes(childIndent, null)
  }

  override fun isIncomplete(): Boolean = when (node.elementType) {
    in GherkinElementTypes.SCENARIOS -> true
    GherkinElementTypes.FEATURE ->
      node.getChildren(TokenSet.create(GherkinElementTypes.FEATURE_HEADER, GherkinElementTypes.SCENARIO, GherkinElementTypes.SCENARIO_OUTLINE)).isEmpty()
    else -> false
  }

  override fun isLeaf(): Boolean = children.isEmpty()

  private companion object {
    val INDENTED = TokenSet.create(
      GherkinElementTypes.FEATURE_HEADER,
      GherkinElementTypes.RULE,
      GherkinElementTypes.SCENARIO,
      GherkinElementTypes.SCENARIO_OUTLINE,
      GherkinElementTypes.STEP,
      GherkinElementTypes.TABLE,
      GherkinElementTypes.EXAMPLES_BLOCK,
    )
    val INDENT_CHILDREN = TokenSet.create(
      GherkinParserDefinition.GHERKIN_FILE,
      GherkinElementTypes.FEATURE,
      GherkinElementTypes.SCENARIO,
      GherkinElementTypes.RULE,
      GherkinElementTypes.SCENARIO_OUTLINE,
    )
    val READ_ONLY = TokenSet.create(GherkinElementTypes.PYSTRING, GherkinTokenTypes.COMMENT)
    val FENCE: Pattern = Pattern.compile("""[|┆]?-{3,}.*""")
  }
}
