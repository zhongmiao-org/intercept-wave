package org.zhongmiao.interceptwave.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for ConsoleService
 *
 * Note: Some methods like showConsole() interact with UI components that are difficult to test
 * in unit tests. These tests focus on the methods that can be tested without full UI context.
 */
class ConsoleServiceTest : BasePlatformTestCase() {

    private lateinit var consoleService: ConsoleService

    override fun setUp() {
        super.setUp()
        consoleService = project.getService(ConsoleService::class.java)
    }

    fun `test consoleService is available`() {
        assertNotNull(consoleService)
    }

    fun `test printInfo does not throw exception`() {
        // Should not throw
        consoleService.printInfo("Test info message")
        consoleService.printInfo("Another test message")
    }

    fun `test printSuccess does not throw exception`() {
        consoleService.printSuccess("Operation completed successfully")
    }

    fun `test printWarning does not throw exception`() {
        consoleService.printWarning("This is a warning")
    }

    fun `test printError does not throw exception`() {
        consoleService.printError("An error occurred")
    }

    fun `test printDebug does not throw exception`() {
        consoleService.printDebug("Debug information")
    }

    fun `test printSeparator does not throw exception`() {
        consoleService.printSeparator()
    }

    fun `test clear does not throw exception`() {
        // Should not throw even if console is not initialized
        consoleService.clear()
    }

    fun `test multiple print calls`() {
        consoleService.printInfo("Starting operation")
        consoleService.printDebug("Initializing...")
        consoleService.printSuccess("Initialization complete")
        consoleService.printWarning("Low memory warning")
        consoleService.printError("Connection failed")
        consoleService.printSeparator()
    }

    fun `test print with empty string`() {
        consoleService.printInfo("")
        consoleService.printSuccess("")
        consoleService.printWarning("")
        consoleService.printError("")
        consoleService.printDebug("")
    }

    fun `test print with long message`() {
        val longMessage = "A".repeat(1000)
        consoleService.printInfo(longMessage)
        consoleService.printSuccess(longMessage)
        consoleService.printWarning(longMessage)
        consoleService.printError(longMessage)
        consoleService.printDebug(longMessage)
    }

    fun `test print with special characters`() {
        consoleService.printInfo("Special: !@#$%^&*()_+-=[]{}|;':\",./<>?")
        consoleService.printInfo("Unicode: 你好世界 🎉 ✓ ✗ ⚠")
        consoleService.printInfo("Newlines:\nLine 1\nLine 2")
        consoleService.printInfo("Tabs:\tColumn 1\tColumn 2")
    }

    fun `test print with null-like strings`() {
        consoleService.printInfo("null")
        consoleService.printInfo("undefined")
        consoleService.printInfo("")
    }

    fun `test consecutive clear calls`() {
        consoleService.clear()
        consoleService.clear()
        consoleService.clear()
    }

    fun `test print after clear`() {
        consoleService.printInfo("Before clear")
        consoleService.clear()
        consoleService.printInfo("After clear")
    }

    fun `test mixed print and clear operations`() {
        consoleService.printInfo("Message 1")
        consoleService.printSuccess("Message 2")
        consoleService.clear()
        consoleService.printWarning("Message 3")
        consoleService.printSeparator()
        consoleService.printError("Message 4")
        consoleService.clear()
    }

    fun `test print with multiline messages`() {
        val multiline = """
            Line 1
            Line 2
            Line 3
        """.trimIndent()

        consoleService.printInfo(multiline)
        consoleService.printError(multiline)
    }

    fun `test rapid successive prints`() {
        repeat(100) { i ->
            consoleService.printInfo("Message $i")
        }
    }

    fun `test all print types in sequence`() {
        consoleService.printSeparator()
        consoleService.printInfo("Information message")
        consoleService.printSuccess("Success message")
        consoleService.printWarning("Warning message")
        consoleService.printError("Error message")
        consoleService.printDebug("Debug message")
        consoleService.printSeparator()
    }

    fun `test print with JSON-like content`() {
        val jsonLike = """{"status": "ok", "data": [1, 2, 3]}"""
        consoleService.printInfo(jsonLike)
        consoleService.printDebug(jsonLike)
    }

    fun `test print with XML-like content`() {
        val xmlLike = "<response><status>ok</status></response>"
        consoleService.printInfo(xmlLike)
    }

    fun `test print with escaped characters`() {
        consoleService.printInfo("Escaped: \\n \\t \\r")
        consoleService.printInfo("Quotes: \"double\" 'single'")
        consoleService.printInfo("Backslash: C:\\Users\\test")
    }
}