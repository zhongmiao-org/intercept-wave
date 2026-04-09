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
        "//div[" +
            "@class='MyDialog' and (" +
            "contains(@title, 'Mock') or " +
            "contains(@title, 'Configuration') or " +
            ".//div[@visible_text='Group Management' or @visible_text='配置组管理']" +
            ")" +
            "]"
    )

    private val trustDialogLocator = byXpath(
        "//div[" +
            "@class='MyDialog' and (" +
            ".//div[@class='JButton' and (@text='Trust Project' or @text='信任项目')] or " +
            ".//div[@class='JButton' and contains(@text, 'Trust')] or " +
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

    private val onboardingDialogLocator = byXpath(
        "//div[" +
            "@class='MyDialog' and " +
            ".//div[(" +
            "@text='Meet the Islands Theme' or " +
            "@accessiblename='Meet the Islands Theme' or " +
            "@text.key='newUiOnboarding.dialog.title'" +
            ")]" +
            "]"
    )

    private val onboardingSkipLocator = byXpath(
        "//div[" +
            "(@class='ActionLink' or @class='JButton') and (" +
            "@text='Skip' or " +
            "@visible_text='Skip' or " +
            "@accessiblename='Skip' or " +
            "@text.key='dialog.skip'" +
            ")" +
            "]"
    )

    private val manageLicensesDialogLocator = byXpath(
        "//div[" +
            "@class='MyDialog' and (" +
            "@title='Manage Licenses' or " +
            "@accessiblename='Manage Licenses' or " +
            "@title.key='dialog.title.manage.licenses' or " +
            ".//div[contains(@visible_text, 'Non-commercial use')] or " +
            ".//div[contains(@visible_text, 'Paid license')] or " +
            ".//div[contains(@visible_text, 'Start trial')]" +
            ")" +
            "]"
    )

    private val nonCommercialUseLocator = byXpath(
        "//div[" +
            "(contains(@visible_text, 'Non-commercial use') or " +
            "@accessiblename='Non-commercial use' or " +
            "@action.key='radio.button.non.commercial.usage')" +
            "]"
    )

    private val projectFrameLocator = byXpath("//div[@class='IdeFrameImpl']")

    private val welcomeFrameLocator = byXpath(
        "//div[@class='FlatWelcomeFrame']"
    )

    private val recentProjectsLocator = byXpath(
        "//div[@accessiblename='Recent Projects']"
    )

    private inline fun <reified T : ComponentFixture> findAllSafe(
        locator: com.intellij.remoterobot.search.locators.Locator,
        attempts: Int = 3
    ): List<T> {
        repeat(attempts) { attempt ->
            val result = runCatching { remoteRobot.findAll<T>(locator) }
            if (result.isSuccess) return result.getOrThrow()
            runCatching { Thread.sleep(((attempt + 1) * 200L)) }
        }
        return emptyList()
    }

    private fun hasComponent(locator: com.intellij.remoterobot.search.locators.Locator): Boolean =
        findAllSafe<ComponentFixture>(locator).isNotEmpty()

    private fun clickVisibleButtonByText(vararg texts: String): Boolean = runCatching {
        texts.any { text ->
            val locator = byXpath(
                "//div[" +
                    "(@class='JButton' or @class='ActionButton' or @class='ActionButtonWithText') and (" +
                    "@text='$text' or " +
                    "@visible_text='$text' or " +
                    "@accessiblename='$text' or " +
                    "contains(@tooltiptext, '$text') or " +
                    "contains(@myaction, '$text')" +
                    ")" +
                    "]"
            )
            val candidates = findAllSafe<ComponentFixture>(locator)
            candidates.any { robotClick(it) }
        }
    }.getOrDefault(false)

    private fun visibleButtonsSnapshot(): String = "disabled"

    private fun waitForProjectUiReady(): Boolean =
        runCatching {
            waitFor(Duration.ofMinutes(8)) {
                clearBlockingDialogs()
                if (!hasComponent(projectFrameLocator) && hasComponent(welcomeFrameLocator)) {
                    runCatching { openProjectFromWelcomeIfNeeded() }
                }
                (hasComponent(projectFrameLocator) || hasComponent(toolWindowButtonLocator)) &&
                    !hasComponent(trustDialogLocator)
            }
            true
        }.getOrDefault(false)

    private fun showToolWindowFromIde(): Boolean {
        if (isToolWindowVisible()) return true
        if (!hasComponent(toolWindowButtonLocator)) return false
        return runCatching {
            clickOrThrow(remoteRobot.find(toolWindowButtonLocator), "InterceptWave tool window stripe button")
            true
        }.getOrDefault(false)
    }

    private fun acceptTrustDialogIfPresent() {
        if (!hasComponent(trustDialogLocator)) return

        step("Trust the UI test project if the trust dialog is shown") {
            val trustDialog = remoteRobot.find<CommonContainerFixture>(trustDialogLocator)

            runCatching { trustDialog.find<ComponentFixture>(trustAllCheckboxLocator) }

            clickOrThrow(
                trustDialog.find(trustProjectButtonLocator),
                "Trust Project button"
            )
        }

        waitFor(Duration.ofSeconds(15)) {
            !hasComponent(trustDialogLocator)
        }
    }

    private fun dismissOnboardingDialogIfPresent() {
        if (!hasComponent(onboardingDialogLocator)) return

        step("Dismiss the new UI onboarding dialog if it is shown") {
            clickOrThrow(
                remoteRobot.find<CommonContainerFixture>(onboardingDialogLocator)
                    .find(onboardingSkipLocator),
                "onboarding Skip button"
            )
        }

        waitFor(Duration.ofSeconds(15)) {
            !hasComponent(onboardingDialogLocator)
        }
    }

    private fun dismissManageLicensesDialogIfPresent() {
        if (!hasComponent(manageLicensesDialogLocator)) return

        step("Dismiss the manage licenses dialog if it is shown") {
            val dialog = remoteRobot.find<CommonContainerFixture>(manageLicensesDialogLocator)

            runCatching {
                clickOrThrow(dialog.find(nonCommercialUseLocator), "Non-commercial use option")
            }

            val actionCandidates = listOf("Use for Free", "Continue", "OK", "Activate", "继续", "确定")
            actionCandidates.forEach { text ->
                runCatching {
                    clickOrThrow(
                        dialog.find<ComponentFixture>(
                            byXpath(
                                "//div[" +
                                    "(@class='JButton' or @class='ActionButton' or @class='ActionButtonWithText') and (" +
                                    "@text='$text' or @visible_text='$text' or @accessiblename='$text'" +
                                    ")" +
                                    "]"
                            )
                        ),
                        "license dialog action $text"
                    )
                }
            }
        }

        waitFor(Duration.ofSeconds(30)) {
            !hasComponent(manageLicensesDialogLocator)
        }
    }

    private fun clearBlockingDialogs() {
        acceptTrustDialogIfPresent()
        dismissOnboardingDialogIfPresent()
        dismissManageLicensesDialogIfPresent()
    }

    private fun openProjectFromWelcomeIfNeeded() {
        if (hasComponent(projectFrameLocator)) return
        if (hasComponent(projectFrameLocator) || hasComponent(trustDialogLocator)) return
        if (!hasComponent(welcomeFrameLocator)) return

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

    private fun robotClick(component: ComponentFixture): Boolean =
        runCatching {
            component.click()
            true
        }.getOrDefault(false)

    private fun clickOrThrow(component: ComponentFixture, description: String) {
        check(robotClick(component)) {
            "Failed to click $description via Swing dispatch"
        }
    }

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

    private fun waitForConfigDialog() {
        waitFor(Duration.ofSeconds(20)) {
            findAllSafe<CommonContainerFixture>(configDialogLocator).isNotEmpty()
        }
    }

    private fun openConfigDialog() {
        clearBlockingDialogs()
        if (findAllSafe<CommonContainerFixture>(configDialogLocator).isNotEmpty()) return

        waitFor(Duration.ofSeconds(45)) {
            clearBlockingDialogs()
            if (!isToolWindowVisible()) {
                showToolWindowFromIde()
            }
            findAllSafe<JButtonFixture>(configButtonLocator).isNotEmpty() || isToolWindowVisible()
        }

        val configButtons = findAllSafe<JButtonFixture>(configButtonLocator)
        val openedByFixture = configButtons.any { robotClick(it) }
        val openedByGlobalSearch = if (!openedByFixture) clickVisibleButtonByText("配置", "Configure") else false
        check(openedByFixture || openedByGlobalSearch) {
            "Failed to click Configure button via Swing dispatch. Visible snapshot:\n${visibleButtonsSnapshot()}"
        }

        waitForConfigDialog()
    }

    private fun isToolWindowVisible(): Boolean =
        hasComponent(configButtonLocator) ||
            hasComponent(startAllButtonLocator) ||
            hasComponent(byXpath("//div[@class='JButton' and (@text='停止所有' or @text='Stop All')]"))

    private fun ensureToolWindowOpen() {
        clearBlockingDialogs()
        if (isToolWindowVisible()) return

        step("Ensure Intercept Wave tool window is open") {
            val stripeExists = runCatching {
                waitFor(Duration.ofSeconds(30)) {
                    clearBlockingDialogs()
                    showToolWindowFromIde()
                    hasComponent(toolWindowButtonLocator) || isToolWindowVisible()
                }
                true
            }.getOrDefault(false)

            if (!isToolWindowVisible() && stripeExists && hasComponent(toolWindowButtonLocator)) {
                clickOrThrow(remoteRobot.find(toolWindowButtonLocator), "InterceptWave tool window stripe button")
            }

            if (!isToolWindowVisible()) {
                showToolWindowFromIde()
            }
        }

        waitFor(Duration.ofMinutes(2)) {
            clearBlockingDialogs()
            isToolWindowVisible()
        }
    }

    @BeforeEach
    fun setUp() {
        applyUiFixtureConfig()
        clearBlockingDialogs()
        openProjectFromWelcomeIfNeeded()
        clearBlockingDialogs()
        waitForProjectUiReady()
        clearBlockingDialogs()
    }

    @AfterEach
    fun tearDown() {
        // Clean up: close any open dialogs
        step("Close any open dialogs") {
            try {
                remoteRobot.find<CommonContainerFixture>(byXpath("//div[@class='MyDialog']")).apply {
                    clickOrThrow(
                        find(byXpath("//div[@text='Cancel']")),
                        "dialog Cancel button"
                    )
                }
            } catch (_: Exception) {
                // No dialog open, that's fine
            }
        }
    }

    @Test
    fun `test tool window is available`() {
        ensureToolWindowOpen()

        step("Verify tool window is opened") {
            // Wait for tool window content to appear
            waitFor(Duration.ofSeconds(30)) {
                isToolWindowVisible()
            }
        }
    }

    @Test
    fun `test start all button is visible`() {
        ensureToolWindowOpen()

        step("Verify Start All button exists") {
            waitFor(Duration.ofSeconds(10)) {
                findAllSafe<JButtonFixture>(startAllButtonLocator).isNotEmpty()
            }
        }
    }

    @Test
    fun `test configuration dialog can be opened`() {
        ensureToolWindowOpen()

        step("Click configuration button") {
            openConfigDialog()
        }

        step("Verify configuration dialog is opened") {
            waitForConfigDialog()
        }

        step("Close dialog") {
            configDialog().apply {
                val cancelButton = find<JButtonFixture>(byXpath("//div[@class='JButton' and (@text='Cancel' or @text='取消')]"))
                clickOrThrow(cancelButton, "config dialog Cancel button")
            }
        }
    }

    /**
     * Example of a more complex test:
     * This test would verify the entire workflow of adding a mock API
     */
    @Test
    fun `test add new proxy group workflow`() {
        ensureToolWindowOpen()

        step("Open configuration dialog") {
            openConfigDialog()
        }

        step("Click add group button") {
            waitForConfigDialog()
            val addGroupButton = configDialog().find<JButtonFixture>(addGroupButtonLocator)
            val clicked = robotClick(addGroupButton) || clickVisibleButtonByText("新增配置组", "Add Group")
            check(clicked) { "Failed to click Add Group button via Swing dispatch" }
        }

        step("Verify new group is created") {
            waitFor(Duration.ofSeconds(10)) {
                hasConfigGroup("Group 6") || hasConfigGroup("配置组 6")
            }
        }

        step("Close dialog without saving") {
            configDialog().apply {
                val cancelButton = find<JButtonFixture>(byXpath("//div[@class='JButton' and (@text='Cancel' or @text='取消')]"))
                clickOrThrow(cancelButton, "config dialog Cancel button")
            }
        }
    }
}
