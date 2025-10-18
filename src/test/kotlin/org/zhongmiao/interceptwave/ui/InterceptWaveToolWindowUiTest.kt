package org.zhongmiao.interceptwave.ui

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * UI tests for Intercept Wave tool window
 *
 * These tests verify the UI behavior of the plugin, including:
 * - Tool window visibility and layout
 * - Button clicks and interactions
 * - Configuration dialog opening
 * - Server start/stop operations
 *
 * Note: These tests require the Robot Server Plugin to be installed
 * and the IDE to be running with `./gradlew runIdeForUiTests`
 */
class InterceptWaveToolWindowUiTest {

    private val remoteRobot = RemoteRobot("http://127.0.0.1:8082")

    @BeforeEach
    fun setUp() {
        // Wait for IDE to be ready
        waitFor(Duration.ofMinutes(2)) {
            runCatching { remoteRobot.callJs<Boolean>("true") }.isSuccess
        }
    }

    @AfterEach
    fun tearDown() {
        // Clean up: close any open dialogs
        step("Close any open dialogs") {
            try {
                remoteRobot.find<CommonContainerFixture>(byXpath("//div[@class='MyDialog']")).apply {
                    find<JButtonFixture>(byXpath("//div[@text='Cancel']")).click()
                }
            } catch (_: Exception) {
                // No dialog open, that's fine
            }
        }
    }

    @Test
    fun `test tool window is available`() = with(remoteRobot) {
        step("Open Intercept Wave tool window") {
            // Find and click the tool window stripe button
            // Note: Adjust the locator based on your actual plugin name
            find<ComponentFixture>(
                byXpath("//div[@tooltiptext.key='plugin.name']")
            ).click()
        }

        step("Verify tool window is opened") {
            // Wait for tool window content to appear
            waitFor(Duration.ofSeconds(10)) {
                findAll<ComponentFixture>(
                    byXpath("//div[contains(@text, 'Intercept Wave')]")
                ).isNotEmpty()
            }
        }
    }

    @Test
    fun `test start all button is visible`() = with(remoteRobot) {
        step("Open tool window") {
            find<ComponentFixture>(
                byXpath("//div[@tooltiptext.key='plugin.name']")
            ).click()
        }

        step("Verify Start All button exists") {
            waitFor(Duration.ofSeconds(10)) {
                findAll<JButtonFixture>(
                    byXpath("//div[@class='JButton' and @text='启动所有']")
                ).isNotEmpty()
            }
        }
    }

    @Test
    fun `test configuration dialog can be opened`() {
        with(remoteRobot) {
            step("Open tool window") {
                find<ComponentFixture>(
                    byXpath("//div[@tooltiptext.key='plugin.name']")
                ).click()
            }

            step("Click configuration button") {
                find<JButtonFixture>(
                    byXpath("//div[@class='JButton' and @text='配置']")
                ).click()
            }

            step("Verify configuration dialog is opened") {
                waitFor(Duration.ofSeconds(10)) {
                    findAll<CommonContainerFixture>(
                        byXpath("//div[@class='MyDialog' and contains(@title, 'Mock')]")
                    ).isNotEmpty()
                }
            }

            step("Close dialog") {
                find<CommonContainerFixture>(
                    byXpath("//div[@class='MyDialog']")
                ).apply {
                    find<JButtonFixture>(byXpath("//div[@text='Cancel']")).click()
                }
            }
        }
    }

    /**
     * Example of a more complex test:
     * This test would verify the entire workflow of adding a mock API
     */
    @Test
    fun `test add new proxy group workflow`() {
        with(remoteRobot) {
            step("Open tool window") {
                find<ComponentFixture>(
                    byXpath("//div[@tooltiptext.key='plugin.name']")
                ).click()
            }

            step("Open configuration dialog") {
                find<JButtonFixture>(
                    byXpath("//div[@class='JButton' and @text='配置']")
                ).click()
            }

            step("Click add group button") {
                // Find the "Add Group" button in the dialog
                find<JButtonFixture>(
                    byXpath("//div[@class='JButton' and contains(@text, '新增配置组')]")
                ).click()
            }

            step("Verify new tab is created") {
                // Check that a new tab appeared
                waitFor(Duration.ofSeconds(5)) {
                    findAll<ComponentFixture>(
                        byXpath("//div[contains(@text, '配置组')]")
                    ).size > 1
                }
            }

            step("Close dialog without saving") {
                find<CommonContainerFixture>(
                    byXpath("//div[@class='MyDialog']")
                ).apply {
                    find<JButtonFixture>(byXpath("//div[@text='Cancel']")).click()
                }
            }
        }
    }
}