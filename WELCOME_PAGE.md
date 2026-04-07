# Welcome Page Guide

[简体中文](./WELCOME_PAGE_zh.md)

## Overview

When you access the root path of a mock service, for example `http://localhost:8888/`, the server returns a JSON welcome payload instead of an error. This response helps you inspect the current service status and route summary quickly.

## Example Responses

### When No Mock Is Available

```json
{
  "status": "running",
  "message": "Intercept Wave Mock Server is running",
  "configGroup": "Gateway",
  "server": {
    "port": 8888,
    "routes": 1
  },
  "mockApis": {
    "total": 0,
    "enabled": 0
  },
  "usage": {
    "description": "Access configured paths to get mock data or trigger forwarding",
    "example": "GET http://localhost:8888/your-api-path"
  },
  "routes": [
    {"name": "API", "pathPrefix": "/api", "targetBaseUrl": "http://localhost:8080", "stripPrefix": true, "enableMock": true, "mockApis": 0}
  ],
  "examples": []
}
```

### When Mock APIs Are Available

```json
{
  "status": "running",
  "message": "Intercept Wave Mock Server is running",
  "configGroup": "Gateway",
  "server": {
    "port": 8888,
    "routes": 2
  },
  "mockApis": {
    "total": 3,
    "enabled": 2
  },
  "usage": {
    "description": "Access configured paths to get mock data or trigger forwarding",
    "example": "GET http://localhost:8888/your-api-path"
  },
  "routes": [
    {"name": "User API", "pathPrefix": "/api", "targetBaseUrl": "http://localhost:9000", "stripPrefix": true, "enableMock": true, "mockApis": 2},
    {"name": "Order API", "pathPrefix": "/order-api", "targetBaseUrl": "http://localhost:9001", "stripPrefix": true, "enableMock": false, "mockApis": 1}
  ],
  "examples": [
    {"route": "User API", "method": "GET", "url": "http://localhost:8888/api/user/info"},
    {"route": "User API", "method": "ALL", "url": "http://localhost:8888/api/posts"}
  ]
}
```

## Field Reference

| Field | Type | Description |
|------|------|------|
| `status` | string | Service status, always `"running"` |
| `message` | string | Welcome message |
| `configGroup` | string | Current proxy group name |
| `server.port` | number | Listening port of the mock service |
| `server.routes` | number | Number of routes in the current group |
| `mockApis.total` | number | Total number of mock APIs |
| `mockApis.enabled` | number | Number of enabled mock APIs |
| `usage.description` | string | Short usage hint |
| `usage.example` | string | Example request |
| `routes` | array | Route list for the current group |
| `routes[].pathPrefix` | string | Route prefix |
| `routes[].targetBaseUrl` | string | Upstream target for that route |
| `routes[].mockApis` | number | Number of mock APIs under that route |
| `examples` | array | Directly usable example requests |

## Typical Uses

### 1. Check Whether the Service Is Running

Open `http://localhost:8888/` in a browser. If you receive the JSON payload, the service is running normally.

### 2. Review the Current Mock Setup

The `routes` and `examples` arrays let you quickly inspect which routes are configured and which example URLs are ready to use.

### 3. Confirm Active Service Settings

The `server` and `routes` sections help you verify the listening port, number of routes, and upstream targets.

### 4. Use It as a Health Check Endpoint

You can treat the root path as a health check endpoint. A `200` response indicates that the service is available.

## Implementation Details

### Source Location
`src/main/kotlin/org/zhongmiao/interceptwave/util/HttpWelcomeUtil.kt`

### Key Method
```kotlin
fun buildWelcomeJson(config: ProxyConfig): String
```

### When It Is Returned

The welcome payload is returned when the request path is `/`. It may also be returned for a route prefix root path when `stripPrefix` is enabled.

### Response Headers
- `Content-Type: application/json; charset=UTF-8`
- `Access-Control-Allow-Origin: *`

## Notes

1. **Only specific entry points return the welcome payload**: Usually `/` returns it, and some prefix root paths can also return it when `stripPrefix` is enabled
2. **JSON format**: The response is plain JSON, so scripts and tools can parse it easily
3. **CORS support**: The response includes CORS headers and can be accessed directly from frontend code
4. **Live configuration snapshot**: The payload reflects the currently active config

## Test with `curl`

```bash
# View the welcome response
curl http://localhost:8888/

# Pretty-print it
curl http://localhost:8888/ | jq

# View only the routes
curl http://localhost:8888/ | jq '.routes'

# View server information
curl http://localhost:8888/ | jq '.server'
```

## Possible Future Enhancements

1. **HTML mode**: Return HTML or JSON depending on the `Accept` header
2. **Metrics**: Show request counts and response timing
3. **Inline testing**: Add quick API testing to an HTML page
4. **Config editing**: Support editing config from the welcome page
5. **Recent logs**: Show the latest request history
