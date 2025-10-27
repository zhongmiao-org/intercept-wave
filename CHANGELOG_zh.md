<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Intercept Wave 更新日志

> [English Changelog](./CHANGELOG.md) | [英文更新日志](./CHANGELOG.md)

## [Unreleased]

## [2.2.1]
### 🔧 CI/CD
- 🔧 发布工作流第一步执行 `patchChangelog`，确保打包到插件市场的 changeNotes 与当前版本一致。
- 🚀 发布成功后检出 `main`，再次执行 changelog 更新，并创建 PR 自动合并以更新 `main`。
- 🇨🇳 新增中文日志处理：自动将 Unreleased 归档到当前版本并插入新的 Unreleased，供下个版本编写。
- ✅ 仅在发布成功后才变更 `main` 分支的 changelog。

## [2.2.0]
### ✨ 新增
- 🌟 Mock 接口路径支持通配匹配
- 🔹 单段通配 `*`：如 `/a/b/*` 匹配 `/a/b/123`（不匹配 `/a/b/123/456`）
- 🔹 多段通配 `**`：如 `/a/b/**` 匹配 `/a/b/123` 与 `/a/b/123/456`（不匹配 `/a/b`）
- 🔹 段内通配：如 `/order/*/submit` 匹配 `/order/123/submit`
- 🧭 匹配优先级：精确路径 > 通配符更少 > 方法更具体（非 ALL） > 模式更长
- 🧩 `stripPrefix` 行为不变：启用时在去掉前缀后填写 `path`

### 🧪 测试与质量
- ✅ 新增通配单测：单星 `*`、双星 `**`、中段 `*`
- 🗒️ 代码注释改为中文，并通过拆分示例避免出现 `/**` 触发块注释的问题

### 📚 文档
- 📖 README（英文）：新增 “Path Matching Rules (Wildcards)” 章节，包含示例与优先级
- 🇨🇳 README_zh：新增 “路径匹配规则（通配符）” 说明与示例
- 📝 CHANGELOG：在 Unreleased 中记录上述改动

## [2.1.0]
### 🔄 变更
- **UI 组件迁移**: 从标准 Swing/AWT 组件迁移到 IntelliJ 平台 UI 组件
  - 将 `JPanel` 替换为 `JBPanel`，实现更好的主题集成
  - 将 `JCheckBox` 替换为 `JBCheckBox`，提供一致的 UI 样式
  - 所有对话框面板现在使用 JetBrains 组件，更好地支持 HiDPI 和主题

### ✨ 新增
- **HTTP 方法下拉选择器**: 在 Mock API 表格的方法列添加下拉选择器
  - 提供标准 HTTP 方法：GET、POST、PUT、DELETE、PATCH、HEAD、OPTIONS
  - 防止输入错误，确保方法选择的一致性
  - 使用 IntelliJ 平台的 `ComboBox` 组件

### 🧪 测试
- **扩展测试覆盖率**: 添加全面的单元测试以提高代码质量和可靠性
  - **ProxyConfigTest**: 23 个测试用例，覆盖 RootConfig 和 ProxyConfig 数据模型
    - 测试默认值、序列化、UUID 生成
    - 边界情况：特殊字符、端口边界值、字段变更
    - 验证 stripPrefix 行为和 useCookie 标志
  - **ProjectCloseListenerTest**: 3 个测试用例，覆盖项目关闭处理
    - 监听器实例化
    - 无运行服务器的安全处理
    - 多次调用安全性
  - **ConfigDialogTest**: 4 个测试用例，覆盖 UI 组件迁移
    - 验证 JetBrains UI 组件使用
    - 对话框实例化和销毁
    - JBPanel 和 JBCheckBox 可用性
  - **ConfigServiceTest**: 20 个测试用例，覆盖 v2.0 API（RootConfig 和 ProxyConfig）
    - Root 配置初始化和持久化
    - 代理组 CRUD 操作
    - Mock API 持久化
    - 配置重新加载和文件管理
  - **MockServerExecutorTest**: 9 个测试用例，覆盖 executor 和服务器管理
    - Executor 生命周期管理
    - 服务器状态和 URL 获取
    - ProxyConfig 创建和验证
