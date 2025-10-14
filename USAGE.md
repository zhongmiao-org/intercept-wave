# Intercept Wave - Mock 服务使用指南

## 功能概述

Intercept Wave 是一个 IntelliJ IDEA 插件，提供本地 HTTP Mock 服务功能，用于：
- 拦截特定接口并返回配置的 Mock 数据
- 转发未配置的接口到原始服务器（代理功能）
- 自动添加 CORS 头，解决跨域问题
- 保留原始请求头和 User-Agent

## 快速开始

### 1. 启动插件

1. 在 IntelliJ IDEA 中打开项目
2. 点击右侧工具栏的 "Intercept Wave" 图标
3. 在工具窗口中点击 "启动服务" 按钮
4. 服务启动成功后，会显示访问地址（默认：http://localhost:8888）

### 2. 配置 Mock 服务

点击 "配置" 按钮，打开配置对话框：

#### 全局配置
- **Mock端口**: 本地 Mock 服务监听的端口（默认：8888）
- **拦截前缀**: 需要拦截的接口路径前缀（默认：/api）
- **原始接口地址**: 原始服务器的基础 URL（例如：http://localhost:8080）

#### Mock接口配置
1. 点击 "添加接口" 按钮
2. 填写以下信息：
   - **接口路径**: 例如 `/api/user/info`
   - **HTTP方法**: ALL、GET、POST、PUT、DELETE、PATCH
   - **状态码**: HTTP 响应状态码（默认：200）
   - **延迟(毫秒)**: 模拟网络延迟（默认：0）
   - **Mock数据**: JSON 格式的响应数据
   - **启用**: 是否启用此 Mock 配置

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

配置：
- 路径: `/api/user/info`
- 方法: `GET`
- Mock数据:
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

如果配置了原始接口地址为 `http://api.example.com`，则实际请求：
`http://api.example.com/api/posts`

### 场景 3: 模拟网络延迟

在 Mock 配置中设置延迟时间（例如：1000ms），模拟慢速网络环境。

### 场景 4: 测试不同的响应状态码

配置不同的状态码（404、500等）来测试前端的错误处理逻辑。

## 配置文件

所有配置保存在项目根目录的 `.intercept-wave` 文件夹中：

```
.intercept-wave/
├── config.json           # 全局配置和接口映射
├── api_user_info.json    # 可选：独立的 Mock 数据文件
└── api_posts.json
```

### config.json 示例

```json
{
  "port": 8888,
  "interceptPrefix": "/api",
  "baseUrl": "http://localhost:8080",
  "mockApis": [
    {
      "path": "/api/user/info",
      "enabled": true,
      "mockData": "{\"code\":0,\"data\":{\"name\":\"张三\"}}",
      "method": "GET",
      "statusCode": 200,
      "headers": {},
      "delay": 0
    }
  ]
}
```

## 高级功能

### 自定义响应头

在 Mock 接口配置中可以添加自定义响应头：

```json
{
  "path": "/api/custom",
  "headers": {
    "X-Custom-Header": "value",
    "Cache-Control": "no-cache"
  }
}
```

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

## 注意事项

1. **端口占用**: 确保配置的端口未被其他程序占用
2. **配置修改**: 修改配置后需要重启 Mock 服务
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

## 开发计划

- [ ] 支持 HTTPS
- [ ] 支持 WebSocket Mock
- [ ] 请求日志查看器
- [ ] 导入/导出配置
- [ ] Mock 数据模板库

## 反馈与贡献

如有问题或建议，欢迎提交 Issue 或 Pull Request！