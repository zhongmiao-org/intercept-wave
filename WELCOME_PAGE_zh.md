# Mock 服务欢迎页说明

[English](./WELCOME_PAGE.md)

## 功能说明

当你直接访问 Mock 服务的根路径（例如 `http://localhost:8888/`）时，服务不会返回错误，而是返回一份 JSON 格式的欢迎页，用于展示当前服务状态和路由摘要。

## 欢迎页面返回示例

### 没有可用 Mock 时

```json
{
  "status": "running",
  "message": "Intercept Wave Mock 服务运行中",
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
    "description": "访问已配置的接口路径即可获取 Mock 数据或触发转发",
    "example": "GET http://localhost:8888/your-api-path"
  },
  "routes": [
    {"name": "API", "pathPrefix": "/api", "targetBaseUrl": "http://localhost:8080", "stripPrefix": true, "enableMock": true, "mockApis": 0}
  ],
  "examples": []
}
```

### 存在可用 Mock 时

```json
{
  "status": "running",
  "message": "Intercept Wave Mock 服务运行中",
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
    "description": "访问已配置的接口路径即可获取 Mock 数据或触发转发",
    "example": "GET http://localhost:8888/your-api-path"
  },
  "routes": [
    {"name": "用户 API", "pathPrefix": "/api", "targetBaseUrl": "http://localhost:9000", "stripPrefix": true, "enableMock": true, "mockApis": 2},
    {"name": "订单 API", "pathPrefix": "/order-api", "targetBaseUrl": "http://localhost:9001", "stripPrefix": true, "enableMock": false, "mockApis": 1}
  ],
  "examples": [
    {"route": "用户 API", "method": "GET", "url": "http://localhost:8888/api/user/info"},
    {"route": "用户 API", "method": "ALL", "url": "http://localhost:8888/api/posts"}
  ]
}
```

## 返回字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `status` | string | 服务状态，固定为 "running" |
| `message` | string | 欢迎消息 |
| `configGroup` | string | 当前配置组名称 |
| `server.port` | number | Mock 服务监听端口 |
| `server.routes` | number | 当前配置组包含的路由数量 |
| `mockApis.total` | number | Mock 接口总数 |
| `mockApis.enabled` | number | 已启用的 Mock 接口数量 |
| `usage.description` | string | 使用说明 |
| `usage.example` | string | 使用示例 |
| `routes` | array | 当前配置组下的路由列表 |
| `routes[].pathPrefix` | string | 路由前缀 |
| `routes[].targetBaseUrl` | string | 该路由的上游目标地址 |
| `routes[].mockApis` | number | 该路由下的 Mock 数量 |
| `examples` | array | 可直接访问的示例请求 |

## 使用场景

### 1. 检查服务是否正常运行

在浏览器中访问 `http://localhost:8888/`，如果能看到 JSON 响应，说明服务正常运行。

### 2. 查看当前 Mock 配置

通过返回的 `routes` 和 `examples` 数组，可以快速了解当前有哪些路由，以及哪些示例地址可直接访问。

### 3. 获取服务配置信息

通过 `server` 与 `routes` 信息，可以快速确认端口、路由数量和各路由的上游目标。

### 4. 用于健康检查

可以将根路径作为健康检查端点，返回 200 状态码表示服务正常。

## 实现细节

### 代码位置
`src/main/kotlin/org/zhongmiao/interceptwave/util/HttpWelcomeUtil.kt`

### 关键方法
```kotlin
fun buildWelcomeJson(config: ProxyConfig): String
```

### 触发条件
当请求路径为 `/`，或在启用 `stripPrefix` 时访问对应前缀根路径，服务会返回欢迎页，而不是继续向上游转发。

### 响应头
- `Content-Type: application/json; charset=UTF-8`
- `Access-Control-Allow-Origin: *`

## 注意事项

1. **仅特定入口返回欢迎页**：通常访问 `/` 会返回欢迎页；若启用了 `stripPrefix`，部分前缀根路径也会返回欢迎页
2. **JSON 格式**：返回的是纯 JSON，便于程序和脚本解析
3. **CORS 支持**：响应中包含 CORS 头，可直接从前端访问
4. **实时配置**：显示的是当前实际生效的配置快照

## 示例：使用 curl 测试

```bash
# 查看欢迎页面
curl http://localhost:8888/

# 格式化输出
curl http://localhost:8888/ | jq

# 只查看路由列表
curl http://localhost:8888/ | jq '.routes'

# 查看服务配置
curl http://localhost:8888/ | jq '.server'
```

## 改进建议（未来可以添加）

1. **HTML 版本**：根据 Accept 头返回 HTML 或 JSON
2. **统计信息**：添加请求次数、响应时间等统计
3. **在线测试**：在 HTML 版本中添加接口测试功能
4. **配置编辑**：在 HTML 版本中支持在线编辑配置
5. **日志查看**：显示最近的请求日志