- **测试总数**: 整个项目共 104 个单元测试（61 个平台测试 + 22 个标准 JUnit 测试 + 21 个集成测试）
  - 注意：在 CI 环境中，部分平台测试（MockServerServiceTest、ConfigServiceTest、ProjectCloseListenerTest）因需要 IDE 实例而被排除
- **覆盖领域**: 数据模型、服务、监听器、UI 组件、配置管理、服务器操作、executor 管理

## [2.0.0]

### 🎉 重大特性

#### 多服务代理支持
- ✨ **标签式界面**: 在独立标签页中组织多个代理配置
- 🚀 **多配置组**: 同时配置和管理多个服务
- 🎯 **独立端口管理**: 每个代理组可在独立端口运行
- ⚙️ **分组设置**: 为每个服务自定义端口、拦截前缀、目标地址等
- 🔄 **快速切换**: 通过标签页快速切换不同的代理配置
- 🏗️ **微服务就绪**: 完美适配微服务架构开发（如用户服务 8888 端口，订单服务 8889 端口）
- 🌍 **多环境支持**: 支持不同环境配置（开发、测试、预发布、生产）

#### 增强的用户界面
- 📑 **标签系统**: 工具窗口中显示所有配置组的可视化标签
- ➕ **快速添加**: 点击 "+" 标签即可快速添加新配置组
- ✏️ **配置对话框**: 功能完整的对话框，用于编辑所有代理组
- 🗑️ **组管理**: 直接从对话框删除配置组（至少保留一个）
- 🔘 **启用/禁用切换**: 通过复选框控制哪些组处于活动状态
- ⬅️➡️ **标签重排序**: 左移/右移标签以组织您的服务

#### 配置迁移
- 🔄 **自动迁移**: 旧的 v1.0 配置在插件升级时自动升级到 v2.0
- 💾 **创建备份**: 旧配置备份为 `config.json.backup`
- 📦 **保留数据**: 迁移过程中保留所有现有的 Mock API 和设置
- 🆔 **基于 UUID 的组**: 每个代理组获得唯一标识符以实现可靠管理
- 📢 **用户通知**: 迁移完成后显示成功通知

### ✨ 新增

#### 新数据模型
- 📋 **RootConfig**: 新的根配置结构，包含版本和 proxyGroups
- 🎯 **ProxyConfig**: 个人代理组配置，包含 UUID、名称、启用状态
- 🔗 **向后兼容**: 保留旧的 MockConfig 以实现兼容性（标记为 @Deprecated）

#### ConfigService 增强
- 📂 `getAllProxyGroups()`: 获取所有配置组
- ✅ `getEnabledProxyGroups()`: 获取已启用的配置组
- 🔍 `getProxyGroup(id)`: 通过 UUID 获取特定组
- ➕ `addProxyGroup(config)`: 添加新配置组
- 🔄 `updateProxyGroup(id, config)`: 更新现有组
- 🗑️ `deleteProxyGroup(id)`: 删除配置组
- 🔘 `toggleProxyGroup(id, enabled)`: 启用/禁用组
- ⬆️⬇️ `moveProxyGroup(fromIndex, toIndex)`: 重排序组
- 🏭 `createDefaultProxyConfig()`: 新配置的工厂方法

#### MockServerService 增强
- ▶️ `startServer(configId)`: 启动特定配置组的服务器
- ⏹️ `stopServer(configId)`: 停止特定配置组的服务器
- ▶️▶️ `startAllServers()`: 启动所有已启用的配置组
- ⏹️⏹️ `stopAllServers()`: 停止所有运行中的服务器
- ℹ️ `getServerStatus(configId)`: 获取服务器运行状态
- 🔗 `getServerUrl(configId)`: 获取服务器访问地址
- 📊 `getRunningServers()`: 获取所有运行中的服务器实例

