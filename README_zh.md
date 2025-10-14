# Intercept Wave

![Build](https://github.com/zhongmiao-org/intercept-wave/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/28704.svg)](https://plugins.jetbrains.com/plugin/28704-intercept-wave)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/28704.svg)](https://plugins.jetbrains.com/plugin/28704-intercept-wave)

[English](./README.md) | 简体中文

## 插件简介

Intercept Wave 是一个强大的 IntelliJ IDEA 插件，为开发者提供本地 HTTP Mock 服务功能。它能够拦截特定接口并返回预设的 Mock 数据，同时将未配置的接口转发到原始服务器，完美支持前后端分离开发场景。

**主要特性**：
- 🎯 灵活的接口拦截与 Mock 数据配置
- 🔀 智能代理：未配置接口自动转发到原始服务器
- 🌐 自动处理 CORS 跨域问题
- ⏱️ 支持网络延迟模拟
- 🎨 可视化配置界面
- 💾 配置持久化存储
- 🔧 支持自定义响应头和状态码
- 🍪 全局 Cookie 配置，支持需要认证的接口

## 功能概述

Intercept Wave 提供以下核心功能：

- **接口拦截**: 拦截特定接口并返回配置的 Mock 数据
- **代理转发**: 自动转发未配置的接口到原始服务器
- **CORS 支持**: 自动添加 CORS 头，解决跨域问题
- **请求保留**: 保留原始请求头和 User-Agent
- **延迟模拟**: 模拟网络延迟，测试慢速网络环境
- **状态码测试**: 配置不同状态码测试错误处理逻辑
- **前缀过滤**: 支持配置前缀过滤，简化接口访问路径
- **全局 Cookie**: 配置全局 Cookie，支持需要认证的 Mock 接口

## 安装

### 使用 IDE 内置插件系统

<kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>搜索 "Intercept Wave"</kbd> > <kbd>Install</kbd>

### 使用 JetBrains Marketplace

访问 [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28704-intercept-wave) 并点击 <kbd>Install to ...</kbd> 按钮安装。

或下载 [最新版本](https://plugins.jetbrains.com/plugin/28704-intercept-wave/versions) 手动安装：
<kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

### 手动安装

从 [GitHub Releases](https://github.com/zhongmiao-org/intercept-wave/releases/latest) 下载最新版本，然后手动安装：
<kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## 快速开始

### 1. 启动 Mock 服务

1. 在 IntelliJ IDEA 中打开项目
2. 点击左侧工具栏的 "Intercept Wave" 图标
3. 在工具窗口中点击 "启动服务" 按钮
4. 服务启动成功后，会显示访问地址（默认：http://localhost:8888）

### 2. 配置 Mock 接口

点击 "配置" 按钮，打开配置对话框：

#### 全局配置
- **Mock端口**: 本地 Mock 服务监听的端口（默认：8888）
- **拦截前缀**: 需要拦截的接口路径前缀（默认：/api）
- **原始接口地址**: 原始服务器的基础 URL（例如：http://localhost:8080）
- **过滤/取消前缀**: 启用后，访问 `localhost:8888/user/info` 会匹配 `/api/user/info`
- **全局 Cookie**: 配置全局 Cookie 值，可被 Mock 接口使用（例如：sessionId=abc123; userId=456）

#### Mock 接口配置
1. 点击 "添加接口" 按钮
2. 填写以下信息：
   - **接口路径**: 例如 `/api/user/info`
   - **HTTP方法**: ALL、GET、POST、PUT、DELETE、PATCH
   - **状态码**: HTTP 响应状态码（默认：200）
   - **延迟(毫秒)**: 模拟网络延迟（默认：0）
   - **Mock数据**: JSON 格式的响应数据
   - **启用**: 是否启用此 Mock 配置
   - **使用全局Cookie**: 启用后，会在响应中包含配置的全局 Cookie

3. 点击 "格式化JSON" 按钮可以格式化 Mock 数据
4. 点击 "OK" 保存配置

## 使用场景

### 场景 1: Mock 特定接口

```javascript
// 前端代码
fetch('http://localhost:8888/api/user/info')
  .then(res => res.json())
  .then(data => console.log(data));
```

**配置**：
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

如果配置了原始接口地址为 `http://api.example.com`，则实际请求：`http://api.example.com/api/posts`

### 场景 3: 模拟需要认证的接口

1. 在全局配置中设置 Cookie：`sessionId=abc123; userId=456`
2. 在 Mock 接口配置中勾选 "使用全局Cookie"
3. Mock 接口响应时会自动添加 `Set-Cookie` 响应头

### 场景 4: 模拟网络延迟

在 Mock 配置中设置延迟时间（例如：1000ms），模拟慢速网络环境。

### 场景 5: 测试不同的响应状态码

配置不同的状态码（404、500等）来测试前端的错误处理逻辑。

## 配置文件

所有配置保存在项目根目录的 `.intercept-wave` 文件夹中：

```
.intercept-wave/
└── config.json           # 全局配置和接口映射
```

### config.json 示例

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
      "mockData": "{\"code\":0,\"data\":{\"name\":\"张三\"}}",
      "method": "GET",
      "statusCode": 200,
      "useCookie": true,
      "delay": 0
    }
  ]
}
```

## 高级功能

### 全局 Cookie 配置

在全局配置中设置 Cookie 值，多个 Cookie 用分号分隔：

```
sessionId=abc123; userId=456; token=xyz789
```

然后在需要 Cookie 的 Mock 接口中勾选 "使用全局Cookie"，响应时会自动添加 `Set-Cookie` 头。

### CORS 支持

Mock 服务器自动添加以下 CORS 头：
```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Content-Type, Authorization
```

### 代理模式

未配置 Mock 的接口会自动转发到原始服务器，并保留：
- 原始请求头
- User-Agent
- 请求体（POST/PUT 等）
- Cookie（如果有）

## 欢迎页面

访问 Mock 服务根路径（`http://localhost:8888/`）会返回服务状态和配置信息：

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
  "apis": [
    {"path": "/api/user/info", "method": "GET", "enabled": true},
    {"path": "/api/posts", "method": "ALL", "enabled": true}
  ]
}
```

## 注意事项

1. **端口占用**: 确保配置的端口未被其他程序占用
2. **配置修改**: 修改配置后，如果服务正在运行会自动停止
3. **项目关闭**: 关闭项目时 Mock 服务会自动停止
4. **安全性**: 此工具仅用于本地开发环境，不要在生产环境使用

## 常见问题

### Q: 服务启动失败怎么办？
A: 检查端口是否被占用，可以修改配置中的端口号。

### Q: 接口没有被 Mock？
A: 确认接口路径完全匹配，且 Mock 配置已启用。

### Q: 如何查看请求日志？
A: 打开 IDEA 的 Event Log 或 Console，可以看到请求日志。

### Q: 支持 HTTPS 吗？
A: 当前版本仅支持 HTTP，HTTPS 支持在计划中。

### Q: 全局 Cookie 如何工作？
A: 在全局配置中设置 Cookie 值后，在 Mock 接口配置中勾选"使用全局Cookie"，响应时会通过 `Set-Cookie` 响应头返回给客户端。

## 开发计划

- [ ] 支持 HTTPS
- [ ] 支持 WebSocket Mock
- [ ] 请求日志查看器
- [ ] 导入/导出配置
- [ ] Mock 数据模板库
- [ ] 支持自定义请求头

## 反馈与贡献

如有问题或建议，欢迎提交 [Issue](https://github.com/zhongmiao-org/intercept-wave/issues) 或 [Pull Request](https://github.com/zhongmiao-org/intercept-wave/pulls)！

## 许可证

本项目基于 [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template) 开发。