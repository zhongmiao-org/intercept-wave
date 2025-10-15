<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# intercept-wave Changelog

## [Unreleased]

## [1.0.1] - 2025-10-15
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
