package io.github.jonfrankel.playwrightyadda.yadda

import com.google.gson.JsonParser
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether a file belongs to a project that runs its features with playwright-yadda: one of the
 * `package.json` files above it depends on the package. Yadda is a dialect of Gherkin, so a
 * `.feature` file is only Yadda if it is run by Yadda.
 */
object PlaywrightYaddaProjects {
  private const val PACKAGE = "playwright-yadda"
  private val DEPENDENCY_SECTIONS = listOf("dependencies", "devDependencies", "peerDependencies", "optionalDependencies")

  private class Cached(val stamp: Long, val dependsOnPackage: Boolean)

  private val cache = ConcurrentHashMap<String, Cached>()

  fun isYaddaFeature(file: VirtualFile): Boolean {
    var dir = file.parent
    while (dir != null) {
      val manifest = dir.findChild("package.json")
      if (manifest != null && !manifest.isDirectory && dependsOnPackage(manifest)) return true
      dir = dir.parent
    }
    return false
  }

  private fun dependsOnPackage(manifest: VirtualFile): Boolean {
    val cached = cache[manifest.url]
    if (cached != null && cached.stamp == manifest.modificationStamp) return cached.dependsOnPackage
    val depends = readDependsOnPackage(manifest)
    cache[manifest.url] = Cached(manifest.modificationStamp, depends)
    return depends
  }

  private fun readDependsOnPackage(manifest: VirtualFile): Boolean =
    try {
      val root = JsonParser.parseString(VfsUtilCore.loadText(manifest)).asJsonObject
      DEPENDENCY_SECTIONS.any { section -> root.getAsJsonObject(section)?.has(PACKAGE) == true }
    } catch (_: Exception) {
      false
    }
}
