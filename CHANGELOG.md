<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Intercept Wave Changelog

> [中文更新日志](./CHANGELOG_zh.md) | [Chinese Changelog](./CHANGELOG_zh.md)

## [Unreleased]

### 🐛 Fixed

- Stop button activation logic in Run tool window
  - Single start failure (e.g., port in use) no longer enables the IDE Stop button.
  - Stop is enabled only after at least one server starts successfully (including bulk starts).
  - Implemented by lazy ProcessHandler binding: show console without attaching on Starting events, attach on `ServerStarted` or `AllServersStarted(success > 0)`. When no servers are running, the virtual process is terminated to disable Stop.

### 🔄 Changed

- Tool window factory refactor to reduce Plugin Verifier noise
  - Introduced a thin Java adapter `IWToolWindowFactory` implementing `ToolWindowFactory, DumbAware` to avoid Kotlin interface bridge methods that were reported as deprecated/experimental/internal API usages.
  - Updated `plugin.xml` to use the new factory class and removed deprecated `doNotActivateOnStart` attribute (kept `anchor`/`icon` declarative).
  - Verification still reports as Compatible for target IDEs.

## [3.0.0] - 2025-10-31

### ✨ Added

- 🏠 Prefix welcome route: When `stripPrefix=true`, requests to `/<interceptPrefix>` and `/<interceptPrefix>/` now return the same JSON welcome page as `/`, showing only enabled Mock APIs and example URLs. (MockServerService)
- 🧠 Smart mock input: Mock API editor accepts JS/JSON5-like snippets (single quotes, unquoted keys, trailing commas, comments) pasted from code and converts them to strict JSON before saving. (MockApiDialog)
- 🧩 Domain events + startup subscriber: Introduced `MockServerEvent` (RequestReceived/MockMatched/ForwardingTo/Forwarded/MatchedPath/AllServers*) with a project-level publisher and a UI subscriber created on startup so first events are visible in the Run tool window.

### 🔄 Changed

- 🗜️ Mock data persistence: On save, `mockData` is strictly validated and minified to compact JSON (no spaces or newlines). Invalid JSON is rejected with clear errors; the “Format JSON” button now pretty-prints using a real JSON parser for readability only, storage remains minified. (MockApiDialog)
- 🔁 Config version stamping: On load or after normalization, the `version` field in `config.json` is automatically updated to the plugin's major.minor (x.y). Patch (z) releases do not change this value, ensuring migration scripts run at intended milestones. (ConfigService)
- 🧵 Proxy forwarding now uses Java HttpClient, filters hop‑by‑hop and restricted request headers, filters conflicting response headers, and normalizes CORS headers via set (not add) to avoid duplicates.
- 🎛️ Run/Stop integration: the Run content’s ProcessHandler is kept in sync; “Stop All” delegates to the same stop path; timestamps are dimmed and levels are colorized in the console. Printing no longer implicitly re‑binds a process.
- 📦 Platform target updated: sinceBuild -> `251`, platformVersion -> `2025.1.5`.

### 🧹 Deprecated/Compat

- 🔄 Replaced deprecated `ProcessAdapter` with `ProcessListener`.
- 🔔 Migrated notifications to `NotificationGroupManager` (replacing `Notifications.Bus` / raw `Notification`).

## [2.2.1] - 2025-10-27

### 🔧 CI/CD

- 🔧 Update release workflow to patch `CHANGELOG.md` at the start of the release using `patchChangelog`, ensuring packaged change notes match the current version.
- 🚀 After a successful publish, check out `main`, re-run the changelog patch, and open an auto-merge PR to update `main`.
- 🇨🇳 Add Chinese changelog handling: automatically move Unreleased to the current version and insert a new Unreleased section for the next cycle.
- ✅ Ensure `main` branch changelog only changes after a successful publish.

## [2.2.0]

### ✨ Added

- 🌟 Wildcard path matching for mock API paths
- 🔹 Single-segment `*`: e.g., `/a/b/*` matches `/a/b/123` (not `/a/b/123/456`)
- 🔹 Multi-segment `**`: e.g., `/a/b/**` matches `/a/b/123` and `/a/b/123/456` (not `/a/b`)
- 🔹 Middle-segment wildcard: e.g., `/order/*/submit` matches `/order/123/submit`
- 🧭 Matching priority: exact path > fewer wildcards > specific method (non-ALL) > longer pattern
- 🧩 `stripPrefix` behavior unchanged: when enabled, write paths after removing the intercept prefix

### 🧪 Testing & Quality

- ✅ Added unit tests for wildcard matching: single `*`, double `**`, and middle `*`
- 🗒️ Updated code comments to Chinese and avoided `/**` sequences by splitting examples

### 📚 Documentation

- 📖 README: Added "Path Matching Rules (Wildcards)" section with examples and priority
- 🇨🇳 README_zh: Added "路径匹配规则（通配符）"说明与示例
- 📝 CHANGELOG: Updated Unreleased with the above changes

