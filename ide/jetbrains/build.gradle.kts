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
    bundledModules("intellij.aqua.runners.playwright.js", "intellij.aqua.runners.js.core")

    testFramework(TestFrameworkType.Platform)
  }

  testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      sinceBuild = providers.gradleProperty("sinceBuild")
      // Cucumber.js internals are not a stable API: let the Marketplace verifier, not an
      // open-ended range, decide which newer builds are compatible.
      untilBuild = provider { null }
    }
  }

  pluginVerification {
    ides {
      val localIde = providers.gradleProperty("localIdePath")
      if (localIde.isPresent) local(file(localIde.get())) else recommended()
    }
  }
}

tasks.test {
  useJUnit()
}
