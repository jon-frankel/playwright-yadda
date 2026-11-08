import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
  id("org.jetbrains.kotlin.jvm") version "2.4.21"
  id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.jonfrankel"
version = providers.gradleProperty("pluginVersion").get()

repositories {
  mavenCentral()
  intellijPlatform { defaultRepositories() }
}

dependencies {
  intellijPlatform {
    val localIde = providers.gradleProperty("localIdePath")
    if (localIde.isPresent) {
      local(localIde.get())
    } else {
      webstorm(providers.gradleProperty("platformVersion").get())
    }

    // JavaScript: PSI for JS/TS step definitions. gherkin + cucumber-javascript: the step
    // index and Gherkin step references this plugin extends.
    bundledPlugins("JavaScript", "gherkin", "cucumber-javascript")

    // The Playwright run configuration: a module of the JavaScript plugin, not public API.
    bundledModules(
      "intellij.aqua.runners.playwright.js",
      "intellij.aqua.runners.js.core",
      "intellij.platform.smRunner",
      "intellij.platform.testRunner",
      "org.intellij.intelliLang",
    )

    // The plugin names no Markdown class and works with the Markdown plugin off; the tests need it on.
    testBundledPlugin("org.intellij.plugins.markdown")

    testFramework(TestFrameworkType.Platform)
  }

  testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
  pluginConfiguration {
    changeNotes = providers.environmentVariable("PLUGIN_CHANGE_NOTES")
      .orElse(providers.gradleProperty("pluginChangeNotes"))
    ideaVersion {
      sinceBuild = providers.gradleProperty("sinceBuild")
      // Cucumber.js internals are not a stable API: let the Marketplace verifier, not an
      // open-ended range, decide which newer builds are compatible.
      untilBuild = provider { null }
    }
  }

  signing {
    certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
    privateKey = providers.environmentVariable("PRIVATE_KEY")
    password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
  }

  publishing {
    token = providers.environmentVariable("PUBLISH_TOKEN")
    channels = providers.gradleProperty("pluginChannel")
      .map { listOf(it) }
      .orElse(listOf("default"))
  }

  pluginVerification {
    ides {
      val localIde = providers.gradleProperty("localIdePath")
      if (localIde.isPresent) {
        local(file(localIde.get()))
      } else {
        // Target the baseline WebStorm release in CI to avoid downloading multi-gigabyte
        // IDE distributions on every run. JetBrains Marketplace runs its own verifier against
        // newer builds automatically upon upload.
        create(IntelliJPlatformType.WebStorm, providers.gradleProperty("platformVersion").get())
      }
    }
  }
}

tasks.test {
  useJUnit()
}

val prebuiltPluginArchive = providers.gradleProperty("prebuiltPluginArchive")
if (prebuiltPluginArchive.isPresent) {
  tasks.signPlugin {
    archiveFile = layout.projectDirectory.file(prebuiltPluginArchive)
  }
  tasks.publishPlugin {
    setDependsOn(listOf(tasks.signPlugin))
    archiveFiles.setFrom(tasks.signPlugin.flatMap { it.signedArchiveFile })
  }
}
