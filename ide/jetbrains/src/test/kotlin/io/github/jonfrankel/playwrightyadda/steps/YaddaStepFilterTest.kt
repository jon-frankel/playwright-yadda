package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class YaddaStepFilterTest : BasePlatformTestCase() {
  private fun add(path: String, text: String): PsiFile =
    myFixture.addFileToProject(path, text.trimIndent())

  fun `test ignored locations are recognized`() {
    val nm = add("node_modules/pkg/steps.ts", "steps.given('step', () => {})")
    assertTrue(YaddaStepFilter.isIgnoredLocation(nm.virtualFile))

    val pnpm = add(".pnpm/pkg@1.0.0/node_modules/pkg/steps.ts", "steps.given('step', () => {})")
    assertTrue(YaddaStepFilter.isIgnoredLocation(pnpm.virtualFile))

    val dist = add("dist/steps.js", "steps.given('step', () => {})")
    assertTrue(YaddaStepFilter.isIgnoredLocation(dist.virtualFile))

    val build = add("build/steps.js", "steps.given('step', () => {})")
    assertTrue(YaddaStepFilter.isIgnoredLocation(build.virtualFile))

    val src = add("src/steps.ts", "steps.given('step', () => {})")
    assertFalse(YaddaStepFilter.isIgnoredLocation(src.virtualFile))
  }

  fun `test test files are recognized`() {
    val testTs = add("src/fixtures.test.ts", "steps.given('step', () => {})")
    assertTrue(YaddaStepFilter.isTestFile(testTs.virtualFile))

    val specTs = add("src/features.spec.ts", "steps.given('step', () => {})")
    assertTrue(YaddaStepFilter.isTestFile(specTs.virtualFile))

    val testJs = add("src/login.test.js", "steps.given('step', () => {})")
    assertTrue(YaddaStepFilter.isTestFile(testJs.virtualFile))

    val inTestsDir = add("src/__tests__/helper.ts", "steps.given('step', () => {})")
    assertTrue(YaddaStepFilter.isTestFile(inTestsDir.virtualFile))

    val realStep = add("src/steps/login.ts", "steps.given('step', () => {})")
    assertFalse(YaddaStepFilter.isTestFile(realStep.virtualFile))

    val stepName = add("src/steps/login.steps.ts", "steps.given('step', () => {})")
    assertFalse(YaddaStepFilter.isTestFile(stepName.virtualFile))
  }

  fun `test step calls inside test runner functions are detected`() {
    val file = add(
      "src/helpers.ts",
      """
      test("a vitest test", () => {
        builder.given("inside test", () => {});
      });

      describe("a suite", () => {
        it("a scenario", () => {
          client.given("inside it", () => {});
        });
      });

      steps.given("at module level", () => {});

      export function defineSteps(s) {
        s.given("inside regular function", () => {});
      }
      """,
    )

    val literals = PsiTreeUtil.findChildrenOfType(file, JSLiteralExpression::class.java)
    val insideTest = literals.first { it.stringValue == "inside test" }
    val insideIt = literals.first { it.stringValue == "inside it" }
    val moduleLevel = literals.first { it.stringValue == "at module level" }
    val regularFunc = literals.first { it.stringValue == "inside regular function" }

    assertTrue(YaddaStepFilter.isInsideTestCall(insideTest))
    assertTrue(YaddaStepFilter.isInsideTestCall(insideIt))
    assertFalse(YaddaStepFilter.isInsideTestCall(moduleLevel))
    assertFalse(YaddaStepFilter.isInsideTestCall(regularFunc))
  }

  fun `test findPackageRoot finds nearest package json`() {
    add("packages/pkg-a/package.json", """{ "name": "pkg-a" }""")
    val stepInA = add("packages/pkg-a/src/steps.ts", "export const a = 1;")

    val pkgRoot = YaddaStepFilter.findPackageRoot(stepInA.virtualFile)
    assertNotNull(pkgRoot)
    assertEquals("pkg-a", pkgRoot!!.name)
  }
}
