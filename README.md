<div align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="Intercept Wave Logo" width="128" height="128">

  # Intercept Wave for IntelliJ IDEA

  [![Build](https://github.com/zhongmiao-org/intercept-wave/workflows/Build/badge.svg)](https://github.com/zhongmiao-org/intercept-wave/actions)
  [![Version](https://img.shields.io/jetbrains/plugin/v/28728.svg?style=flat-square)](https://plugins.jetbrains.com/plugin/28728-intercept-wave)
  [![Downloads](https://img.shields.io/jetbrains/plugin/d/28728.svg?style=flat-square)](https://plugins.jetbrains.com/plugin/28728-intercept-wave)
  [![Rating](https://img.shields.io/jetbrains/plugin/r/rating/28728?style=flat-square)](https://plugins.jetbrains.com/plugin/28728-intercept-wave)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
  [![JetBrains IDEs](https://img.shields.io/badge/JetBrains%20IDEs-supported-000000?style=flat-square&logo=jetbrains&logoColor=white)](https://www.jetbrains.com/)
  [![UI Tests](https://github.com/zhongmiao-org/intercept-wave/actions/workflows/run-ui-tests.yml/badge.svg)](https://github.com/zhongmiao-org/intercept-wave/actions/workflows/run-ui-tests.yml)
  [![License](https://img.shields.io/github/license/zhongmiao-org/intercept-wave?style=flat-square)](https://github.com/zhongmiao-org/intercept-wave/blob/main/LICENSE)

  English | [简体中文](./README_zh.md)
</div>

<!-- Plugin description -->
## Plugin Overview

Intercept Wave is an IntelliJ IDEA plugin for local development that combines request interception, mocking, and proxying in a workflow similar to **Nginx** and **Charles**. It can intercept HTTP requests and WebSocket (`ws://` / `wss://`) messages, return custom mock data, or bridge traffic to an upstream service when you need real responses.

### ✨ Multi-Service Proxy

- 📑 **Tab-based Interface**: Manage multiple proxy configuration groups in separate tabs
- 🚀 **Multiple Proxy Groups**: Run multiple mock services simultaneously, each on its own port
- 🏗️ **Microservices Ready**: Perfect for microservices architecture (e.g., user service on port 8888, order service on port 8889)
- 🔄 **Quick Switching**: Easily switch and manage different service configurations via tabs
- 🌍 **Multi-Environment**: Effortlessly manage dev, test, staging, and other environments

### Core Capabilities

**Smart Interception & Proxy**:
- 🎯 Configure path prefixes (for example, `/api`) to precisely target specific request paths
- 🧭 Configure multiple HTTP routes inside one HTTP group, each with its own prefix, upstream target, strip-prefix rule, and Mock switch
- 🔁 Rewrite route-local paths to nginx-like upstream paths, such as `/backend/users` -> `/v1/users`
- 🔄 **With Mock Config**: Returns preset mock data for offline development
- 🌐 **Without Mock Config**: Acts as a proxy server, forwarding requests with complete HTTP headers to get real data
- 🔀 Smart path matching with prefix stripping support
- 📡 **WebSocket Mock & Bridge**: Create WS groups that listen on local `ws://` ports, optionally expose local `wss://` with a PKCS#12 keystore, bridge to upstream `ws://` / `wss://` servers, and support both automatic and manual message pushing

**Developer-Friendly Features**:
- 👥 **Target Users**: Frontend Engineers, QA Engineers, Full-Stack Developers
- 🎨 Visual configuration UI, with optional direct editing of the project config file when needed
- 💾 Persistent configuration with project-level isolation
- 🌐 Automatic CORS handling
- ⏱️ Network delay simulation support
- 🔧 Custom response status codes and headers
- 🍪 Global cookie support for authenticated APIs

### Typical Use Cases

1. **Microservices Development**: Mock multiple microservices simultaneously (user service, order service, payment service, etc.)
2. **Independent Frontend Development**: Continue development with mock data when backend APIs are not ready
3. **API Testing**: Quickly switch between different response data to test edge cases
4. **Multi-Environment Debugging**: Configure and manage dev, test, staging environments at once
5. **Local Debugging**: Use mock for some APIs while proxying others to test servers
6. **Network Simulation**: Simulate slow networks or API timeout scenarios
7. **Cross-Origin Development**: Automatically add CORS headers to solve frontend development CORS issues

## Features Overview

Intercept Wave provides the following core functionalities:

- **API Interception**: Intercept specific APIs and return configured mock data
- **Proxy Forwarding**: Automatically forward unconfigured APIs to the original server
- **WebSocket Mock & Push**: Start local WS mock/proxy services, forward to upstream `ws://`/`wss://` endpoints, and define push rules (periodic/timeline) or send messages manually from the tool window.
- **CORS Support**: Automatically add CORS headers to resolve cross-origin issues
- **Request Preservation**: Preserve original request headers and User-Agent
- **Delay Simulation**: Simulate network delays for testing slow network environments
- **Status Code Testing**: Configure different status codes to test error handling logic
- **Prefix Filtering**: Support prefix filtering to simplify API access paths
- **Global Cookie**: Configure global cookies for APIs requiring authentication

## Compatibility

Intercept Wave supports JetBrains IDEs based on the IntelliJ Platform 2023.1 and newer. The plugin is built against the 2023.1 platform baseline and emits Java 17 bytecode for compatibility with 2023.x IDE runtimes.

The plugin verification matrix covers 2023.1 baseline releases for CLion, DataGrip, DataSpell, GoLand, IntelliJ IDEA Community, IntelliJ IDEA Ultimate, PhpStorm, PyCharm Community, PyCharm Professional, Rider, RubyMine, and WebStorm.
<!-- Plugin description end -->

### What's New in v4.0

- Config version now aligns with the plugin major.minor (e.g., 4.0). Existing legacy configs load seamlessly and are saved with `"version": "4.0"` automatically.
- Mock JSON normalization and minification: `mockData` accepts single quotes, comments, unquoted keys, and trailing commas, then saves the result as compact JSON. Use "Format JSON" to pretty-print it while editing.

## Installation

### Using IDE Built-in Plugin System

<kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Intercept Wave"</kbd> > <kbd>Install</kbd>

### Using JetBrains Marketplace

Visit [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28704-intercept-wave) and click the <kbd>Install to ...</kbd> button.

Or download the [latest release](https://plugins.jetbrains.com/plugin/28704-intercept-wave/versions) and install it manually:
<kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

### Manual Installation

Download the latest release from [GitHub Releases](https://github.com/zhongmiao-org/intercept-wave/releases/latest) and install manually:
<kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Packaging & Release Notes (Maintainers)

- JetBrains Marketplace and IDE manual install require the original plugin archive generated by `./gradlew buildPlugin`.
- Always upload `build/distributions/*.zip`.
- Do not upload `build/libs/*.jar`.
- Do not upload an extracted plugin directory (for example, files unpacked from the ZIP).
- CI workflows should preserve and publish the ZIP artifact directly.

Quick local verification before release:

```bash
./gradlew buildPlugin
unzip -t build/distributions/*.zip
```

## Quick Start

### 1. Open Tool Window

1. Open your project in IntelliJ IDEA
2. Click the "Intercept Wave" icon in the left toolbar
3. The tool window displays all configured proxy groups as tabs

### 2. Manage Configuration Groups

The tool window provides global operations at the top:
- **Start All**: Start all enabled configuration groups
- **Stop All**: Stop all running services
- **Configuration**: Open the configuration dialog to manage all configuration groups

The tool window title bar also provides quick file-oriented actions:
- **Open Config File**: Open `.intercept-wave/config.json` directly in the IDE editor
- **Reload Config**: Save any unsaved IDE documents, reload the config from disk, refresh the tool window, and restart only the groups that were already running

#### Tab Explanation
- Each tab represents a configuration group (e.g., "User Service", "Order Service")
- Displays service name, port number, and enabled status
- Click a tab to switch to the corresponding service control panel
- **Plus Tab**: Click to quickly open the configuration dialog and add a new configuration group

#### Individual Service Control
Each tab panel displays:
- **Config Status**: Shows whether this configuration group is enabled
- **Running Status**: Running / Stopped
- 🔗 **Access URL**: Service access URL when running
- **Start Service** / **Stop Service**: Control individual service start/stop
- **Current Configuration**:
  - HTTP groups now use route tree cards, with child Mock items and quick edit entry points.
  - WS groups now use rule cards, with clearer connection state, send actions, and inline editing entry points.

### 3. Configure Proxy Groups

Click the "Configuration" button to open the configuration dialog:

#### Configuration Group Management (Multi-Tab Interface)
- Each tab represents a configuration group
- Tab display format: `Configuration Group Name (:Port)`
- **Add Configuration Group**: Add a new configuration group
- **Delete Current Configuration Group**: Delete the currently selected configuration group (at least one must remain)
- **Move Left** / **Move Right**: Adjust the display order of configuration groups

#### Configuration Group Settings
Each configuration group contains the following settings:

**Basic Configuration**:
- **Protocol**: `HTTP` or `WS`. HTTP groups handle HTTP APIs; WS groups handle WebSocket connections.
- **Configuration Group Name**: Custom name (e.g., "User Service", "Order Service")
- **Port Number**: The port this service listens on (e.g., 8888, 8889)
- **Enable This Configuration Group**: When checked, this configuration group will be included in "Start All"

#### Mock API Configuration
1. Click the "Add API" button
2. Fill in the following information:
   - **API Path**: e.g., `/api/user/info` or `/user/info` (depends on whether prefix stripping is enabled)
   - **HTTP Method**: ALL, GET, POST, PUT, DELETE, PATCH
   - **Status Code**: HTTP response status code (default: 200)
   - **Delay (ms)**: Simulate network delay (default: 0)
   - **Mock Data**: Response data in JSON format
   - **Enabled**: Whether to enable this mock configuration
   - **Use Global Cookie**: When enabled, the response includes the configured global cookie

3. Click "Format JSON" to format the mock data for easier editing
4. Click "OK" to save configuration

#### HTTP Settings (Protocol = HTTP)

HTTP configuration groups provide additional HTTP-specific settings:

- **Global Cookie**: Configure global cookie value (e.g., `sessionId=abc123; userId=456`)
- **Routes**: Maintain multiple HTTP routes inside one group. The config dialog uses a left sidebar to choose the current route and a right-side editor for the selected route. Each route defines:
  - **Route Name**: Display name, such as `API` or `Frontend`
  - **Path Prefix**: Prefix used for longest-prefix matching, such as `/api` or `/`
  - **Target Base URL**: Upstream target for forwarding, such as `http://localhost:8080`
  - **Strip Prefix**: Whether the route prefix is removed before Mock matching and forwarding
  - **Rewrite Target Path**: Optional path base applied after strip-prefix, such as `/v1`
  - **Enable Mock**: Whether this route first checks its own Mock API list before forwarding
- **Mock APIs**: Each route owns its own Mock API list. The configured paths are interpreted using that route's `Path Prefix`, `Strip Prefix`, and optional `Rewrite Target Path` rules. The current route's Mock list is edited directly on the right side of the config dialog.

Example multi-route setup:
- Route 1: `pathPrefix="/"`, `enableMock=false`, `targetBaseUrl=http://localhost:4001`
- Route 2: `pathPrefix="/api"`, `enableMock=true`, `targetBaseUrl=http://localhost:4002`

Path rewrite examples for local development gateways:
- `pathPrefix="/api"`, `stripPrefix=true`: `/api/users` is matched and forwarded as `/users`
- `pathPrefix="/backend"`, `stripPrefix=true`, `rewriteTargetPath="/v1"`: `/backend/users` is matched and forwarded as `/v1/users`

These routes are intended for local development gateways and nginx-like migration recipes, not as a production nginx replacement.

#### WebSocket Group Settings (Protocol = WS)

WS configuration groups share the same basic settings (name, port, enabled), and add WS-specific options:

- **Upstream WebSocket URL (optional)**: Upstream `ws://` or `wss://` address (for example, `ws://localhost:8080/ws/chat`). Leave it empty to run in local-only WS server mode.
- **WS Prefix (optional)**: Path prefix for WebSocket matching. When configured, it is used to help organize WS route matching; when empty, WS paths are matched as-is.
- **Manual Push Panel**: Toggle whether the tool window shows a WS manual push panel for this group.
- **WS Push Rules**: The config dialog now uses a left-side rule list and a right-side current rule editor:
  - Match by path pattern (supports `*`/`**` like HTTP mocks) and optional JSON event key/value.
  - Choose direction: `in` (upstream → client), `out` (client → upstream), or `both`.
  - Set mode: `off`, `periodic` (send every N seconds, optional `onOpenFire` on connect), or `timeline` (send a sequence at specific milliseconds, optional `loop`).
  - Provide message content, which is used for auto-push and as the default template when manually sending.
  - Optionally intercept matching messages instead of forwarding them.

In the tool window, WS groups now clearly distinguish:
- **Local WS Server mode**: no upstream configured; only accepts and pushes to local clients.
- **Upstream Bridge mode**: local WS service plus upstream bridging/forwarding.

#### Path Matching Rules (Wildcards)
Support wildcards in `path` for flexible matching. `stripPrefix` behavior remains unchanged:
- Single-segment `*`: matches exactly one path segment (no slash)
  - Example: `/a/b/*` matches `/a/b/123`, not `/a/b/123/456`
- Multi-segment `**`: matches multiple segments (can include slashes)
  - Example: `/a/b/**` matches `/a/b/123` and `/a/b/123/456`
- Middle-segment wildcard: support `*` at middle positions
  - Example: `/order/*/submit` matches `/order/123/submit`

Priority (high → low):
- Exact path > wildcard with fewer `*` > method-specific (non-ALL) > longer pattern

Notes:
- Matching only applies to the path (query string is ignored)
- `/a/b/**` does not match `/a/b` itself. Add an extra exact rule `/a/b` if needed

### 4. Start Services

There are two ways to start services:

**Method 1: Start All Services**
- Click the "Start All" button at the top of the tool window
- Automatically starts all enabled configuration groups

**Method 2: Start Individual Service**
- Switch to the corresponding tab
- Click the "Start Service" button in that tab
- Only starts the currently selected service

After successful service startup:
- Status displays as "● Running"
- Access URL is displayed (e.g., http://localhost:8888)
- Run tool window displays real-time logs

## Use Cases

### Case 1: Microservices Development

Mock multiple microservices simultaneously, each service running on an independent port:

**Configuration Group 1 - User Service (Port 8888)**:
```javascript
// Frontend code accessing user service
fetch('http://localhost:8888/api/user/info')
  .then(res => res.json())
  .then(data => console.log(data));
```

**Configuration Group 2 - Order Service (Port 8889)**:
```javascript
// Frontend code accessing order service
fetch('http://localhost:8889/order-api/orders')
  .then(res => res.json())
  .then(data => console.log(data));
```

**Configuration Group 3 - Payment Service (Port 8890)**:
```javascript
// Frontend code accessing payment service
fetch('http://localhost:8890/pay-api/checkout')
  .then(res => res.json())
  .then(data => console.log(data));
```

All services can run simultaneously without interference. Click the "Start All" button to start all services at once!

### Case 2: Mock Specific API

```javascript
// Frontend code
fetch('http://localhost:8888/api/user/info')
  .then(res => res.json())
  .then(data => console.log(data));
```

**Configuration**:
- Path: `/api/user/info`
- Method: `GET`
- Mock Data:
```json
{
  "code": 0,
  "data": {
    "id": 1,
    "name": "John Doe",
    "email": "john@example.com"
  },
  "message": "success"
}
```

### Case 3: Forward Unconfigured APIs

```javascript
// This API has no mock configuration, will be automatically forwarded to the original server
fetch('http://localhost:8888/api/posts')
  .then(res => res.json())
  .then(data => console.log(data));
```

If the upstream route target is configured as `http://api.example.com`, the actual request will be `http://api.example.com/api/posts`.

### Case 4: Simulate Authenticated APIs

1. Set cookie in global configuration: `sessionId=abc123; userId=456`
2. Check "Use Global Cookie" in the mock API configuration
3. The mock response will automatically include a `Set-Cookie` header

### Case 5: Simulate Network Delay

Set delay time in mock configuration (e.g., 1000ms) to simulate slow network environment.

### Case 6: Test Different Response Status Codes

Configure different status codes (404, 500, etc.) to test frontend error handling logic.

## Configuration File

All configurations are saved in the `.intercept-wave` folder in the project root directory:

```
.intercept-wave/
└── config.json           # Global configuration and multiple configuration groups
```

### config.json Example

```json
{
  "version": "4.0",
  "proxyGroups": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Gateway",
      "port": 8888,
      "globalCookie": "sessionId=abc123",
      "enabled": true,
      "routes": [
        {
          "name": "User API",
          "pathPrefix": "/api",
          "targetBaseUrl": "http://localhost:9000",
          "stripPrefix": true,
          "enableMock": true,
          "mockApis": [
            {
              "path": "/user/info",
              "enabled": true,
              "mockData": "{\"code\":0,\"data\":{\"name\":\"John Doe\"}}",
              "method": "GET",
              "statusCode": 200,
              "useCookie": true,
              "delay": 0
            }
          ]
        },
        {
          "name": "Order API",
          "pathPrefix": "/order-api",
          "targetBaseUrl": "http://localhost:9001",
          "stripPrefix": true,
          "enableMock": false,
          "mockApis": []
        },
        {
          "name": "Payment API",
          "pathPrefix": "/pay-api",
          "targetBaseUrl": "http://localhost:9002",
          "stripPrefix": true,
          "enableMock": false,
          "mockApis": []
        }
      ]
    }
  ]
}
```

 

## Advanced Features

### Global Cookie Configuration

Set cookie value in global configuration, multiple cookies separated by semicolons:

```
sessionId=abc123; userId=456; token=xyz789
```

Then enable "Use Global Cookie" for any mock API that needs cookies. The response will automatically include a `Set-Cookie` header.

### CORS Support

Mock server automatically adds the following CORS headers:
```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization
```

### Proxy Mode

Unconfigured mock APIs will be automatically forwarded to the original server, preserving:
- Original request headers
- User-Agent
- Request body (for POST/PUT, etc.)
- Cookies (if any)

## Welcome Page

Accessing the mock server root path (`http://localhost:8888/`) returns server status and route summary information:

```json
{
  "status": "running",
  "message": "Intercept Wave Mock Server is running",
  "configGroup": "Gateway",
  "server": {
    "port": 8888,
    "routes": 3
  },
  "mockApis": {
    "total": 3,
    "enabled": 2
  },
  "routes": [
    {"name": "User API", "pathPrefix": "/api", "targetBaseUrl": "http://localhost:9000", "stripPrefix": true, "enableMock": true, "mockApis": 1},
    {"name": "Order API", "pathPrefix": "/order-api", "targetBaseUrl": "http://localhost:9001", "stripPrefix": true, "enableMock": false, "mockApis": 1}
  ],
  "examples": [
    {"route": "User API", "method": "GET", "url": "http://localhost:8888/api/user/info"}
  ]
}
```

## Important Notes

1. **Port Availability**: Make sure the configured port is not already in use
2. **Configuration Changes**: If you modify the configuration while a service is running, the service may restart or stop so the new settings can take effect
3. **Project Closure**: Running services stop automatically when the project closes
4. **Security**: This tool is intended for local development only and should not be used in production

## FAQ

### Q: What should I do if the server fails to start?
A: Check whether the configured port is already in use, then change the port number if needed.

### Q: Why is my API not being mocked?
A: Make sure the request matches the selected route, the mock path is correct, and the mock entry is enabled.

### Q: How can I view request logs?
A: After you start a service, the "Intercept Wave Mock Server" tab appears in the Run tool window at the bottom of IntelliJ IDEA and shows real-time logs for requests, including timestamps, methods, paths, and whether each response was mocked or proxied.

### Q: Does it support HTTPS?
A: HTTP groups currently expose local HTTP endpoints. WebSocket groups support local `ws://` and optional local `wss://` with a PKCS#12 keystore.

### Q: How does the global cookie feature work?
A: Set the cookie value in the group configuration, then enable "Use Global Cookie" for the relevant mock API. The response will return that cookie through the `Set-Cookie` header.

## Testing & Coverage

### Run Unit Tests

- Run all tests: `./gradlew test`
- Start the IDE for Robot tests: `./gradlew runIdeForUiTests`
- UI tests (Robot): `./gradlew testUi` (run this after `runIdeForUiTests` is healthy on `http://127.0.0.1:8082`)

Notes:
- Platform tests run in a single forked JVM and are headless-configured.
- Remote Robot UI tests are split into `testUi`; other platform/UI tests still run under `test`.

### Integration Tests (Docker upstream)

These tests require the upstream service container running (default http://localhost:9000, `intercept-wave-upstream:v0.3.0`):

- Start container:
  - `cd docker`
  - `docker compose -f docker-compose.upstream.yml up -d upstream`

Running by category (marked with `@Category(IntegrationTest)`):

- Run only integration tests:
  - `./gradlew test -DincludeTags=org.zhongmiao.interceptwave.tags.IntegrationTest -Diw.upstream.http=http://localhost:9000`
- Exclude integration tests (unit tests only):
  - `./gradlew test -DexcludeTags=org.zhongmiao.interceptwave.tags.IntegrationTest`
- Default behavior:
  - Without include/exclude, all tests run; if the container is not running, integration tests auto-skip after availability probe.

Override upstream base URL:

- System property: `-Diw.upstream.http=http://localhost:9000`
- Environment variable: `IW_UPSTREAM_HTTP=http://localhost:9000`

### Coverage Reports (Kover)

- XML: `./gradlew koverXmlReport` → `build/reports/kover/report.xml`
- HTML: `./gradlew koverHtmlReport` → `build/reports/kover/html/index.html`

Excluded from coverage:
- UI packages: `org.zhongmiao.interceptwave.ui`, `org.zhongmiao.interceptwave.toolWindow`
- Adapter packages: `org.zhongmiao.interceptwave.listeners`, `org.zhongmiao.interceptwave.startup`, `org.zhongmiao.interceptwave.events`
- UI-facing service: `org.zhongmiao.interceptwave.services.ConsoleService`

## Project Structure (Gradle)

```
project/
├── build.gradle.kts        # Core build: plugins, dependencies, IntelliJ config
├── gradle/
│   ├── changelog.gradle.kts # Changelog plugin config
│   ├── kover.gradle.kts     # Coverage config and exclusions
│   ├── test.gradle.kts      # Unit test task configuration
│   └── ui-test.gradle.kts   # UI test and robot server configuration
├── gradle.properties        # Version, platform, plugin coordinates
└── settings.gradle.kts      # Gradle settings
```

Rationale:
- Keep `build.gradle.kts` concise and focused on core plugin setup.
- Isolate test/UI/coverage/changelog logic to reduce merge conflicts and improve readability.

## Repository Layout

For a brief Gradle layout, see the previous section. For the full repository layout (with module/package responsibilities), see:
- CONTRIBUTING.md: Project Structure
- CONTRIBUTING_zh.md: 项目结构

## Development Roadmap

- [ ] HTTPS/WSS support
- [x] WebSocket mock support
- [x] Request log viewer (available in Run tool window)
- [ ] Import/export configuration
- [ ] Mock data template library
- [ ] Custom request headers support

## Feedback & Contribution

If you have any questions or suggestions, feel free to submit an [Issue](https://github.com/zhongmiao-org/intercept-wave/issues) or [Pull Request](https://github.com/zhongmiao-org/intercept-wave/pulls)!

## License

This project is based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).
