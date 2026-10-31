package io.github.jonfrankel.playwrightyadda.steps

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry

/**
 * Offers [YaddaStepRenameHandler] alone for a step of a Yadda feature.
 *
 * When more than one rename handler is available the IDE asks which to use, and the other one here is the Cucumber
 * plugin's own, which can't rename these steps. The platform lets a plugin replace this service (it is registered
 * `open`), and this adds nothing to it but that choice.
 */
class YaddaRenameHandlerRegistry : RenameHandlerRegistry() {
  override fun getRenameHandlers(dataContext: DataContext): List<RenameHandler> {
    val handlers = super.getRenameHandlers(dataContext)
    val ours = handlers.firstOrNull { it is YaddaStepRenameHandler } ?: return handlers
    return listOf(ours)
  }
}
