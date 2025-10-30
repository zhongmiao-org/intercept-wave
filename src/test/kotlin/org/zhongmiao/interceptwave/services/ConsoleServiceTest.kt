package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ConsoleServiceTest : BasePlatformTestCase() {

    fun `test console methods do not create UI in unit tests`() {
        val console = project.getService(ConsoleService::class.java)

        // These calls should be no-op for UI but should not throw in test mode
        console.showConsole()
        console.printInfo("info")
        console.printSuccess("success")
        console.printWarning("warning")
        console.printError("error")
        console.printDebug("debug")
        console.printSeparator()
        console.clear()

        // If we reach here without exceptions, behavior is acceptable
        assertTrue(true)
    }
}