#### UI 组件
- 🪟 **ConfigDialog**: 支持多组的标签式配置对话框
- 📱 **ProxyConfigPanel**: 每个组设置的独立面板
- 🛠️ **Tool Window**: 用于服务控制和状态的标签式界面
- 🎨 **ProxyGroupTabPanel**: 每个服务状态和操作的显示面板

#### 附加功能
- 🔒 **端口冲突检测**: 启动服务器前检查端口可用性
- 🚫 **重复端口防护**: 防止多个服务使用同一端口
- 🌐 **多语言名称**: 支持中英文配置组名称
- 📝 **增强日志**: 控制台日志包含配置组名称（`[用户服务] ➤ GET /api/user`）

### 🔄 变更

#### 配置格式
- 📄 **文件结构**: 配置格式从 v1.0 升级到 v2.0
  - **新格式**: `{ "version": "2.0", "proxyGroups": [...] }`
  - **旧格式**: `{ "port": 8888, "interceptPrefix": "/api", ... }`
- 📁 **嵌套结构**: 单个配置现在变成 `proxyGroups` 中的配置数组

#### 服务器行为
- 📊 **控制台日志**: 现在包含配置组名称以便更好地识别
  - 示例: `[用户服务] ➤ GET /api/user/info`
- 🏠 **欢迎页面**: 服务器欢迎页面显示配置组信息
- 🚀 **独立服务器**: 每个组作为单独的 HTTP 服务器实例运行

#### UI/UX 改进
- 🎨 **现代布局**: 采用标签式界面的完整 UI 重新设计
- 🔀 **多服务器控制**: 每个服务的独立启动/停止控制
- 📍 **状态指示器**: 运行/停止服务的可视化指示器
- 🎯 **更好的组织**: 相关配置的逻辑分组

### 🐛 修复
- 🔧 **端口检测**: 使用 ServerSocket 修复端口冲突误报问题
- 🔄 **对话框关闭**: 修复配置对话框保存后不关闭的问题
- 🎯 **更改监听器**: 移除重复的标签更改监听器以防止对话框重新打开
- 🧹 **资源清理**: 重建标签时正确清理旧监听器

### 🛡️ 向后兼容

#### 自动迁移
- 🔄 **无缝升级**: 旧的 v1.0 配置自动升级到 v2.0 格式
- 💾 **安全备份**: 旧配置备份为 `config.json.backup`
- 🎯 **默认名称**: 迁移的配置变成"默认配置"
- 📢 **用户反馈**: 迁移后显示成功通知
- ✅ **无数据丢失**: 保留所有 Mock API 和设置

### 🔧 技术细节

#### 架构
- 🗺️ **ConcurrentHashMap**: 线程安全的多服务器实例管理
- 🧵 **独立线程**: 每个服务器拥有自己的 `HttpServer` 和线程池
- 🆔 **UUID 标识**: 配置组通过 UUID 标识以确保稳定性
- 🔍 **智能检测**: 启动前智能检测端口冲突
- 🧹 **资源管理**: 服务器实例的适当生命周期管理

#### 数据流
- 📊 **状态管理**: 所有服务器实例的集中状态跟踪
- 🔄 **响应式更新**: 服务器状态更改时 UI 自动更新
- 💾 **持久化**: 配置更改立即保存到磁盘
- 🔐 **数据完整性**: 验证确保配置一致性

### 📝 说明
- ✅ **完整实现**: UI 层、配置对话框和工具窗口全部更新
- 🚫 **无破坏性变更**: 对最终用户零破坏性变更 - 自动迁移处理一切
- 🎯 **生产就绪**: 通过多个并发服务器的全面测试
- 📚 **文档**: 包含迁移指南的综合 CHANGELOG

