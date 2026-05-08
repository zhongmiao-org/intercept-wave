# Intercept Wave Usage Guide

[简体中文](./USAGE_zh.md)

## Overview

Intercept Wave is an IntelliJ IDEA plugin that helps you handle HTTP mocking, proxy forwarding, and WebSocket debugging in one local workflow. You can use it to:
- Intercept specific APIs and return custom mock data
- Forward unmatched requests to upstream services automatically
- Maintain multiple proxy groups for different services or environments
- Add CORS headers automatically to simplify frontend integration

## Quick Start

### 1. Open the Plugin

1. Open your project in IntelliJ IDEA
2. Click the "Intercept Wave" icon in the side toolbar
3. Select a proxy group and click "Start Service", or use "Start All" at the top
4. After the service starts, a local access URL such as `http://localhost:8888` will be shown

### 2. Configure Mock Services

Click the "Configuration" button to open the configuration dialog.

#### Group Basics
- **Protocol**: `HTTP` or `WS`
- **Group Name**: For example, "User Service" or "Order Service"
- **Port**: The local listening port for the current group
- **Enable This Group**: Whether this group should be included in "Start All"

#### HTTP Settings
- **Global Cookie**: Optionally append a shared cookie to mock responses
- **Routes**: Each HTTP group can contain multiple routes, and each route includes:
  - **Path Prefix**: Such as `/api` or `/order-api`
  - **Target Base URL**: Such as `http://localhost:8080`
  - **Strip Prefix**: Whether to remove the route prefix before matching and forwarding
  - **Rewrite Target Path**: Optional path base applied after strip-prefix, such as `/v1`
  - **SPA Fallback Path**: Optional fallback path for HTML navigation 404s, such as `/index.html`
  - **Enable Mock**: Whether this route should check its own mock list first

Route rewrite is useful when replacing a local nginx rule. For example, with `pathPrefix="/backend"`, `stripPrefix=true`, and `rewriteTargetPath="/v1"`, a request to `/backend/users?active=true` is matched and forwarded as `/v1/users?active=true`. This is local development gateway behavior, not production nginx replacement behavior.

Frontend dev server proxy example: configure an API route with `pathPrefix="/api"`, `stripPrefix=true`, `targetBaseUrl="http://localhost:9000"`, and a frontend route with `pathPrefix="/"`, `stripPrefix=false`, `targetBaseUrl="http://localhost:5173"`, `spaFallbackPath="/index.html"`, `enableMock=false`. Then `/api/users` goes to the backend, while `/`, `/assets/app.js`, and `/dashboard/settings` go to the frontend dev server.

#### Nginx Migration Recipes

Use one HTTP group as the local gateway port, then add routes that mirror nginx `location` blocks. This is intended for local development, not production traffic.

Frontend dev server plus backend API:

```json
{
  "name": "Gateway",
  "port": 8888,
  "protocol": "HTTP",
  "routes": [
    { "name": "API", "pathPrefix": "/api", "targetBaseUrl": "http://localhost:9000", "stripPrefix": true, "rewriteTargetPath": "", "spaFallbackPath": "", "enableMock": true },
    { "name": "Frontend", "pathPrefix": "/", "targetBaseUrl": "http://localhost:5173", "stripPrefix": false, "rewriteTargetPath": "", "spaFallbackPath": "/index.html", "enableMock": false }
  ]
}
```

Multiple backend services with a frontend fallback:

```json
[
  { "name": "API", "pathPrefix": "/api", "targetBaseUrl": "http://localhost:9000", "stripPrefix": true, "rewriteTargetPath": "", "spaFallbackPath": "", "enableMock": true },
  { "name": "Auth", "pathPrefix": "/auth", "targetBaseUrl": "http://localhost:9010", "stripPrefix": true, "rewriteTargetPath": "", "spaFallbackPath": "", "enableMock": true },
  { "name": "Admin API", "pathPrefix": "/admin-api", "targetBaseUrl": "http://localhost:9020", "stripPrefix": true, "rewriteTargetPath": "", "spaFallbackPath": "", "enableMock": true },
  { "name": "Frontend", "pathPrefix": "/", "targetBaseUrl": "http://localhost:5173", "stripPrefix": false, "rewriteTargetPath": "", "spaFallbackPath": "/index.html", "enableMock": false }
]
```

