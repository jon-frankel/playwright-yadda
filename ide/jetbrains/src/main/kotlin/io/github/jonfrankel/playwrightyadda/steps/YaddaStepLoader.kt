package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiFile
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition

object YaddaStepLoader {
  private val STEP_KEYWORDS = setOf("given", "when", "then", "and", "but")

  fun loadDynamicSteps(module: Module): List<AbstractStepDefinition> {
    val project = module.project
    if (DumbService.isDumb(project)) return emptyList()

    return CachedValuesManager.getManager(project).getCachedValue(module) {
      CachedValueProvider.Result.create(
        discoverDynamicSteps(module),
        PsiModificationTracker.MODIFICATION_COUNT,
      )
    }
  }

  private fun discoverDynamicSteps(module: Module): List<AbstractStepDefinition> {
    val project = module.project
    val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(project)
    val manager = com.intellij.psi.PsiManager.getInstance(project)
    val extensions = listOf("ts", "js", "tsx", "jsx", "mts", "mjs", "cts", "cjs")
    val fileTypes = extensions.map { com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByExtension(it) }.distinct()

    val files = fileTypes
      .flatMap { com.intellij.psi.search.FileTypeIndex.getFiles(it, scope) }
      .distinct()
      .filter { !YaddaStepFilter.isIgnoredLocation(it) && !YaddaStepFilter.isTestFile(it) }
      .mapNotNull { manager.findFile(it) }

    val definitions = mutableListOf<AbstractStepDefinition>()
    for (file in files) {
      val calls = PsiTreeUtil.findChildrenOfType(file, JSCallExpression::class.java)
      for (call in calls) {
        val callee = call.methodExpression as? JSReferenceExpression ?: continue
        val name = callee.referenceName?.lowercase() ?: continue
        if (name !in STEP_KEYWORDS) continue
        if (YaddaStepFilter.isInsideTestCall(call)) continue

        val arg = call.arguments.firstOrNull() ?: continue
        // Cucumber.js plugin already indexes string and regexp literal expressions.
        // We only index dynamic/expression step definitions that Cucumber.js misses.
        if (arg is JSLiteralExpression && (arg.isQuotedLiteral || arg.isRegExpLiteral)) continue

        val regex = YaddaPatternResolver.extractPattern(arg) ?: continue
        definitions.add(YaddaStepDefinition(arg, regex))
      }
    }
    return definitions
  }
}
