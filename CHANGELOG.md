<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intercept-wave Changelog

## [Unreleased]

## [1.0.2]
### Added
- 更新文档，更加精准介绍插件

### Fixed
- 修复 `stripPrefix` 路径匹配逻辑
    - 修正了路径匹配行为，使其更符合直觉
    - `stripPrefix=true`（默认）：`mockApis` 中的 `path` 配置为相对路径，请求 `/api/user` 会去掉前缀匹配 `path="/user"`
    - `stripPrefix=false`：`mockApis` 中的 `path` 需要配置完整路径，请求 `/api/user` 匹配 `path="/api/user"`
    - 更新相关注释和文档，清晰说明配置方式
- 修复配置对话框 tooltip 中端口号被格式化的问题
    - 将端口号转换为字符串传递给 `message()` 函数，避免被本地化格式化为 `8,888`



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
