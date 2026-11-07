package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Filters step definitions to real step definitions in project source code, ruling out:
 * - Build artifacts and dependencies (`node_modules`, `.pnpm`, `dist`, `build`, etc.)
 * - Test files (`*.test.ts`, `*.spec.ts`, `__tests__/`, etc.)
 * - Calls inside test runner functions (`it(...)`, `test(...)`, `describe(...)`, etc.)
 * - Definitions from other packages in a monorepo when local package definitions exist.
 */
object YaddaStepFilter {
  private val IGNORED_DIRECTORY_NAMES = setOf(
    "node_modules",
    ".pnpm",
    "dist",
    "build",
    "out",
    ".gradle",
    ".git",
    ".idea",
    ".yarn",
    ".turbo",
    ".cache",
  )

  private val TEST_FILE_REGEX = Regex("""^.+\.(test|spec)\.[cm]?[jt]sx?$""", RegexOption.IGNORE_CASE)
  private val TEST_DIR_NAMES = setOf("__tests__", "__mocks__")

  private val TEST_RUNNER_NAMES = setOf(
    "it",
    "test",
    "describe",
    "suite",
    "beforeeach",
    "aftereach",
    "beforeall",
    "afterall",
    "before",
    "after",
  )

  /** Whether [file] is inside an ignored directory like node_modules or dist. */
  fun isIgnoredLocation(file: VirtualFile): Boolean {
    val path = file.path.replace('\\', '/')
    val segments = path.split('/')
    return segments.any { it in IGNORED_DIRECTORY_NAMES }
  }

  /** Whether [file] is a unit or integration test file (e.g. *.test.ts, *.spec.js, __tests__/). */
  fun isTestFile(file: VirtualFile): Boolean {
    if (TEST_FILE_REGEX.matches(file.name)) return true
    val path = file.path.replace('\\', '/')
    val segments = path.split('/')
    return segments.dropLast(1).any { it in TEST_DIR_NAMES }
  }

  /**
   * Whether [element] is inside an enclosing test runner block (`it(...)`, `test(...)`, `describe(...)`, etc.),
   * meaning any `.given(...)` call inside it is part of a test rather than an actual step definition.
   */
  fun isInsideTestCall(element: PsiElement): Boolean {
    val stepCall = findStepCall(element)
    var current: PsiElement? = (stepCall ?: element).parent
    while (current != null && current !is PsiFile) {
      if (current is JSCallExpression && isTestRunnerCall(current)) {
        return true
      }
      current = current.parent
    }
    return false
  }

  /** True if [element] is a valid candidate step definition in non-test project source code. */
  fun isValidStepDefinition(element: PsiElement): Boolean {
    val targetElement = (element as? PomTargetPsiElement)?.let {
      (it.target as? YaddaStepPomTarget)?.navigationElement ?: element
    } ?: element

    val file = targetElement.containingFile ?: return false
    val vFile = file.virtualFile ?: file.viewProvider.virtualFile
    if (isIgnoredLocation(vFile)) return false
    if (isTestFile(vFile)) return false
    if (isInsideTestCall(targetElement)) return false
    return true
  }

  /**
   * Filters candidate definitions:
   * 1. Excludes invalid definitions (node_modules, test files, test calls).
   * 2. In a monorepo, if candidates exist in the same package root (nearest `package.json`)
   *    as the context file, narrows to those local package candidates.
   */
  fun filterCandidates(definitions: List<PsiElement>, context: PsiElement): List<PsiElement> {
    val valid = definitions.filter { isValidStepDefinition(it) }
    if (valid.isEmpty()) return emptyList()

    val contextFile = context.containingFile?.virtualFile ?: context.containingFile?.viewProvider?.virtualFile
    if (contextFile != null) {
      val contextPkg = findPackageRoot(contextFile)
      if (contextPkg != null) {
        val samePackage = valid.filter { def ->
          val defTarget = (def as? PomTargetPsiElement)?.let {
            (it.target as? YaddaStepPomTarget)?.navigationElement ?: def
          } ?: def
          val defFile = defTarget.containingFile?.virtualFile ?: defTarget.containingFile?.viewProvider?.virtualFile
          defFile != null && findPackageRoot(defFile) == contextPkg
        }
        if (samePackage.isNotEmpty()) {
          return samePackage
        }
      }
    }

    return valid
  }

  fun findPackageRoot(file: VirtualFile): VirtualFile? {
    var dir: VirtualFile? = if (file.isDirectory) file else file.parent
    while (dir != null) {
      if (dir.findChild("package.json") != null) return dir
      dir = dir.parent
    }
    return null
  }

  private fun findStepCall(element: PsiElement): JSCallExpression? {
    var cur: PsiElement? = element
    while (cur != null && cur !is PsiFile) {
      if (cur is JSCallExpression && YaddaSteps.isStepCallCandidate(cur)) {
        return cur
      }
      cur = cur.parent
    }
    return null
  }

  private fun isTestRunnerCall(call: JSCallExpression): Boolean {
    val method = call.methodExpression ?: return false
    val name = when (method) {
      is JSReferenceExpression -> rootQualifierName(method) ?: method.referenceName
      else -> null
    }
    return name?.lowercase() in TEST_RUNNER_NAMES
  }

  private fun rootQualifierName(ref: JSReferenceExpression): String? {
    var current: JSReferenceExpression = ref
    while (current.qualifier is JSReferenceExpression) {
      current = current.qualifier as JSReferenceExpression
    }
    return current.referenceName
  }
}