## [1.0.3]
### 变更
- 将插件名称更新为 "Intercept Wave"
- 简化工具窗口配置，将更多属性移至 plugin.xml 声明式配置
    - 在 plugin.xml 中直接配置 icon、anchor、doNotActivateOnStart 属性
    - 移除代码中的 `init()` 方法，改用 XML 声明式配置

### 新增
- 添加 MIT 许可证文件
- 添加 IDEA Run 工具窗口中的实时请求日志查看器
    - 服务启动时自动在 Run 工具窗口显示
    - 彩色日志消息（info、success、warning、error、debug）
    - 带时间戳的请求/响应日志
    - Mock 响应 vs. 代理转发指示器
    - 服务器启动/停止通知
    - 集成到 IDEA 原生 Run 工具窗口（而非嵌入插件窗口）

### 改进
- 移除服务启动/停止的弹窗提示
    - 服务状态通知现在仅在 Run 工具窗口日志中显示
    - 提供更清爽、无干扰的用户体验

### 说明
- Plugin Verifier 的某些警告（deprecated/experimental/internal API）来自 `ToolWindowFactory` 接口本身
    - Kotlin 编译器会为接口方法自动生成桥接实现
    - 这些警告是 Kotlin 与 IntelliJ Platform 交互的固有特性，不影响功能
    - 源代码中已经使用了所有推荐的新 API（`shouldBeAvailable()`, DumbAware 等）

## [1.0.2]
### 新增
- 更新文档，更加精准介绍插件
- 改进 UI 兼容性
    - 使用 `JBColor` 代替 `java.awt.Color`，支持亮色和暗色主题
    - 使用 `JBUI.insets()` 代替原生 `Insets`，支持 HiDPI 显示器
    - 使用 `JBScrollPane` 代替原生 `JScrollPane`

### 修复
- 修复 `stripPrefix` 路径匹配逻辑
    - 修正了路径匹配行为，使其更符合直觉
    - `stripPrefix=true`（默认）：`mockApis` 中的 `path` 配置为相对路径，请求 `/api/user` 会去掉前缀匹配 `path="/user"`
    - `stripPrefix=false`：`mockApis` 中的 `path` 需要配置完整路径，请求 `/api/user` 匹配 `path="/api/user"`
    - 更新相关注释和文档，清晰说明配置方式
- 修复配置对话框 tooltip 中端口号被格式化的问题
    - 将端口号转换为字符串传递给 `message()` 函数，避免被本地化格式化为 `8,888`
- 解决所有 IntelliJ Platform 兼容性警告
    - 移除了对已废弃 API 的使用（`ToolWindowFactory.isApplicable()`, `isDoNotActivateOnStart()`）
    - 移除了对实验性 API 的使用（`ToolWindowFactory.manage()`）
    - 移除了对内部 API 的使用（`getAnchor()`, `getIcon()`）
    - 使用新的 `init()` 方法配置工具窗口属性
    - 添加 `DumbAware` 接口提高兼容性
- 修复 CI 构建中的 Kotlin 统计收集错误
    - 禁用 Kotlin 编译统计功能，避免 CI 环境中目录缺失导致的错误
    - 添加 `kotlin.build.report.enabled = false` 配置

## [1.0.1] - 2025-10-15
### 新增
- 配置文件自动补全功能
    - 插件启动时自动检测并补全配置文件中缺失的字段
    - 保留用户已有配置，仅添加缺失的默认配置项
    - 支持从旧版本配置平滑升级到新版本

### 修复
- 修复请求转发时 `ERR_INVALID_CHUNKED_ENCODING` 错误
    - 解决了转发响应时 `Transfer-Encoding: chunked` 与 `Content-Length` 冲突的问题
    - 在复制原始服务器响应头时排除 `Transfer-Encoding` 和 `Content-Length` 头

## [1.0.0] - 2025-10-15
### 新增
- 实现 Mock 服务器核心功能
- 支持请求拦截和转发
- 支持自定义 Mock 数据响应
- 提供可视化配置界面
