# Mock 服务欢迎页面功能

## 功能说明

当直接访问 Mock 服务的根路径（例如 `http://localhost:8888/`）时，不再返回错误信息，而是返回一个友好的 JSON 格式欢迎页面，显示服务状态和配置信息。

## 欢迎页面返回示例

### 无 Mock 配置时

```json
{
  "status": "running",
  "message": "Intercept Wave Mock 服务运行中",
  "server": {
    "port": 8888,
    "baseUrl": "http://localhost:8080",
    "interceptPrefix": "/api"
  },
  "mockApis": {
    "total": 0,
    "enabled": 0
  },
  "usage": {
    "description": "访问配置的 Mock 接口路径即可获取 Mock 数据",
    "example": "GET http://localhost:8888/api/your-api-path",
    "configPath": "请在 IntelliJ IDEA 的 Intercept Wave 工具窗口中配置 Mock 接口"
  },
  "apis": []
}
```

### 有 Mock 配置时

```json
{
  "status": "running",
  "message": "Intercept Wave Mock 服务运行中",
  "server": {
    "port": 8888,
    "baseUrl": "http://localhost:8080",
    "interceptPrefix": "/api"
  },
  "mockApis": {
    "total": 3,
    "enabled": 2
  },
  "usage": {
    "description": "访问配置的 Mock 接口路径即可获取 Mock 数据",
    "example": "GET http://localhost:8888/api/your-api-path",
    "configPath": "请在 IntelliJ IDEA 的 Intercept Wave 工具窗口中配置 Mock 接口"
  },
  "apis": [
    {"path": "/api/user/info", "method": "GET", "enabled": true},
    {"path": "/api/posts", "method": "ALL", "enabled": true},
    {"path": "/api/login", "method": "POST", "enabled": false}
  ]
}
```

## 返回字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `status` | string | 服务状态，固定为 "running" |
| `message` | string | 欢迎消息 |
| `server.port` | number | Mock 服务监听端口 |
| `server.baseUrl` | string | 原始接口的基础 URL |
| `server.interceptPrefix` | string | 拦截的接口路径前缀 |
| `mockApis.total` | number | Mock 接口总数 |
| `mockApis.enabled` | number | 已启用的 Mock 接口数量 |
| `usage.description` | string | 使用说明 |
| `usage.example` | string | 使用示例 |
| `usage.configPath` | string | 配置路径说明 |
| `apis` | array | 所有 Mock 接口列表 |
| `apis[].path` | string | 接口路径 |
| `apis[].method` | string | HTTP 方法 |
| `apis[].enabled` | boolean | 是否启用 |

## 使用场景

### 1. 检查服务是否正常运行

在浏览器中访问 `http://localhost:8888/`，如果能看到 JSON 响应，说明服务正常运行。

### 2. 查看当前 Mock 配置

通过返回的 `apis` 数组，可以快速了解当前配置了哪些 Mock 接口。

### 3. 获取服务配置信息

通过 `server` 对象，可以查看端口、原始接口地址等配置信息。

### 4. 用于健康检查

可以将根路径作为健康检查端点，返回 200 状态码表示服务正常。

## 实现细节

### 代码位置
`src/main/kotlin/org/zhongmiao/interceptwave/services/MockServerService.kt`

### 关键方法
```kotlin
private fun handleWelcomePage(exchange: HttpExchange, config: MockConfig)
```

### 触发条件
当请求路径为 `/` 或空字符串时，返回欢迎页面，不再尝试转发到原始服务器。

### 响应头
- `Content-Type: application/json; charset=UTF-8`
- `Access-Control-Allow-Origin: *`

## 注意事项

1. **仅根路径生效**：只有访问 `/` 才会返回欢迎页面，其他路径正常处理
2. **JSON 格式**：返回纯 JSON 数据，方便程序解析
3. **CORS 支持**：添加了 CORS 头，可以被前端直接访问
4. **实时配置**：显示的是当前实际生效的配置，而不是缓存数据

## 示例：使用 curl 测试

```bash
# 查看欢迎页面
curl http://localhost:8888/

# 格式化输出
curl http://localhost:8888/ | jq

# 只查看 API 列表
curl http://localhost:8888/ | jq '.apis'

# 查看服务配置
curl http://localhost:8888/ | jq '.server'
```

## 改进建议（未来可以添加）

1. **HTML 版本**：根据 Accept 头返回 HTML 或 JSON
2. **统计信息**：添加请求次数、响应时间等统计
3. **在线测试**：在 HTML 版本中添加接口测试功能
4. **配置编辑**：在 HTML 版本中支持在线编辑配置
5. **日志查看**：显示最近的请求日志