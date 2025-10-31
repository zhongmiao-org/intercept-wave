# Contributing to Intercept Wave

Thank you for your interest in contributing to Intercept Wave! This document provides guidelines and instructions for contributing to the project.

[简体中文版](./CONTRIBUTING_zh.md)

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Environment Setup](#development-environment-setup)
- [Project Structure](#project-structure)
- [Development Workflow](#development-workflow)
- [Testing](#testing)
- [Code Style](#code-style)
- [Submitting Changes](#submitting-changes)
- [Reporting Issues](#reporting-issues)
- [Feature Requests](#feature-requests)

## Code of Conduct

This project adheres to a code of conduct that all contributors are expected to follow. Please be respectful and constructive in all interactions.

## Getting Started

### Prerequisites

Before you begin, ensure you have the following installed:

- **JDK 21** or higher
- **IntelliJ IDEA 2024.1** or higher (Ultimate or Community Edition)
- **Git** for version control
- **Gradle** (wrapper included in the project)

### Fork and Clone

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/intercept-wave.git
   cd intercept-wave
   ```
3. Add the upstream repository:
   ```bash
   git remote add upstream https://github.com/zhongmiao-org/intercept-wave.git
   ```

## Development Environment Setup

### 1. Open Project in IntelliJ IDEA

1. Open IntelliJ IDEA
2. Select **File > Open**
3. Navigate to the cloned repository and select the `build.gradle.kts` file
4. Click **Open as Project**
5. Wait for Gradle to sync and download dependencies

### 2. Configure Plugin SDK

The project uses the IntelliJ Platform Gradle Plugin, which automatically configures the plugin SDK. No manual SDK setup is required.

### 3. Run the Plugin

To run the plugin in a sandboxed IntelliJ IDEA instance:

1. Open the Gradle tool window (View > Tool Windows > Gradle)
2. Navigate to `Tasks > intellij > runIde`
3. Double-click to run

Or use the command line:
```bash
./gradlew runIde
```

## Project Structure

```
intercept-wave/
├── .github/                      # CI workflows, issue templates
├── .gradle/                      # Gradle local cache (generated)
├── .idea/                        # IDE project files (local)
├── build/                        # Build outputs (generated)
├── gradle/
│   ├── changelog.gradle.kts      # Changelog plugin config
│   ├── kover.gradle.kts          # Coverage config and exclusions
│   ├── test.gradle.kts           # Unit test task configuration
│   └── ui-test.gradle.kts        # UI test and robot server configuration
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── org/zhongmiao/interceptwave/
│   │   │       ├── services/     # Core services (MockServer, Config, Console adapter)
│   │   │       ├── ui/           # Tool window, dialogs, console subscriber
│   │   │       ├── events/       # Domain events and publisher (MessageBus)
│   │   │       ├── listeners/    # IDE listeners (e.g., project closing)
│   │   │       ├── startup/      # Startup activities (subscriber init)
│   │   │       ├── model/        # Data models (RootConfig, ProxyConfig, MockApiConfig)
│   │   │       └── util/         # Utilities (JSON normalize, path matching, constants)
│   │   └── resources/
│   │       ├── META-INF/plugin.xml           # Plugin descriptor
│   │       ├── messages/InterceptWaveBundle*.properties  # i18n resources
│   │       └── icons/                         # Plugin icons and resources
│   └── test/
│       └── kotlin/
│           └── org/zhongmiao/interceptwave/
│               ├── services/     # Service tests (incl. platform tests)
│               └── ui/           # UI tests (separate `testUi` task)
├── CHANGELOG.md                  # English changelog
├── CHANGELOG_zh.md               # Chinese changelog
├── README.md                     # English docs
├── README_zh.md                  # Chinese docs
├── build.gradle.kts              # Core build: plugins, deps, IntelliJ config
├── gradle.properties             # Versions, platform, plugin coordinates
└── settings.gradle.kts           # Gradle settings
```

### Key Components

- **MockServerService**: Core service managing mock servers
- **ConfigService**: Handles configuration persistence
- **ConfigDialog**: Main configuration UI
- **InterceptWaveToolWindowFactory**: Creates the tool window UI
- **ProjectCloseListener**: Cleanup on project close

## Development Workflow

### 1. Create a Feature Branch

```bash
git checkout -b feature/your-feature-name
```

Use branch naming conventions:
- `feature/` for new features
- `fix/` for bug fixes
- `docs/` for documentation changes
- `refactor/` for code refactoring
- `test/` for test additions

### 2. Make Your Changes

- Follow the existing code style (see [Code Style](#code-style))
- Write clear, descriptive commit messages
- Add tests for new functionality
- Update documentation as needed

### 3. Test Your Changes

Run tests before committing:
```bash
./gradlew test
```

Run UI tests (if applicable):
```bash
./gradlew testUi
```

Test the plugin manually:
```bash
./gradlew runIde
```

### 4. Keep Your Branch Updated

Regularly sync with the upstream repository:
```bash
git fetch upstream
git rebase upstream/main
```

## Testing

### Unit Tests

Located in `src/test/kotlin/`, unit tests cover individual components and services.

Run all unit tests:
```bash
./gradlew test
```

Run specific test class:
```bash
./gradlew test --tests "org.zhongmiao.interceptwave.services.MockServerServiceTest"
```

### UI Tests

UI tests use IntelliJ's Remote Robot framework and are located in `src/test/kotlin/org/zhongmiao/interceptwave/ui/`.

Run UI tests:
```bash
./gradlew testUi
```

Note: UI tests require more memory and time, so they run separately from unit tests.

### Test Coverage

Check test coverage:
```bash
./gradlew koverXmlReport
```

The report will be generated in `build/reports/kover/`.

### Manual Testing Checklist

When testing manually, verify:

- [ ] Plugin loads without errors
- [ ] Configuration dialog opens and saves correctly
- [ ] Mock server starts and stops properly
- [ ] Mock APIs return expected responses
- [ ] Proxy mode forwards requests correctly
- [ ] CORS headers are added
- [ ] Global cookies work as expected
- [ ] Multiple proxy groups work independently
- [ ] Configuration persists across IDE restarts

## Code Style

### Kotlin Style

Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html):

- Use 4 spaces for indentation
- Use camelCase for function and variable names
- Use PascalCase for class names
- Maximum line length: 120 characters
- Place opening braces on the same line
- Use explicit type declarations when clarity is important

### Example

```kotlin
class ExampleService {
    private var isRunning: Boolean = false

    fun startService(port: Int): Boolean {
        if (isRunning) {
            return false
        }

        // Implementation
        isRunning = true
        return true
    }
}
```

### IntelliJ Platform Guidelines

- Use the IntelliJ Platform SDK APIs correctly
- Avoid deprecated APIs
- Handle EDT (Event Dispatch Thread) properly
- Use application services and project services appropriately

## Submitting Changes

### 1. Commit Your Changes

Write clear, descriptive commit messages:

```bash
git commit -m "feat: add support for custom headers in mock responses"
```

Follow conventional commit format:
- `feat:` new feature
- `fix:` bug fix
- `docs:` documentation changes
- `test:` test additions or changes
- `refactor:` code refactoring
- `chore:` maintenance tasks

### 2. Push to Your Fork

```bash
git push origin feature/your-feature-name
```

### 3. Create a Pull Request

1. Go to the [original repository](https://github.com/zhongmiao-org/intercept-wave)
2. Click **Pull Requests > New Pull Request**
3. Click **compare across forks**
4. Select your fork and branch
5. Fill in the PR template with:
   - Clear description of changes
   - Related issue numbers (if any)
   - Screenshots (for UI changes)
   - Testing performed

### 4. PR Review Process

- Maintainers will review your PR
- Address any feedback or requested changes
- Keep your PR updated with the main branch
- Once approved, a maintainer will merge your PR

## Reporting Issues

### Before Submitting an Issue

1. Check existing issues to avoid duplicates
2. Verify the issue with the latest version
3. Gather relevant information

### Creating an Issue

Include the following information:

- **Plugin Version**: Check in Settings > Plugins
- **IntelliJ IDEA Version**: Help > About
- **Operating System**: Windows, macOS, Linux
- **Steps to Reproduce**: Clear, numbered steps
- **Expected Behavior**: What should happen
- **Actual Behavior**: What actually happens
- **Screenshots/Logs**: If applicable
- **Configuration**: Relevant config.json content (sanitized)

### Issue Template

```markdown
**Plugin Version**: 2.0.0
**IntelliJ IDEA Version**: 2024.1
**Operating System**: macOS 14.0

**Description**
A clear description of the issue.

**Steps to Reproduce**
1. Step one
2. Step two
3. ...

**Expected Behavior**
What you expected to happen.

**Actual Behavior**
What actually happened.

**Screenshots**
If applicable, add screenshots.

**Additional Context**
Any other relevant information.
```

## Feature Requests

We welcome feature requests! When submitting a feature request:

1. Check if the feature already exists or is planned
2. Clearly describe the feature and its use case
3. Explain why this feature would be valuable
4. Provide examples or mockups if possible

## Development Guidelines

### Adding a New Feature

1. **Design First**: Consider the user experience and API design
2. **Update Models**: Add or modify data models in `model/`
3. **Implement Service Logic**: Add core functionality in `services/`
4. **Create UI Components**: Add UI in `ui/` if needed
5. **Update Configuration**: Modify `ConfigService` if configuration changes
6. **Add Tests**: Write unit and UI tests
7. **Update Documentation**: Update README.md and CHANGELOG.md

### Plugin Configuration

The plugin is configured in `plugin.xml`. Key sections:

- **Extensions**: Register services, tool windows, etc.
- **Actions**: Define menu actions and toolbar buttons
- **Listeners**: Register application and project listeners

### Internationalization

Support for multiple languages is planned. When adding UI text:

1. Add keys to `messages/InterceptWaveBundle.properties`
2. Use `InterceptWaveBundle.message("key")` in code
3. Consider creating language-specific property files

## Questions?

If you have questions about contributing:

- Open a [GitHub Discussion](https://github.com/zhongmiao-org/intercept-wave/discussions)
- Create an issue with the "question" label
- Check existing documentation and issues

## License

By contributing to Intercept Wave, you agree that your contributions will be licensed under the same license as the project.

---

Thank you for contributing to Intercept Wave!
