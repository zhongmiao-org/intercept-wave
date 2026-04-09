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

    private fun showToolWindowFromIde(): Boolean =
        runCatching {
            remoteRobot.callJs<Boolean>(
                """
                importPackage(Packages.com.intellij.openapi.project)
                importPackage(Packages.com.intellij.openapi.wm)

                var projects = ProjectManager.getInstance().getOpenProjects()
                if (projects == null || projects.length === 0) {
                    false
                } else {
                    var toolWindow = ToolWindowManager.getInstance(projects[0]).getToolWindow("InterceptWave")
                    if (toolWindow == null) {
                        false
                    } else {
                        toolWindow.show(null)
                        toolWindow.activate(null)
                        true
                    }
                }
                """.trimIndent(),
                true
            )
        }.getOrDefault(false)

    private fun acceptTrustDialogIfPresent() {
        if (!hasComponent(trustDialogLocator)) return

        step("Trust the UI test project if the trust dialog is shown") {
            val trustDialog = remoteRobot.find<CommonContainerFixture>(trustDialogLocator)

            runCatching {
                val trustAllCheckbox = trustDialog.find<ComponentFixture>(trustAllCheckboxLocator)
                val isSelected = trustAllCheckbox.callJs<Boolean>("component.isSelected()", true)
                if (!isSelected) {
                    swingClick(trustAllCheckbox)
                }
            }

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
                swingClick(dialog.find(nonCommercialUseLocator))
            }

            runCatching {
                dialog.callJs<Boolean>(
                    """
                    function textOf(c) {
                      try {
                        var text = c.getText ? c.getText() : null
                        if (text != null) return String(text)
                      } catch (e) {}
                      try {
                        var ac = c.getAccessibleContext ? c.getAccessibleContext() : null
                        var name = ac != null ? ac.getAccessibleName() : null
                        if (name != null) return String(name)
                      } catch (e) {}
                      return ""
                    }

                    function clickMatching(root, patterns) {
                      if (root == null) return false
                      var stack = [root]
                      while (stack.length > 0) {
                        var current = stack.pop()
                        var text = textOf(current)
                        if (current instanceof javax.swing.AbstractButton) {
                          for (var i = 0; i < patterns.length; i++) {
                            if (text.indexOf(patterns[i]) >= 0) {
                              current.doClick()
                              return true
                            }
                          }
                        }
                        if (current.getComponents) {
                          var children = current.getComponents()
                          for (var j = 0; j < children.length; j++) {
                            stack.push(children[j])
                          }
                        }
                      }
                      return false
                    }

                    clickMatching(component, ['Non-commercial use', 'Non commercial use', 'Free']) ||
                      clickMatching(component, ['Use for Free', 'Continue', 'OK', 'Activate'])
                    """.trimIndent(),
                    true
                )
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

    private fun swingClick(component: ComponentFixture): Boolean =
        runCatching {
            component.callJs<Boolean>(
                """
                if (component == null) {
                  false
                } else if (component instanceof javax.swing.AbstractButton) {
                  component.doClick()
                  true
                } else {
                  component.dispatchEvent(new java.awt.event.MouseEvent(
                    component,
                    java.awt.event.MouseEvent.MOUSE_PRESSED,
                    java.lang.System.currentTimeMillis(),
                    0,
                    Math.max(1, component.getWidth() / 2),
                    Math.max(1, component.getHeight() / 2),
                    1,
                    false,
                    java.awt.event.MouseEvent.BUTTON1
                  ))
                  component.dispatchEvent(new java.awt.event.MouseEvent(
                    component,
                    java.awt.event.MouseEvent.MOUSE_RELEASED,
                    java.lang.System.currentTimeMillis(),
                    0,
                    Math.max(1, component.getWidth() / 2),
                    Math.max(1, component.getHeight() / 2),
                    1,
                    false,
                    java.awt.event.MouseEvent.BUTTON1
                  ))
                  component.dispatchEvent(new java.awt.event.MouseEvent(
                    component,
                    java.awt.event.MouseEvent.MOUSE_CLICKED,
                    java.lang.System.currentTimeMillis(),
                    0,
                    Math.max(1, component.getWidth() / 2),
                    Math.max(1, component.getHeight() / 2),
                    1,
                    false,
                    java.awt.event.MouseEvent.BUTTON1
                  ))
                  true
                }
                """.trimIndent(),
                true
            )
        }.getOrDefault(false)

    private fun clickOrThrow(component: ComponentFixture, description: String) {
        check(swingClick(component)) {
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
            remoteRobot.findAll<CommonContainerFixture>(configDialogLocator).isNotEmpty()
        }
    }

    private fun isToolWindowVisible(): Boolean =
        remoteRobot.findAll<JButtonFixture>(configButtonLocator).isNotEmpty() ||
            remoteRobot.findAll<JButtonFixture>(startAllButtonLocator).isNotEmpty() ||
            remoteRobot.findAll<JButtonFixture>(
                byXpath("//div[@class='JButton' and (@text='停止所有' or @text='Stop All')]")
            ).isNotEmpty()

    private fun ensureToolWindowOpen() {
        clearBlockingDialogs()
        if (isToolWindowVisible()) return

        waitForToolWindowButton()
        clearBlockingDialogs()

        step("Ensure Intercept Wave tool window is open") {
            val openedViaIde = showToolWindowFromIde()
            if (!openedViaIde) {
                clickOrThrow(
                    remoteRobot.find(toolWindowButtonLocator),
                    "InterceptWave tool window stripe button"
                )
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
        waitForToolWindowButton()
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
                val configButton = find<JButtonFixture>(configButtonLocator)
                clickOrThrow(configButton, "Configure button")
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
                val configButton = find<JButtonFixture>(configButtonLocator)
                clickOrThrow(configButton, "Configure button")
            }

            step("Click add group button") {
                waitForConfigDialog()
                val addGroupButton = configDialog().find<JButtonFixture>(addGroupButtonLocator)
                clickOrThrow(addGroupButton, "Add Group button")
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
}
