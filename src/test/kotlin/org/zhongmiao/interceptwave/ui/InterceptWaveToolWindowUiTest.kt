package org.zhongmiao.interceptwave.ui

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
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
    private val uiProjectName = System.getProperty("intercept.wave.ui.projectName")
        ?: error("Missing system property: intercept.wave.ui.projectName")
    private val workspaceProjectName = File(System.getProperty("user.dir")).name

    private val toolWindowButtonLocator = byXpath(
        "//div[" +
            "@class='SquareStripeButton' and (" +
            "@accessiblename='InterceptWave' or " +
            "@tooltiptext='InterceptWave' or " +
            "@tooltiptext.key='toolwindow.stripe.InterceptWave'" +
            ")" +
            "]"
    )

    private val startAllButtonLocator = byXpath(
        "//div[@class='JButton' and (@text='启动所有' or @text='Start All')]"
    )

    private val configButtonLocator = byXpath(
        "//div[@class='JButton' and (@text='配置' or @text='Configure')]"
    )

    private val addGroupButtonLocator = byXpath(
        "//div[@class='JButton' and (@text='新增配置组' or @text='Add Group')]"
    )

    private val configDialogLocator = byXpath(
        "//div[@class='MyDialog' and contains(@title, 'Mock')]"
    )

    private val trustDialogLocator = byXpath(
        "//div[" +
            "@class='MyDialog' and (" +
            ".//div[@class='JButton' and (@text='Trust Project' or @text='信任项目')] or " +
            ".//div[contains(@visible_text, 'Trust and Open Project')] or " +
            ".//div[contains(@visible_text, 'Trust Project')]" +
            ")" +
            "]"
    )

    private val trustProjectButtonLocator = byXpath(
        "//div[@class='JButton' and (" +
            "@text='Trust Project' or " +
            "@text='信任项目' or " +
            "contains(@text, 'Trust ') or " +
            "contains(@visible_text, 'Trust ')" +
            ")]"
    )

    private val trustAllCheckboxLocator = byXpath(
        "//div[" +
            "@class='JBCheckBox' and (" +
            "contains(@text, \"Trust all projects\") or " +
            "contains(@text, \"信任\")" +
            ")" +
            "]"
    )

    private val projectFrameLocator = byXpath("//div[@class='IdeFrameImpl']")

    private val welcomeFrameLocator = byXpath(
        "//div[@class='FlatWelcomeFrame']"
    )

    private val recentProjectsLocator = byXpath(
        "//div[@accessiblename='Recent Projects']"
    )

    private fun hasComponent(locator: com.intellij.remoterobot.search.locators.Locator): Boolean =
        remoteRobot.findAll<ComponentFixture>(locator).isNotEmpty()

    private fun waitForProjectUiReady() {
        waitFor(Duration.ofMinutes(6)) {
            runCatching { remoteRobot.callJs<Boolean>("true") }.getOrDefault(false) &&
                hasComponent(projectFrameLocator) &&
                !hasComponent(trustDialogLocator)
        }
    }

    private fun waitForToolWindowButton() {
        waitFor(Duration.ofMinutes(3)) {
            hasComponent(toolWindowButtonLocator)
        }
    }

    private fun acceptTrustDialogIfPresent() {
        if (!hasComponent(trustDialogLocator)) return

        step("Trust the UI test project if the trust dialog is shown") {
            val trustDialog = remoteRobot.find<CommonContainerFixture>(trustDialogLocator)

            runCatching {
                val trustAllCheckbox = trustDialog.find<ComponentFixture>(trustAllCheckboxLocator)
                val isSelected = trustAllCheckbox.callJs<Boolean>("component.isSelected()", true)
                if (!isSelected) {
                    trustAllCheckbox.click()
                }
            }

            trustDialog.find<JButtonFixture>(trustProjectButtonLocator).click()
        }

        waitFor(Duration.ofSeconds(15)) {
            !hasComponent(trustDialogLocator)
        }
    }

    private fun openProjectFromWelcomeIfNeeded() {
        if (hasComponent(projectFrameLocator)) return

        waitFor(Duration.ofMinutes(1)) {
            hasComponent(welcomeFrameLocator) || hasComponent(projectFrameLocator) || hasComponent(trustDialogLocator)
        }

        if (hasComponent(projectFrameLocator) || hasComponent(trustDialogLocator)) return

        step("Open a visible recent project from the Welcome screen") {
            val recentProjects = remoteRobot.find<JTreeFixture>(recentProjectsLocator)

            val openedKnownProject = runCatching {
                recentProjects.doubleClickRowWithText(uiProjectName, fullMatch = false)
                true
            }.recoverCatching {
                recentProjects.doubleClickRowWithText(workspaceProjectName, fullMatch = false)
                true
            }.getOrDefault(false)

            if (!openedKnownProject) {
                recentProjects.clickRow(0)
                remoteRobot.keyboard { enter() }
            }
        }
    }

    private fun applyUiFixtureConfig() {
        val projectDir = System.getProperty("intercept.wave.ui.projectDir")
            ?: error("Missing system property: intercept.wave.ui.projectDir")
        val fixtureResource = System.getProperty("intercept.wave.ui.fixtureResource")
            ?: error("Missing system property: intercept.wave.ui.fixtureResource")
        val fixtureContent = checkNotNull(javaClass.getResource(fixtureResource)) {
            "Missing UI fixture resource: $fixtureResource"
        }.readText()

        listOf(
            File(projectDir),
            File(System.getProperty("user.dir"))
        ).distinctBy { it.absolutePath }
            .forEach { targetDir ->
                val configDir = File(targetDir, ".intercept-wave").apply { mkdirs() }
                File(configDir, "config.json").writeText(fixtureContent)
            }
    }

    private fun configDialog(): CommonContainerFixture =
        remoteRobot.find(configDialogLocator)

    private fun hasConfigGroup(groupName: String): Boolean =
        configDialog().findAll<ComponentFixture>(
            byXpath(
                "//div[" +
                    "(@class='JLabel' or @class='JBTextField') and " +
                    "(" +
                    "@visible_text='$groupName' or " +
                    "@text='$groupName' or " +
                    "@accessiblename='$groupName (:8893)'" +
                    ")" +
                    "]"
            )
        ).isNotEmpty()

    private fun isToolWindowVisible(): Boolean =
        remoteRobot.findAll<JButtonFixture>(configButtonLocator).isNotEmpty() ||
            remoteRobot.findAll<JButtonFixture>(startAllButtonLocator).isNotEmpty() ||
            remoteRobot.findAll<JButtonFixture>(
                byXpath("//div[@class='JButton' and (@text='停止所有' or @text='Stop All')]")
            ).isNotEmpty()

    private fun ensureToolWindowOpen() {
        if (isToolWindowVisible()) return

        waitForToolWindowButton()

        step("Ensure Intercept Wave tool window is open") {
            remoteRobot.find<ComponentFixture>(toolWindowButtonLocator).click()
        }

        waitFor(Duration.ofMinutes(2)) {
            isToolWindowVisible()
        }
    }

    @BeforeEach
    fun setUp() {
        applyUiFixtureConfig()
        acceptTrustDialogIfPresent()
        openProjectFromWelcomeIfNeeded()
        acceptTrustDialogIfPresent()
        waitForProjectUiReady()
        acceptTrustDialogIfPresent()
        waitForToolWindowButton()
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
        ensureToolWindowOpen()

        step("Verify tool window is opened") {
            // Wait for tool window content to appear
            waitFor(Duration.ofSeconds(30)) {
                isToolWindowVisible()
            }
        }
    }

    @Test
    fun `test start all button is visible`() = with(remoteRobot) {
        ensureToolWindowOpen()

        step("Verify Start All button exists") {
            waitFor(Duration.ofSeconds(10)) {
                findAll<JButtonFixture>(startAllButtonLocator).isNotEmpty()
            }
        }
    }

    @Test
    fun `test configuration dialog can be opened`() {
        with(remoteRobot) {
            ensureToolWindowOpen()

            step("Click configuration button") {
                find<JButtonFixture>(configButtonLocator).click()
            }

            step("Verify configuration dialog is opened") {
                waitFor(Duration.ofSeconds(10)) {
                    findAll<CommonContainerFixture>(configDialogLocator).isNotEmpty()
                }
            }

            step("Close dialog") {
                configDialog().apply {
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
            ensureToolWindowOpen()

            step("Open configuration dialog") {
                find<JButtonFixture>(configButtonLocator).click()
            }

            step("Click add group button") {
                configDialog().find<JButtonFixture>(addGroupButtonLocator).click()
            }

            step("Verify new group is created") {
                waitFor(Duration.ofSeconds(10)) {
                    hasConfigGroup("Group 6") || hasConfigGroup("配置组 6")
                }
            }

            step("Close dialog without saving") {
                configDialog().apply {
                    find<JButtonFixture>(byXpath("//div[@text='Cancel']")).click()
                }
            }
        }
    }
}