## [2.1.0]

### 🔄 Changed

- **UI Components Migration**: Migrated from standard Swing/AWT components to IntelliJ Platform UI components
  - Replaced `JPanel` with `JBPanel` for better theme integration
  - Replaced `JCheckBox` with `JBCheckBox` for consistent UI styling
  - All dialog panels now use JetBrains components for better HiDPI and theme support

### ✨ Added

- **HTTP Method Dropdown**: Added dropdown selector for HTTP method column in Mock API table
  - Provides standard HTTP methods: GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
  - Prevents typos and ensures consistency in method selection
  - Uses IntelliJ Platform's `ComboBox` component

### 🧪 Testing

- **Expanded Test Coverage**: Added comprehensive unit tests to improve code quality and reliability
  - **ProxyConfigTest**: 23 test cases for RootConfig and ProxyConfig data models
    - Tests for default values, serialization, UUID generation
    - Edge cases: special characters, port boundaries, field mutations
    - Validation of stripPrefix behavior and useCookie flag
  - **ProjectCloseListenerTest**: 3 test cases for project close handling
    - Listener instantiation
    - Safe handling of no running servers
    - Multiple calls safety
  - **ConfigDialogTest**: 4 test cases for UI component migration
    - Verification of JetBrains UI component usage
    - Dialog instantiation and disposal
    - JBPanel and JBCheckBox availability
  - **ConfigServiceTest**: 20 test cases for v2.0 API (RootConfig and ProxyConfig)
    - Root config initialization and persistence
    - Proxy group CRUD operations
    - Mock API persistence
    - Configuration reload and file management
  - **MockServerExecutorTest**: 9 test cases for executor and server management
    - Executor lifecycle management
    - Server status and URL retrieval
    - ProxyConfig creation and validation
- **Total Test Count**: 104 unit tests across the entire project (61 Platform tests + 22 standard JUnit tests + 21 integration tests)
  - Note: In CI environment, some Platform tests (MockServerServiceTest, ConfigServiceTest, ProjectCloseListenerTest) are excluded due to IDE instance requirements
- **Coverage Areas**: Data models, services, listeners, UI components, configuration management, server operations, executor management

## [2.0.0]

### 🎉 Major Features

- ✨ **Tab-based UI**: Organize multiple proxy configurations in separate tabs
- 🚀 **Multiple Proxy Groups**: Configure and manage multiple services simultaneously
- 🎯 **Individual Port Management**: Each proxy group can run on its own port
- ⚙️ **Per-Group Settings**: Customize port, intercept prefix, base URL, and more for each service
- 🔄 **Easy Switching**: Quickly switch between different proxy configurations via tabs
- 🏗️ **Microservices Ready**: Perfect for microservices architecture development (e.g., user service on port 8888, order service on port 8889)
- 🌍 **Multi-Environment**: Support different environment configurations (dev, test, staging, production)

### ✨ Added

- 📋 **RootConfig**: New root configuration structure with version and proxyGroups
- 🎯 **ProxyConfig**: Individual proxy group configuration with UUID, name, enabled status
- 🔗 **Backward Compatible**: Old MockConfig kept for compatibility (marked @Deprecated)

### 🔄 Changed

- 📄 **File Structure**: Configuration format upgraded from v1.0 to v2.0
  - **New format**: `{ "version": "2.0", "proxyGroups": [...] }`
  - **Old format**: `{ "port": 8888, "interceptPrefix": "/api", ... }`
- 📁 **Nested Structure**: Single config now becomes array of configs in `proxyGroups`

### 🐛 Fixed

- 🔧 **Port Detection**: Fixed false positive port conflict detection using ServerSocket
- 🔄 **Dialog Close**: Fixed configuration dialog not closing after saving
- 🎯 **Change Listeners**: Removed duplicate tab change listeners to prevent dialog reopening
- 🧹 **Resource Cleanup**: Proper cleanup of old listeners when rebuilding tabs

### 🛡️ Backward Compatibility

- 🔄 **Seamless Upgrade**: Old v1.0 configs automatically upgrade to v2.0 format
- 💾 **Safety Backup**: Old configuration backed up as `config.json.backup`
- 🎯 **Default Name**: Migrated config becomes "默认配置" (Default Config)
- 📢 **User Feedback**: Success notification shown after migration
- ✅ **No Data Loss**: All mock APIs and settings preserved

### 🔧 Technical Details

- 🗺️ **ConcurrentHashMap**: Thread-safe multi-server instance management
- 🧵 **Independent Threads**: Each server has its own `HttpServer` and thread pool
- 🆔 **UUID Identification**: Configuration groups identified by UUID for stability
- 🔍 **Smart Detection**: Intelligent port conflict detection before startup
- 🧹 **Resource Management**: Proper lifecycle management for server instances

