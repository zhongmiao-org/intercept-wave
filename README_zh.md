<div align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="Intercept Wave Logo" width="128" height="128">

  # Intercept Wave for IntelliJ IDEA

  [![Build](https://github.com/zhongmiao-org/intercept-wave/workflows/Build/badge.svg)](https://github.com/zhongmiao-org/intercept-wave/actions)
  [![Version](https://img.shields.io/jetbrains/plugin/v/28728.svg?style=flat-square)](https://plugins.jetbrains.com/plugin/28728-intercept-wave)
  [![Downloads](https://img.shields.io/jetbrains/plugin/d/28728.svg?style=flat-square)](https://plugins.jetbrains.com/plugin/28728-intercept-wave)
  [![Rating](https://img.shields.io/jetbrains/plugin/r/rating/28728?style=flat-square)](https://plugins.jetbrains.com/plugin/28728-intercept-wave)
  [![License](https://img.shields.io/github/license/zhongmiao-org/intercept-wave?style=flat-square)](https://github.com/zhongmiao-org/intercept-wave/blob/main/LICENSE)

  [English](./README.md) | 简体中文
</div>

## 插件简介

Intercept Wave 是一个功能强大的 IntelliJ IDEA 插件，集成了类似 **Nginx** 和 **Charles** 的代理与拦截功能，专为本地开发环境设计。它能够智能拦截 HTTP 请求，既可以返回自定义的 Mock 数据，也可以作为代理服务器转发真实请求到原始服务器。

### ✨ v2.0 新特性：多服务代理

- 📑 **标签式界面**：在独立标签页中管理多个代理配置组
- 🚀 **多配置组**：同时运行多个 Mock 服务，每个服务独立端口
- 🏗️ **微服务就绪**：完美适配微服务架构（如用户服务 8888 端口，订单服务 8889 端口）
- 🔄 **快速切换**：通过标签页快速切换和管理不同的服务配置
- 🌍 **多环境支持**：轻松管理开发、测试、预发布等多个环境

### 核心能力

**智能拦截与代理**：
- 🎯 配置劫持前缀（如 `/api`），精准拦截指定路径的请求
- 🔄 **有 Mock 配置**：返回预设的 Mock 数据，实现离线开发
- 🌐 **无 Mock 配置**：作为代理服务器，携带完整的 HTTP 请求头转发到原始服务器，获取真实数据
- 🔀 智能路径匹配：支持前缀去除，简化配置

**开发友好特性**：
- 👥 **目标用户**：前端工程师、测试工程师、全栈开发者
- 🎨 可视化配置界面，无需手写配置文件
- 💾 配置持久化，项目级别隔离
- 🌐 自动处理 CORS 跨域问题
- ⏱️ 支持网络延迟模拟
- 🔧 自定义响应状态码和响应头
- 🍪 全局 Cookie 支持，轻松处理需要认证的接口

### 典型使用场景

1. **微服务开发**：同时 Mock 多个微服务（用户服务、订单服务、支付服务等）
2. **前端独立开发**：后端接口未就绪时，配置 Mock 数据继续开发
3. **接口测试**：快速切换不同的返回数据，测试各种边界情况
4. **多环境调试**：同时配置开发、测试、预发布等多个环境
5. **本地调试**：部分接口使用 Mock，其他接口代理到测试服务器
6. **网络模拟**：模拟慢速网络或接口超时场景
7. **跨域开发**：自动添加 CORS 头，解决前端开发中的跨域问题

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

### 1. 打开工具窗口

1. 在 IntelliJ IDEA 中打开项目
2. 点击左侧工具栏的 "Intercept Wave" 图标
3. 工具窗口会显示所有配置的代理组，以标签页形式展示

### 2. 管理配置组（v2.0 新特性）

工具窗口顶部提供全局操作：
- **启动所有**: 启动所有已启用的配置组
- **停止所有**: 停止所有运行中的服务
- **配置**: 打开配置对话框，管理所有配置组

#### 标签页说明
- 每个标签代表一个配置组（如"用户服务"、"订单服务"）
- 显示服务名称、端口号、启用状态
- 点击标签切换到对应的服务控制面板
- **[+]** 标签：点击快速打开配置对话框添加新配置组

#### 单个服务控制
每个标签页面板显示：
- ☑/☐ **启用状态**: 显示该配置组是否启用
- 🟢/⚫ **运行状态**: 运行中 / 已停止
- 🔗 **访问地址**: 服务运行时的访问 URL
- **启动服务** / **停止服务**: 控制单个服务的启停
- **当前配置**: 显示端口、拦截前缀、目标地址、Mock API 列表等详细信息

### 3. 配置代理组

点击 "配置" 按钮，打开配置对话框：

#### 配置组管理（多标签页界面）
- 每个标签页代表一个配置组
- 标签显示格式：`配置组名称 (:端口号)`
- **+ 新增配置组**: 添加新的配置组
- **删除当前配置组**: 删除当前选中的配置组（至少保留一个）
- **← 左移** / **右移 →**: 调整配置组的显示顺序

#### 配置组设置
每个配置组包含以下设置：

**基本配置**：
- **配置组名称**: 自定义名称（如"用户服务"、"订单服务"）
- **端口号**: 该服务监听的端口（如 8888、8889）
- **拦截前缀**: 需要拦截的接口路径前缀（默认：/api）
- **目标地址**: 原始服务器的基础 URL（例如：http://localhost:8080）
- **剥离前缀**: 启用后，匹配时会去掉拦截前缀
  - 例如：请求 `/api/user` 会匹配 Mock 路径 `/user`
- **全局 Cookie**: 配置全局 Cookie 值（例如：sessionId=abc123; userId=456）
- **启用该配置组**: 勾选后，该配置组才会被"启动所有"包含

#### Mock 接口配置
1. 点击 "添加接口" 按钮
2. 填写以下信息：
   - **接口路径**: 例如 `/api/user/info` 或 `/user/info`（取决于是否剥离前缀）
   - **HTTP方法**: ALL、GET、POST、PUT、DELETE、PATCH
   - **状态码**: HTTP 响应状态码（默认：200）
   - **延迟(毫秒)**: 模拟网络延迟（默认：0）
   - **Mock数据**: JSON 格式的响应数据
   - **启用**: 是否启用此 Mock 配置
   - **使用全局Cookie**: 启用后，会在响应中包含配置的全局 Cookie

3. 点击 "格式化JSON" 按钮可以格式化 Mock 数据
4. 点击 "OK" 保存配置

### 4. 启动服务

有两种方式启动服务：

**方式 1：启动所有服务**
- 点击工具窗口顶部的 "启动所有" 按钮
- 自动启动所有已启用的配置组

**方式 2：启动单个服务**
- 切换到对应的标签页
- 点击该标签页中的 "启动服务" 按钮
- 只启动当前选中的服务

服务启动成功后：
- 状态显示为 "● 运行中"
- 显示访问地址（如 http://localhost:8888）
- Run 工具窗口会显示实时日志

## 使用场景

### 场景 1: 微服务开发（v2.0 新特性）

同时 Mock 多个微服务，每个服务独立端口运行：

**配置组 1 - 用户服务（端口 8888）**：
```javascript
// 前端代码访问用户服务
fetch('http://localhost:8888/api/user/info')
  .then(res => res.json())
  .then(data => console.log(data));
```

**配置组 2 - 订单服务（端口 8889）**：
```javascript
// 前端代码访问订单服务
fetch('http://localhost:8889/order-api/orders')
  .then(res => res.json())
  .then(data => console.log(data));
```

**配置组 3 - 支付服务（端口 8890）**：
```javascript
// 前端代码访问支付服务
fetch('http://localhost:8890/pay-api/checkout')
  .then(res => res.json())
  .then(data => console.log(data));
```

所有服务可以同时运行，互不干扰。点击"启动所有"按钮一次性启动所有服务！

### 场景 2: Mock 特定接口

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

### 场景 3: 转发未配置的接口

```javascript
// 这个接口没有配置 Mock，会自动转发到原始服务器
fetch('http://localhost:8888/api/posts')
  .then(res => res.json())
  .then(data => console.log(data));
```

如果配置了原始接口地址为 `http://api.example.com`，则实际请求：`http://api.example.com/api/posts`

### 场景 4: 模拟需要认证的接口

1. 在全局配置中设置 Cookie：`sessionId=abc123; userId=456`
2. 在 Mock 接口配置中勾选 "使用全局Cookie"
3. Mock 接口响应时会自动添加 `Set-Cookie` 响应头

### 场景 5: 模拟网络延迟

在 Mock 配置中设置延迟时间（例如：1000ms），模拟慢速网络环境。

### 场景 6: 测试不同的响应状态码

配置不同的状态码（404、500等）来测试前端的错误处理逻辑。

## 配置文件

所有配置保存在项目根目录的 `.intercept-wave` 文件夹中：

```
.intercept-wave/
└── config.json           # 全局配置和多配置组
```

### config.json 示例（v2.0 格式）

```json
{
  "version": "2.0",
  "proxyGroups": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "用户服务",
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
          "mockData": "{\"code\":0,\"data\":{\"name\":\"张三\"}}",
          "method": "GET",
          "statusCode": 200,
          "useCookie": true,
          "delay": 0
        }
      ]
    },
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "name": "订单服务",
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

### 配置迁移说明

**从 v1.x 升级到 v2.0**：
- 旧配置会自动迁移，并备份为 `config.json.backup`
- 旧配置会转换为新结构中的"默认配置"组
- 迁移过程完全自动，无需手动操作

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
A: 启动 Mock 服务后，IDEA 底部的 Run 工具窗口会自动出现 "Intercept Wave Mock Server" 标签页，展示所有请求的实时彩色日志，包括时间戳、请求方法、路径，以及响应是来自 Mock 还是代理转发。

### Q: 支持 HTTPS 吗？
A: 当前版本仅支持 HTTP，HTTPS 支持在计划中。

### Q: 全局 Cookie 如何工作？
A: 在全局配置中设置 Cookie 值后，在 Mock 接口配置中勾选"使用全局Cookie"，响应时会通过 `Set-Cookie` 响应头返回给客户端。

## 开发计划

- [ ] 支持 HTTPS
- [ ] 支持 WebSocket Mock
- [x] 请求日志查看器（已在 Run 工具窗口中实现）
- [ ] 导入/导出配置
- [ ] Mock 数据模板库
- [ ] 支持自定义请求头

## 反馈与贡献

如有问题或建议，欢迎提交 [Issue](https://github.com/zhongmiao-org/intercept-wave/issues) 或 [Pull Request](https://github.com/zhongmiao-org/intercept-wave/pulls)！

## 许可证

本项目基于 [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template) 开发。