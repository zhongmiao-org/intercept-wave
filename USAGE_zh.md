# Intercept Wave 使用指南

[English](./USAGE.md)

## 功能概述

Intercept Wave 是一个 IntelliJ IDEA 插件，用于在本地开发中统一处理 HTTP Mock、代理转发和 WebSocket 调试。你可以用它来：
- 拦截指定接口并返回自定义 Mock 数据
- 将未命中的请求自动转发到上游服务
- 为不同服务或环境维护多个配置组
- 自动补充 CORS 头，减少前端联调阻力

## 快速开始

### 1. 启动插件

1. 在 IntelliJ IDEA 中打开项目
2. 点击右侧工具栏的 "Intercept Wave" 图标
3. 选择一个配置组后点击“启动服务”，或使用顶部的“启动所有”
4. 服务启动成功后，会显示本地访问地址，例如 `http://localhost:8888`

### 2. 配置 Mock 服务

点击 "配置" 按钮，打开配置对话框：

#### 配置组基础信息
- **协议类型**：`HTTP` 或 `WS`
- **配置组名称**：例如“用户服务”“订单服务”
- **端口号**：当前配置组监听的本地端口
- **启用该配置组**：是否允许被“启动所有”统一拉起

#### HTTP 配置
- **全局 Cookie**：需要时可统一为 Mock 响应附带 Cookie
- **路由列表**：一个 HTTP 配置组可维护多条路由，每条路由都包含：
  - **路径前缀**：如 `/api`、`/order-api`
  - **目标地址**：如 `http://localhost:8080`
  - **剥离前缀**：是否在匹配和转发前移除当前路由前缀
  - **重写目标路径**：可选，剥离前缀后追加的目标路径基底，如 `/v1`
  - **SPA 兜底路径**：可选，HTML 导航请求返回 404 时重试的路径，如 `/index.html`
  - **启用 Mock**：是否优先使用该路由下的 Mock 列表

路由重写适合迁移本地 nginx 规则。例如配置 `pathPrefix="/backend"`、`stripPrefix=true`、`rewriteTargetPath="/v1"` 后，访问 `/backend/users?active=true` 会按 `/v1/users?active=true` 进行匹配和转发。该能力用于本地开发网关，不是生产 nginx 替代方案。

前端开发服务器代理示例：配置后端路由 `pathPrefix="/api"`、`stripPrefix=true`、`targetBaseUrl="http://localhost:9000"`，再配置前端路由 `pathPrefix="/"`、`stripPrefix=false`、`targetBaseUrl="http://localhost:5173"`、`spaFallbackPath="/index.html"`、`enableMock=false`。之后 `/api/users` 进入后端，`/`、`/assets/app.js`、`/dashboard/settings` 进入前端开发服务器。

#### Nginx 迁移配置片段

用一个 HTTP 配置组作为本地网关端口，再用多条路由对应 nginx 的多个 `location`。该能力面向本地开发，不用于生产流量。

前端开发服务器 + 后端 API：

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

多后端服务 + 前端兜底：

```json
[
  { "name": "API", "pathPrefix": "/api", "targetBaseUrl": "http://localhost:9000", "stripPrefix": true, "rewriteTargetPath": "", "spaFallbackPath": "", "enableMock": true },
  { "name": "Auth", "pathPrefix": "/auth", "targetBaseUrl": "http://localhost:9010", "stripPrefix": true, "rewriteTargetPath": "", "spaFallbackPath": "", "enableMock": true },
  { "name": "Admin API", "pathPrefix": "/admin-api", "targetBaseUrl": "http://localhost:9020", "stripPrefix": true, "rewriteTargetPath": "", "spaFallbackPath": "", "enableMock": true },
  { "name": "Frontend", "pathPrefix": "/", "targetBaseUrl": "http://localhost:5173", "stripPrefix": false, "rewriteTargetPath": "", "spaFallbackPath": "/index.html", "enableMock": false }
]
```