### 📝 Notes

- ✅ **Complete Implementation**: UI Layer, configuration dialog, and tool window all updated
- 🚫 **No Breaking Changes**: Zero breaking changes for end users - automatic migration handles everything
- 🎯 **Production Ready**: Thoroughly tested with multiple concurrent servers
- 📚 **Documentation**: Comprehensive CHANGELOG with migration guide

## [1.0.3]

### Changed

- Updated plugin name to "Intercept Wave"
- Simplified tool window configuration by moving more attributes to declarative plugin.xml configuration
    - Configure icon, anchor, and doNotActivateOnStart attributes directly in plugin.xml
    - Removed `init()` method from code in favor of XML declarative configuration

### Added

- Added MIT License file
- Added real-time request log viewer in IDEA's Run tool window
    - Automatically displays in Run tool window when server starts
    - Color-coded log messages (info, success, warning, error, debug)
    - Request/response logging with timestamps
    - Mock response vs. proxy forwarding indicators
    - Server startup/shutdown notifications
    - Integrated with IDEA's native Run tool window (not embedded in plugin window)

### Improved

- Removed dialog popups for server start/stop operations
    - Server status notifications now appear in Run tool window logs only
    - Provides cleaner, less intrusive user experience

### Note

- Some Plugin Verifier warnings (deprecated/experimental/internal API) originate from the `ToolWindowFactory` interface itself
    - Kotlin compiler automatically generates bridge implementations for interface methods
    - These warnings are inherent characteristics of Kotlin's interaction with IntelliJ Platform and do not affect functionality
    - Source code already uses all recommended new APIs (`shouldBeAvailable()`, DumbAware, etc.)

## [1.0.2]

### Added

- Updated documentation for more precise plugin introduction
- Improved UI compatibility
    - Use `JBColor` instead of `java.awt.Color` to support light and dark themes
    - Use `JBUI.insets()` instead of native `Insets` to support HiDPI displays
    - Use `JBScrollPane` instead of native `JScrollPane`

### Fixed

- Fixed `stripPrefix` path matching logic
    - Corrected path matching behavior to be more intuitive
    - `stripPrefix=true` (default): `path` in `mockApis` is configured as relative path, request `/api/user` strips prefix to match `path="/user"`
    - `stripPrefix=false`: `path` in `mockApis` requires full path, request `/api/user` matches `path="/api/user"`
    - Updated related comments and documentation to clearly explain configuration methods
- Fixed port number formatting issue in configuration dialog tooltip
    - Convert port number to string before passing to `message()` function to avoid localization formatting as `8,888`
- Resolved all IntelliJ Platform compatibility warnings
    - Removed usage of deprecated APIs (`ToolWindowFactory.isApplicable()`, `isDoNotActivateOnStart()`)
    - Removed usage of experimental APIs (`ToolWindowFactory.manage()`)
    - Removed usage of internal APIs (`getAnchor()`, `getIcon()`)
    - Use new `init()` method to configure tool window properties
    - Added `DumbAware` interface to improve compatibility
- Fixed Kotlin build statistics collection error in CI
    - Disabled Kotlin compilation statistics to avoid errors from missing directories in CI environment
    - Added `kotlin.build.report.enabled = false` configuration

## [1.0.1] - 2025-10-15

### Added

- Configuration file auto-completion feature
    - Automatically detects and completes missing fields in configuration file on plugin startup
    - Preserves existing user configuration, only adds missing default configuration items
    - Supports smooth upgrade from old version configurations to new versions

### Fixed

- Fixed `ERR_INVALID_CHUNKED_ENCODING` error when forwarding requests
    - Resolved conflict between `Transfer-Encoding: chunked` and `Content-Length` when forwarding responses
    - Exclude `Transfer-Encoding` and `Content-Length` headers when copying original server response headers

## [1.0.0] - 2025-10-15

### Added

- Implemented core Mock server functionality
- Support for request interception and forwarding
- Support for custom Mock data responses
- Provided visual configuration interface

[Unreleased]: https://github.com/zhongmiao-org/intercept-wave/compare/v3.0.0...HEAD
[3.0.0]: https://github.com/zhongmiao-org/intercept-wave/compare/v2.2.1...v3.0.0
[2.2.1]: https://github.com/zhongmiao-org/intercept-wave/compare/v2.2.0...v2.2.1
[2.2.0]: https://github.com/zhongmiao-org/intercept-wave/compare/v2.1.0...v2.2.0
[2.1.0]: https://github.com/zhongmiao-org/intercept-wave/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/zhongmiao-org/intercept-wave/compare/v1.0.3...v2.0.0
[1.0.3]: https://github.com/zhongmiao-org/intercept-wave/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/zhongmiao-org/intercept-wave/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/zhongmiao-org/intercept-wave/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/zhongmiao-org/intercept-wave/commits/v1.0.0
