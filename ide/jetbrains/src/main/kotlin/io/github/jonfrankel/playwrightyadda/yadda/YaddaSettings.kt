package io.github.jonfrankel.playwrightyadda.yadda

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiManager
import com.intellij.util.FileContentUtil

/**
 * Which of Yadda's localisations the project's features are written in. It is a setting, not read from the
 * code that gives the language to `defineFeatureSuite({ language })`: the lexer decides what a heading is
 * before any file is parsed, and has nothing but its text and the project to go on, so it can't search
 * the project's source for an option. The setting has to agree with the code.
 *
 * It is stored with the project, in `.idea/playwrightYadda.xml`, which can be shared.
 */
@State(name = "PlaywrightYaddaSettings", storages = [Storage("playwrightYadda.xml")])
class YaddaSettings : PersistentStateComponent<YaddaSettings.State> {
  class State {
    var language: String = ENGLISH
  }

  private var state = State()

  /** Changes whenever the language does, for what is kept per file and depends on it. */
  val tracker = SimpleModificationTracker()

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
    tracker.incModificationCount()
  }

  var language: String
    get() = state.language
    set(value) {
      if (value == state.language) return
      state.language = value
      tracker.incModificationCount()
    }

  val vocabulary: Vocabulary get() = Vocabulary.byName(state.language) ?: Vocabulary.ENGLISH

  /** Makes the open files, and what is kept for the closed ones, forget what they made of the old language. */
  fun refresh(project: Project) {
    ApplicationManager.getApplication().invokeLater({
      if (project.isDisposed) return@invokeLater
      PsiManager.getInstance(project).dropPsiCaches()
      FileContentUtil.reparseOpenedFiles()
      DaemonCodeAnalyzer.getInstance(project).restart(this)
    }, project.disposed)
  }

  companion object {
    const val ENGLISH = "English"

    fun of(project: Project): YaddaSettings = project.getService(YaddaSettings::class.java)

    /** The language of [project]'s features; English when there is no project or the setting names no language Yadda has. */
    fun vocabularyOf(project: Project?): Vocabulary = project?.takeIf { !it.isDisposed }?.let { of(it).vocabulary } ?: Vocabulary.ENGLISH
  }
}
