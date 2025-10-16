<div align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="Intercept Wave Logo" width="128" height="128">

  # Intercept Wave for IntelliJ IDEA

  [![Build](https://github.com/zhongmiao-org/intercept-wave/workflows/Build/badge.svg)](https://github.com/zhongmiao-org/intercept-wave/actions)
  [![Version](https://img.shields.io/jetbrains/plugin/v/28728.svg?style=flat-square)](https://plugins.jetbrains.com/plugin/28728-intercept-wave)
  [![Downloads](https://img.shields.io/jetbrains/plugin/d/28728.svg?style=flat-square)](https://plugins.jetbrains.com/plugin/28728-intercept-wave)
  [![Rating](https://img.shields.io/jetbrains/plugin/r/rating/28728?style=flat-square)](https://plugins.jetbrains.com/plugin/28728-intercept-wave)
  [![License](https://img.shields.io/github/license/zhongmiao-org/intercept-wave?style=flat-square)](https://github.com/zhongmiao-org/intercept-wave/blob/main/LICENSE)

  English | [简体中文](./README_zh.md)
</div>

<!-- Plugin description -->
A powerful IntelliJ IDEA plugin that combines the proxy and interception capabilities similar to **Nginx** and **Charles**, designed specifically for local development environments. It intelligently intercepts HTTP requests, either returning custom mock data or acting as a proxy server to forward real requests to the original server with full HTTP headers.

### Core Capabilities

**Smart Interception & Proxy**:
- 🎯 Configure intercept prefix (e.g., `/api`) to precisely target specific request paths
- 🔄 **With Mock Config**: Returns preset mock data for offline development
- 🌐 **Without Mock Config**: Acts as a proxy server, forwarding requests with complete HTTP headers to get real data
- 🔀 Smart path matching with prefix stripping support

**Developer-Friendly Features**:
- 👥 **Target Users**: Frontend Engineers, QA Engineers, Full-Stack Developers
- 🎨 Visual configuration UI, no manual config file editing needed
- 💾 Persistent configuration with project-level isolation
- 🌐 Automatic CORS handling
- ⏱️ Network delay simulation support
- 🔧 Custom response status codes and headers
- 🍪 Global cookie support for authenticated APIs

### Typical Use Cases

1. **Independent Frontend Development**: Continue development with mock data when backend APIs are not ready
2. **API Testing**: Quickly switch between different response data to test edge cases
3. **Local Debugging**: Use mock for some APIs while proxying others to test servers
4. **Network Simulation**: Simulate slow networks or API timeout scenarios
5. **Cross-Origin Development**: Automatically add CORS headers to solve frontend development CORS issues
<!-- Plugin description end -->

## Features Overview

Intercept Wave provides the following core functionalities:

- **API Interception**: Intercept specific APIs and return configured mock data
- **Proxy Forwarding**: Automatically forward unconfigured APIs to the original server
- **CORS Support**: Automatically add CORS headers to resolve cross-origin issues
- **Request Preservation**: Preserve original request headers and User-Agent
- **Delay Simulation**: Simulate network delays for testing slow network environments
- **Status Code Testing**: Configure different status codes to test error handling logic
- **Prefix Filtering**: Support prefix filtering to simplify API access paths
- **Global Cookie**: Configure global cookies for APIs requiring authentication

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

## Quick Start

### 1. Start Mock Server

1. Open your project in IntelliJ IDEA
2. Click the "Intercept Wave" icon in the toolbar
3. Click the "Start Server" button in the tool window
4. Once started successfully, the access URL will be displayed (default: http://localhost:8888)

### 2. Configure Mock APIs

Click the "Configuration" button to open the configuration dialog:

#### Global Configuration
- **Mock Port**: Port for the local mock server to listen on (default: 8888)
- **Intercept Prefix**: API path prefix to intercept (default: /api)
- **Base URL**: Base URL of the original server (e.g., http://localhost:8080)
- **Strip Prefix**: When enabled, accessing `localhost:8888/user/info` will match `/api/user/info`
- **Global Cookie**: Configure global cookie value for mock APIs (e.g., sessionId=abc123; userId=456)

#### Mock API Configuration
1. Click "Add API" button
2. Fill in the following information:
   - **API Path**: e.g., `/api/user/info`
   - **HTTP Method**: ALL, GET, POST, PUT, DELETE, PATCH
   - **Status Code**: HTTP response status code (default: 200)
   - **Delay (ms)**: Simulate network delay (default: 0)
   - **Mock Data**: Response data in JSON format
   - **Enabled**: Whether to enable this mock configuration
   - **Use Global Cookie**: When enabled, includes the configured global cookie in response

3. Click "Format JSON" button to format mock data
4. Click "OK" to save configuration

## Use Cases

### Case 1: Mock Specific API

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

### Case 2: Forward Unconfigured APIs

```javascript
// This API has no mock configuration, will be forwarded to original server
fetch('http://localhost:8888/api/posts')
  .then(res => res.json())
  .then(data => console.log(data));
```

If the base URL is configured as `http://api.example.com`, the actual request will be: `http://api.example.com/api/posts`

### Case 3: Simulate Authenticated APIs

1. Set cookie in global configuration: `sessionId=abc123; userId=456`
2. Check "Use Global Cookie" in mock API configuration
3. Mock API response will automatically include `Set-Cookie` response header

### Case 4: Simulate Network Delay

Set delay time in mock configuration (e.g., 1000ms) to simulate slow network environment.

### Case 5: Test Different Response Status Codes

Configure different status codes (404, 500, etc.) to test frontend error handling logic.

## Configuration File

All configurations are saved in the `.intercept-wave` folder in the project root directory:

```
.intercept-wave/
└── config.json           # Global configuration and API mappings
```

### config.json Example

```json
{
  "port": 8888,
  "interceptPrefix": "/api",
  "baseUrl": "http://localhost:8080",
  "stripPrefix": false,
  "globalCookie": "sessionId=abc123; userId=456",
  "mockApis": [
    {
      "path": "/api/user/info",
      "enabled": true,
      "mockData": "{\"code\":0,\"data\":{\"name\":\"John\"}}",
      "method": "GET",
      "statusCode": 200,
      "useCookie": true,
      "delay": 0
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

Then check "Use Global Cookie" for mock APIs that need cookies, and the response will automatically include `Set-Cookie` header.

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

Accessing the mock server root path (`http://localhost:8888/`) returns server status and configuration information:

```json
{
  "status": "running",
  "message": "Intercept Wave Mock Server is running",
  "server": {
    "port": 8888,
    "baseUrl": "http://localhost:8080",
    "interceptPrefix": "/api"
  },
  "mockApis": {
    "total": 3,
    "enabled": 2
  },
  "apis": [
    {"path": "/api/user/info", "method": "GET", "enabled": true},
    {"path": "/api/posts", "method": "ALL", "enabled": true}
  ]
}
```

## Important Notes

1. **Port Occupation**: Ensure the configured port is not occupied by other programs
2. **Configuration Changes**: If the server is running when configuration is modified, it will automatically stop
3. **Project Closure**: Mock server will automatically stop when the project is closed
4. **Security**: This tool is only for local development environment, do not use in production

## FAQ

### Q: What to do if the server fails to start?
A: Check if the port is occupied, you can change the port number in the configuration.

### Q: Why is my API not being mocked?
A: Make sure the API path matches exactly and the mock configuration is enabled.

### Q: How to view request logs?
A: When you start the mock server, a "Intercept Wave Mock Server" tab will automatically appear in the Run tool window at the bottom of IDEA, showing real-time color-coded logs for all requests, including timestamps, request methods, paths, and whether the response was mocked or proxied.

### Q: Does it support HTTPS?
A: The current version only supports HTTP, HTTPS support is planned.

### Q: How does global cookie work?
A: Set cookie value in global configuration, then check "Use Global Cookie" in mock API configuration. The response will include the cookie via `Set-Cookie` response header.

## Development Roadmap

- [ ] HTTPS support
- [ ] WebSocket mock support
- [x] Request log viewer (available in Run tool window)
- [ ] Import/export configuration
- [ ] Mock data template library
- [ ] Custom request headers support

## Feedback & Contribution

If you have any questions or suggestions, feel free to submit an [Issue](https://github.com/zhongmiao-org/intercept-wave/issues) or [Pull Request](https://github.com/zhongmiao-org/intercept-wave/pulls)!

## License

This project is based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).