常见问题：
- **端口占用**：修改配置组端口，或停止已经监听该端口的进程。
- **SPA 刷新返回 404**：前端路由保持 `pathPrefix="/"`，设置 `enableMock=false`，并用 `spaFallbackPath="/index.html"` 处理 HTML 导航请求。
- **前缀或重写不符合预期**：`stripPrefix` 会先于 `rewriteTargetPath` 执行；Mock 路径和转发路径都使用重写后的路由本地路径。
- **CORS 或请求头行为不符合预期**：本地网关会为开发场景追加默认 CORS 头；路由级请求/响应头覆盖能力另行跟进。
- **静态 dist 和 HTTPS**：静态构建产物服务由 [#153](https://github.com/zhongmiao-org/intercept-wave/issues/153) 跟进，HTTPS 监听支持由 [#151](https://github.com/zhongmiao-org/intercept-wave/issues/151) 跟进。

相关 roadmap：IntelliJ [#150](https://github.com/zhongmiao-org/intercept-wave/issues/150)、VS Code [#39](https://github.com/zhongmiao-org/intercept-wave-vscode/issues/39)、VS Code 文档 [#46](https://github.com/zhongmiao-org/intercept-wave-vscode/issues/46)。

#### Mock 接口配置
1. 点击 "添加接口" 按钮
2. 填写以下信息：
   - **接口路径**：例如 `/user/info` 或 `/api/user/info`，取决于当前路由是否启用了“剥离前缀”
   - **HTTP 方法**：ALL、GET、POST、PUT、DELETE、PATCH
   - **状态码**：HTTP 响应状态码（默认：200）
   - **延迟（毫秒）**：模拟网络延迟（默认：0）
   - **响应模板**：可选，内置成功、分页、空数据、错误或登录过期响应结构
   - **Mock 数据**：JSON 格式的响应数据
   - **启用**：是否启用该 Mock 规则
   - **使用全局 Cookie**：是否在响应中附带该配置组的全局 Cookie

3. 需要用模板替换可编辑 Mock 数据区域时，点击“应用模板”
4. 点击“格式化 JSON”按钮，可将内容整理为更易阅读的格式
5. 点击 "OK" 保存配置

## 使用场景

### 场景 1: Mock 特定接口

```javascript
// 前端代码
fetch('http://localhost:8888/api/user/info')
  .then(res => res.json())
  .then(data => console.log(data));
```

配置：
- 路径: `/api/user/info`
- 方法: `GET`
- Mock 数据:
```json
{
  "code": 0,
  "data": {
    "id": 1,
    "name": "张三",
    "email": "zhangsan@example.com"
  },
  "message": "success"
}
```

### 场景 2: 转发未配置的接口

```javascript
// 这个接口没有配置 Mock，会自动转发到原始服务器
fetch('http://localhost:8888/api/posts')
  .then(res => res.json())
  .then(data => console.log(data));
```

如果当前路由的目标地址配置为 `http://api.example.com`，则实际请求会转发到：
`http://api.example.com/api/posts`

### 场景 3: 模拟网络延迟

在 Mock 配置中设置延迟时间（例如：1000ms），模拟慢速网络环境。

### 场景 4: 测试不同的响应状态码

配置不同的状态码（404、500等）来测试前端的错误处理逻辑。

### 场景 5: WebSocket 调试

当配置组协议类型为 `WS` 时，你可以：
- 在本地启动 `ws://` 服务，必要时启用本地 `wss://`
- 将消息桥接到上游 `ws://` / `wss://` 服务
- 配置自动推送规则，或在工具窗口中手动发送消息

## 配置文件

所有配置保存在项目根目录的 `.intercept-wave` 文件夹中：

```
.intercept-wave/
└── config.json           # 根配置、配置组、HTTP 路由、Mock 规则、WS 规则
```

### config.json 示例

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
              "mockData": "{\"code\":0,\"data\":{\"name\":\"张三\"}}",
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

## 高级功能

### 全局 Cookie

在配置组中设置 Cookie 值后，可为指定 Mock 规则开启“使用全局 Cookie”，响应时会自动带上 `Set-Cookie`。

### CORS 支持

Mock 服务器自动添加以下 CORS 头：
```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization
```

### 代理模式

未配置 Mock 的接口会自动转发到上游服务器，并尽量保留：
- 原始请求头
- User-Agent
- 请求体（POST/PUT 等）
- Cookie（如有）

## 注意事项

1. **端口占用**: 确保配置的端口未被其他程序占用
2. **配置修改**：修改配置后，如服务正在运行，可能需要重新加载或重启对应配置组
3. **项目关闭**：关闭项目时，相关服务会自动停止
4. **安全性**：本工具仅用于本地开发和调试，不建议在生产环境中使用

## 常见问题

### Q: 服务启动失败怎么办？
A: 先检查端口是否被占用，必要时调整配置组端口。

### Q: 接口没有被 Mock？
A: 请确认请求命中了正确路由，Mock 路径填写正确，并且对应规则已启用。

### Q: 如何查看请求日志？
A: 启动服务后，可在 IntelliJ IDEA 底部的 Run 工具窗口查看实时请求日志。

### Q: 支持 HTTPS 吗？
A: HTTP 配置组当前提供本地 HTTP 入口；WS 配置组支持本地 `ws://`，并可通过 PKCS#12 证书启用本地 `wss://`。

## 反馈与贡献

如有问题或建议，欢迎提交 Issue 或 Pull Request。
