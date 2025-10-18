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
## Plugin Introduction

Intercept Wave is a powerful IntelliJ IDEA plugin that integrates proxy and interception capabilities similar to **Nginx** and **Charles**, designed specifically for local development environments. It can intelligently intercept HTTP requests, either returning custom mock data or acting as a proxy server to forward real requests to the original server.

### ✨ v2.0 New Features: Multi-Service Proxy

- 📑 **Tab-based Interface**: Manage multiple proxy configuration groups in separate tabs
- 🚀 **Multiple Proxy Groups**: Run multiple mock services simultaneously, each on its own port
- 🏗️ **Microservices Ready**: Perfect for microservices architecture (e.g., user service on port 8888, order service on port 8889)
- 🔄 **Quick Switching**: Easily switch and manage different service configurations via tabs
- 🌍 **Multi-Environment**: Effortlessly manage dev, test, staging, and other environments

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
- **CORS Support**: Automatically add CORS headers to resolve cross-origin issues
- **Request Preservation**: Preserve original request headers and User-Agent
- **Delay Simulation**: Simulate network delays for testing slow network environments
- **Status Code Testing**: Configure different status codes to test error handling logic
- **Prefix Filtering**: Support prefix filtering to simplify API access paths
- **Global Cookie**: Configure global cookies for APIs requiring authentication
<!-- Plugin description end -->

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

### 1. Open Tool Window

1. Open your project in IntelliJ IDEA
2. Click the "Intercept Wave" icon in the left toolbar
3. The tool window displays all configured proxy groups as tabs

### 2. Manage Configuration Groups (v2.0 New Feature)

The tool window provides global operations at the top:
- **Start All**: Start all enabled configuration groups
- **Stop All**: Stop all running services
- **Configuration**: Open the configuration dialog to manage all configuration groups

#### Tab Explanation
- Each tab represents a configuration group (e.g., "User Service", "Order Service")
- Displays service name, port number, and enabled status
- Click a tab to switch to the corresponding service control panel
- **Plus Tab**: Click to quickly open the configuration dialog and add a new configuration group

#### Individual Service Control
Each tab panel displays:
- ☑/☐ **Enabled Status**: Shows whether this configuration group is enabled
- 🟢/⚫ **Running Status**: Running / Stopped
- 🔗 **Access URL**: Service access URL when running
- **Start Service** / **Stop Service**: Control individual service start/stop
- **Current Configuration**: Displays port, intercept prefix, base URL, mock API list, and other detailed information

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
- **Configuration Group Name**: Custom name (e.g., "User Service", "Order Service")
- **Port Number**: The port this service listens on (e.g., 8888, 8889)
- **Intercept Prefix**: API path prefix to intercept (default: /api)
- **Base URL**: Base URL of the original server (e.g., http://localhost:8080)
- **Strip Prefix**: When enabled, matching removes the intercept prefix
  - Example: Request `/api/user` will match mock path `/user`
- **Global Cookie**: Configure global cookie value (e.g., sessionId=abc123; userId=456)
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

3. Click the "Format JSON" button to format mock data
4. Click "OK" to save configuration

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

### Case 1: Microservices Development (v2.0 New Feature)

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

If the original API address is configured as `http://api.example.com`, the actual request will be: `http://api.example.com/api/posts`

### Case 4: Simulate Authenticated APIs

1. Set cookie in global configuration: `sessionId=abc123; userId=456`
2. Check "Use Global Cookie" in mock API configuration
3. Mock API response will automatically include `Set-Cookie` response header

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

### config.json Example (v2.0 Format)

```json
{
  "version": "2.0",
  "proxyGroups": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "User Service",
      "port": 8888,
      "interceptPrefix": "/api",
      "baseUrl": "http://localhost:8080",
      "stripPrefix": true,
      "globalCookie": "sessionId=abc123",
      "enabled": true,
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
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "name": "Order Service",
      "port": 8889,
      "interceptPrefix": "/order-api",
      "baseUrl": "http://localhost:8081",
      "stripPrefix": true,
      "globalCookie": "",
      "enabled": true,
      "mockApis": [
        {
          "path": "/orders",
          "enabled": true,
          "mockData": "{\"code\":0,\"data\":[]}",
          "method": "GET",
          "statusCode": 200,
          "useCookie": false,
          "delay": 0
        }
      ]
    }
  ]
}
```

### Configuration Migration Instructions

**Upgrading from v1.x to v2.0**:
- Old configuration automatically migrates and is backed up as `config.json.backup`
- Old configuration is converted to a "Default Config" group in the new structure
- Migration process is fully automatic, no manual operation required

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
A: When you start the mock server, the "Intercept Wave Mock Server" tab will automatically appear in the Run tool window at the bottom of IDEA, displaying real-time color-coded logs for all requests, including timestamps, request methods, paths, and whether the response was mocked or proxied.

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