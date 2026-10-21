package io.github.jonfrankel.playwrightyadda.run

import com.intellij.lang.javascript.psi.JSFile
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.util.CommonProcessors

/**
 * The Playwright spec file that runs a feature file: the one that calls `defineFeatureSuite`.
 *
 * A project normally has one, such as `e2e/features.spec.ts`. With several (one per package of a
 * monorepo) the one nearest to the feature file is used, because the feature directory a spec points
 * at is computed at runtime (`path.resolve(__dirname, "../features")`) and can't be read reliably.
 */
object YaddaFeatureSpecs {
  private const val ENTRY_POINT = "defineFeatureSuite"

  fun specFor(feature: VirtualFile, project: Project): JSFile? {
    if (DumbService.isDumb(project)) return null

    val collector = CommonProcessors.CollectProcessor<PsiFile>()
    PsiSearchHelper.getInstance(project).processAllFilesWithWord(
      ENTRY_POINT,
      GlobalSearchScope.projectScope(project),
      collector,
      true,
    )
    val candidates = collector.results.filterIsInstance<JSFile>().filter { isSpec(it) }
    return candidates.maxWithOrNull(
      compareBy<JSFile> { sharedPrefix(it.virtualFile.path, feature.path) }
        // Stable between runs when two are equally near.
        .thenByDescending { it.virtualFile.path },
    )
  }

  /** A file that calls `defineFeatureSuite(...)`, not one that only mentions or re-exports it. */
  private fun isSpec(file: JSFile): Boolean =
    Regex("""\b$ENTRY_POINT\s*[(<]""").containsMatchIn(file.text) &&
      file.virtualFile.name.contains(Regex("""\.(spec|test)\.[cm]?[jt]sx?$"""))

  private fun sharedPrefix(a: String, b: String): Int = a.commonPrefixWith(b).length
}
