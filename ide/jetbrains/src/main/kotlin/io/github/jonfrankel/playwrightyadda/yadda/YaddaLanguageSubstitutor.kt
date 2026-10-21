package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor

/**
 * Parses a `.feature` file as Yadda when its project runs features with playwright-yadda. The file
 * keeps its Cucumber file type, so everything keyed to that type keeps working.
 */
class YaddaLanguageSubstitutor : LanguageSubstitutor() {
  override fun getLanguage(file: VirtualFile, project: Project): Language? =
    if (file.extension == "feature" && PlaywrightYaddaProjects.isYaddaFeature(file)) YaddaLanguage else null
}
