# UI Tests for Intercept Wave Plugin

This directory contains UI tests for the Intercept Wave plugin using IntelliJ Remote Robot.

## Overview

UI tests verify the user interface behavior of the plugin, including:
- Tool window interactions
- Dialog operations
- Button clicks
- Configuration workflows
- Server start/stop operations

## Test Architecture

### Separation of Concerns

- **Unit Tests** (`src/test/kotlin/.../services/`, `src/test/kotlin/.../model/`)
  - Test business logic and data models
  - Fast execution (milliseconds)
  - Run on every build
  - Can run in headless CI environment

- **UI Tests** (`src/test/kotlin/.../ui/`)
  - Test user interface interactions
  - Slower execution (seconds to minutes)
  - Run separately via `./gradlew testUi`
  - Require a running IDE instance with robot-server plugin

## Running UI Tests

### Prerequisites

Before running UI tests for the first time, you need to install the Robot Server Plugin:

1. Download the robot-server plugin from [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/17153-robot-server-plugin)
2. Or install it manually in your IDE: Settings → Plugins → Search for "Robot Server Plugin"
3. The plugin will automatically be available when running `runIdeForUiTests`

### Locally

1. **Start IDE with robot-server:**
   ```bash
   ./gradlew runIdeForUiTests
   ```
   This will start IntelliJ IDEA with your plugin. The robot-server plugin will listen on port 8082.

   **Note**: On first run, you may need to manually install the Robot Server Plugin in the started IDE.

2. **Run UI tests** (in a separate terminal):
   ```bash
   ./gradlew testUi
   ```

3. **Run both at once:**
   ```bash
   # Terminal 1
   ./gradlew runIdeForUiTests

   # Terminal 2 (wait for IDE to start, should see robot-server listening on port 8082)
   ./gradlew testUi
   ```

### In CI (GitHub Actions)

UI tests run automatically when you trigger the "Run UI Tests" workflow manually:
- Go to Actions → Run UI Tests → Run workflow

The workflow will:
1. Start IDE with robot-server in the background
2. Wait for IDE to be ready (health check on port 8082)
3. Run UI tests
4. Collect and upload test results

## Writing UI Tests

### Basic Structure

```kotlin
@Test
fun `test my feature`() = with(remoteRobot) {
    step("Step 1 description") {
        // Find UI element and interact with it
        find<JButtonFixture>(
            byXpath("//div[@class='JButton' and @text='My Button']")
        ).click()
    }

    step("Step 2 description") {
        // Verify expected state
        waitFor(Duration.ofSeconds(5)) {
            findAll<ComponentFixture>(
                byXpath("//div[@text='Expected Text']")
            ).isNotEmpty()
        }
    }
}
```

### Key Concepts

1. **RemoteRobot**: The main entry point for UI testing
   ```kotlin
   private val remoteRobot = RemoteRobot("http://127.0.0.1:8082")
   ```

2. **Fixtures**: Type-safe wrappers for UI components
   - `JButtonFixture` - for buttons
   - `JTextFieldFixture` - for text fields
   - `DialogFixture` - for dialogs
   - `ComponentFixture` - for generic components

3. **XPath Locators**: Find UI elements
   ```kotlin
   byXpath("//div[@class='JButton' and @text='Start']")
   ```

4. **waitFor**: Wait for async operations
   ```kotlin
   waitFor(Duration.ofSeconds(10)) {
       findAll<ComponentFixture>(byXpath("//div[@text='Running']")).isNotEmpty()
   }
   ```

5. **step**: Organize test steps for better reporting
   ```kotlin
   step("Click start button") {
       find<JButtonFixture>(byXpath("...")).click()
   }
   ```

### Finding XPath Locators

To find the correct XPath for UI elements:

1. **Use Remote Robot UI Inspector:**
   ```bash
   # Download from: https://github.com/JetBrains/intellij-ui-test-robot
   java -jar robot-server-fixtures-<version>.jar
   ```

2. **Or use built-in IDE structure:**
   - Run `runIdeForUiTests`
   - Use "Tools → Internal Actions → UI → UI Inspector"

### Best Practices

1. **Use descriptive test names:**
   ```kotlin
   fun `test configuration dialog opens when config button is clicked`()
   ```

2. **Add timeouts for async operations:**
   ```kotlin
   waitFor(Duration.ofSeconds(10)) { /* condition */ }
   ```

3. **Clean up in @AfterEach:**
   ```kotlin
   @AfterEach
   fun tearDown() {
       // Close dialogs, stop servers, etc.
   }
   ```

4. **Use step() for better test output:**
   ```kotlin
   step("Open configuration") { ... }
   step("Fill form") { ... }
   step("Verify result") { ... }
   ```

5. **Test one feature per test method:**
   - ❌ `testEverything()`
   - ✅ `test tool window opens`
   - ✅ `test server starts successfully`
   - ✅ `test configuration is saved`

## Common XPath Patterns

```kotlin
// Button by text
byXpath("//div[@class='JButton' and @text='Button Text']")

// Button by tooltip
byXpath("//div[@class='JButton' and @tooltiptext='Tooltip']")

// Text field by accessible name
byXpath("//div[@class='JTextField' and @accessiblename='Field Name']")

// Dialog by title
byXpath("//div[@class='MyDialog' and @title='Dialog Title']")

// Any component containing text
byXpath("//div[contains(@text, 'Partial Text')]")
```

## Debugging Tips

1. **Enable verbose logging:**
   ```kotlin
   // In test setup
   remoteRobot.apply {
       setLogEnabled(true)
   }
   ```

2. **Take screenshots on failure:**
   ```kotlin
   @AfterEach
   fun tearDown() {
       if (testFailed) {
           remoteRobot.callJs("""importPackage(java.awt)
               var robot = new Robot()
               var screen = Toolkit.getDefaultToolkit().getScreenSize()
               var image = robot.createScreenCapture(new Rectangle(screen))
               // Save image...
           """)
       }
   }
   ```

3. **Slow down test execution:**
   ```kotlin
   // Add delays between steps for debugging
   Thread.sleep(1000)
   ```

## Resources

- [IntelliJ Remote Robot](https://github.com/JetBrains/intellij-ui-test-robot)
- [Remote Robot Documentation](https://github.com/JetBrains/intellij-ui-test-robot/wiki)
- [IntelliJ Platform SDK DevGuide](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html)
- [UI Test Examples](https://github.com/JetBrains/intellij-ui-test-robot/tree/master/ui-test-example)

## Troubleshooting

### IDE doesn't start
- Check if port 8082 is available
- Check logs in `build/idea-sandbox/system/log/`
- Try increasing timeout in workflow

### Tests can't find elements
- Verify XPath with UI Inspector
- Add waitFor() for async elements
- Check if element is visible (not hidden by another window)

### Tests are flaky
- Add proper waits (`waitFor()`)
- Avoid `Thread.sleep()`, use `waitFor()` instead
- Ensure cleanup in `@AfterEach`