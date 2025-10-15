<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intercept-wave Changelog

## [1.0.2]
### Added
- 更新文档，更加精准介绍插件
- 改进 UI 兼容性
    - 使用 `JBColor` 代替 `java.awt.Color`，支持亮色和暗色主题
    - 使用 `JBUI.insets()` 代替原生 `Insets`，支持 HiDPI 显示器
    - 使用 `JBScrollPane` 代替原生 `JScrollPane`

### Fixed
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
### Added
- 配置文件自动补全功能
    - 插件启动时自动检测并补全配置文件中缺失的字段
    - 保留用户已有配置，仅添加缺失的默认配置项
    - 支持从旧版本配置平滑升级到新版本

### Fixed
- 修复请求转发时 `ERR_INVALID_CHUNKED_ENCODING` 错误
    - 解决了转发响应时 `Transfer-Encoding: chunked` 与 `Content-Length` 冲突的问题
    - 在复制原始服务器响应头时排除 `Transfer-Encoding` 和 `Content-Length` 头

## [1.0.0] - 2025-10-15
### Added
- 实现 Mock 服务器核心功能
- 支持请求拦截和转发
- 支持自定义 Mock 数据响应
- 提供可视化配置界面