Common issues:
- **Port occupied**: change the group port or stop the process that already listens on it.
- **SPA refresh returns 404**: keep the frontend route at `pathPrefix="/"`, set `enableMock=false`, and use `spaFallbackPath="/index.html"` for HTML navigation requests.
- **Prefix or rewrite mismatch**: `stripPrefix` runs before `rewriteTargetPath`; Mock paths and forwarded paths use the rewritten route-local path.
- **Unexpected CORS or headers**: the local gateway adds default CORS headers for development. Route-level header override recipes are tracked separately.
- **Static dist and HTTPS**: static build serving is tracked by [#153](https://github.com/zhongmiao-org/intercept-wave/issues/153), and HTTPS listener support is tracked by [#151](https://github.com/zhongmiao-org/intercept-wave/issues/151).

Related roadmap items: IntelliJ [#150](https://github.com/zhongmiao-org/intercept-wave/issues/150), VS Code [#39](https://github.com/zhongmiao-org/intercept-wave-vscode/issues/39), and VS Code docs [#46](https://github.com/zhongmiao-org/intercept-wave-vscode/issues/46).

#### Mock API Settings
1. Click "Add API"
2. Fill in the following fields:
   - **API Path**: For example, `/user/info` or `/api/user/info`, depending on whether the current route strips its prefix
   - **HTTP Method**: ALL, GET, POST, PUT, DELETE, PATCH
   - **Status Code**: The HTTP response status code, default `200`
   - **Delay (ms)**: Simulated network delay, default `0`
   - **Response Template**: Optional built-in response shape for success, pagination, empty data, error, or auth expiration
   - **Mock Data**: The response payload in JSON format
   - **Enabled**: Whether this mock rule is active
   - **Use Global Cookie**: Whether the response should include the group-level cookie

3. Click "Apply Template" to replace the editable Mock Data area with the selected template when useful
4. Click "Format JSON" to make the payload easier to read
5. Click "OK" to save the configuration

## Common Scenarios

### Scenario 1: Mock a Specific API

```javascript
// Frontend code
fetch('http://localhost:8888/api/user/info')
  .then(res => res.json())
  .then(data => console.log(data));
```

Configuration:
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

### Scenario 2: Forward an Unconfigured API

```javascript
// This API is not mocked, so it will be forwarded automatically
fetch('http://localhost:8888/api/posts')
  .then(res => res.json())
  .then(data => console.log(data));
```

If the current route target is configured as `http://api.example.com`, the actual request will be forwarded to:
`http://api.example.com/api/posts`

### Scenario 3: Simulate Network Delay

Set a delay value in the mock rule, for example `1000ms`, to simulate a slow network.

### Scenario 4: Test Different Response Status Codes

Configure different status codes such as `404` or `500` to verify frontend error handling.

### Scenario 5: Debug WebSocket Traffic

When a group uses the `WS` protocol, you can:
- Start a local `ws://` service, and enable local `wss://` when needed
- Bridge messages to an upstream `ws://` or `wss://` service
- Configure automatic push rules or send messages manually from the tool window

## Configuration File

All configuration is stored in the `.intercept-wave` directory under the project root:

```
.intercept-wave/
└── config.json           # Root config, proxy groups, HTTP routes, mock rules, and WS rules
```

### Example `config.json`

```json
{
  "version": "4.0",
  "proxyGroups": [
    {
      "name": "Gateway",
      "port": 8888,
      "enabled": true,
      "protocol": "HTTP",
      "globalCookie": "sessionId=abc123",
      "routes": [
        {
          "name": "API",
          "pathPrefix": "/api",
          "targetBaseUrl": "http://localhost:8080",
          "stripPrefix": true,
          "rewriteTargetPath": "",
          "spaFallbackPath": "",
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
        }
      ]
    }
  ]
}
```

## Advanced Features

### Global Cookie

After you set a cookie value at the group level, you can enable "Use Global Cookie" on selected mock rules and the response will include a `Set-Cookie` header automatically.

### CORS Support

The mock server automatically adds the following CORS headers:
```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization
```

### Proxy Mode

When no mock rule matches, the request is forwarded to the upstream server while preserving as much original request data as possible:
- Original request headers
- User-Agent
- Request body for POST/PUT and similar methods
- Cookies when present

## Notes

1. **Port Availability**: Make sure the configured port is not already in use
2. **Configuration Changes**: If a service is already running, changing the config may require a reload or restart for that group
3. **Project Closure**: Related services stop automatically when the project closes
4. **Security**: This tool is intended for local development and debugging only, not production use

## FAQ

### Q: What should I do if the service fails to start?
A: First check whether the configured port is already occupied. If needed, change the port for that proxy group.

### Q: Why is my API not being mocked?
A: Make sure the request matches the correct route, the mock path is correct, and the corresponding rule is enabled.

### Q: How can I view request logs?
A: After you start a service, you can view real-time request logs in the Run tool window at the bottom of IntelliJ IDEA.

### Q: Does it support HTTPS?
A: HTTP groups currently expose local HTTP endpoints. WS groups support local `ws://` and can expose local `wss://` with a PKCS#12 certificate.

## Feedback and Contributions

Issues and pull requests are welcome.
