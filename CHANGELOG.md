<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Intercept Wave Changelog

> [中文更新日志](./CHANGELOG_zh.md) | [Chinese Changelog](./CHANGELOG_zh.md)

## [Unreleased]

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
