package io.github.jonfrankel.playwrightyadda.run

import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy

/**
 * Listens to test runner events and equips test proxies with [YaddaTestLocator].
 *
 * When Playwright tests run, double-clicking a test node in WebStorm's test tree normally navigates
 * to the spec file. This listener installs [YaddaTestLocator] on the proxies so that double-clicking
 * a scenario or feature jumps to its definition in the `.feature` or `.md` file.
 */
class YaddaTestStatusListener : SMTRunnerEventsListener {
  override fun onTestingStarted(root: SMTestProxy.SMRootTestProxy) {
    installTree(root)
  }

  override fun onTestingFinished(root: SMTestProxy.SMRootTestProxy) {
    installTree(root)
  }

  override fun onBuildTreeEnded(root: SMTestProxy.SMRootTestProxy) {
    installTree(root)
  }

  override fun onTestsCountInSuite(count: Int) {}

  override fun onCustomProgressTestsCategory(categoryName: String?, testCount: Int) {}

  override fun onCustomProgressTestStarted() {}

  override fun onCustomProgressTestFailed() {}

  override fun onCustomProgressTestFinished() {}

  override fun onTestStarted(test: SMTestProxy) {
    install(test)
  }

  override fun onTestFinished(test: SMTestProxy) {
    install(test)
  }

  override fun onTestFailed(test: SMTestProxy) {
    install(test)
  }

  override fun onTestIgnored(test: SMTestProxy) {
    install(test)
  }

  override fun onSuiteFinished(suite: SMTestProxy) {
    install(suite)
  }

  override fun onSuiteStarted(suite: SMTestProxy) {
    install(suite)
  }

  override fun onSuiteTreeNodeAdded(proxy: SMTestProxy) {
    install(proxy)
  }

  override fun onSuiteTreeStarted(suite: SMTestProxy) {
    install(suite)
  }

  companion object {
    fun install(proxy: SMTestProxy) {
      var current: SMTestProxy? = proxy
      while (current != null) {
        val existing = current.locator
        if (existing is YaddaTestLocator) break
        current.setLocator(YaddaTestLocator(current, existing))
        current = current.parent
      }
    }

    fun installTree(proxy: SMTestProxy) {
      install(proxy)
      for (child in proxy.children) {
        installTree(child)
      }
    }
  }
}
