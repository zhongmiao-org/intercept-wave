# 为 Intercept Wave 贡献代码

感谢你有兴趣为 Intercept Wave 做出贡献！本文档提供了参与项目贡献的指南和说明。

[English Version](./CONTRIBUTING.md)

## 目录

- [行为准则](#行为准则)
- [开始贡献](#开始贡献)
- [开发环境设置](#开发环境设置)
- [项目结构](#项目结构)
- [开发工作流](#开发工作流)
- [测试](#测试)
- [代码风格](#代码风格)
- [提交更改](#提交更改)
- [报告问题](#报告问题)
- [功能建议](#功能建议)

## 行为准则

本项目遵循行为准则，所有贡献者都应该遵守。请在所有互动中保持尊重和建设性。

## 开始贡献

### 前置要求

在开始之前，请确保你已安装以下工具：

- **JDK 21** 或更高版本
- **IntelliJ IDEA 2024.1** 或更高版本（旗舰版或社区版均可）
- **Git** 版本控制工具
- **Gradle**（项目中已包含 wrapper）

### Fork 和 Clone

1. 在 GitHub 上 Fork 本仓库
2. 克隆你的 Fork 到本地：
   ```bash
   git clone https://github.com/YOUR_USERNAME/intercept-wave.git
   cd intercept-wave
   ```
3. 添加上游仓库：
   ```bash
   git remote add upstream https://github.com/zhongmiao-org/intercept-wave.git
   ```

## 开发环境设置

### 1. 在 IntelliJ IDEA 中打开项目

1. 打开 IntelliJ IDEA
2. 选择 **File > Open**
3. 导航到克隆的仓库，选择 `build.gradle.kts` 文件
4. 点击 **Open as Project**
5. 等待 Gradle 同步并下载依赖

### 2. 配置插件 SDK

项目使用 IntelliJ Platform Gradle Plugin，会自动配置插件 SDK。无需手动设置 SDK。

### 3. 运行插件

在沙箱 IntelliJ IDEA 实例中运行插件：

1. 打开 Gradle 工具窗口（View > Tool Windows > Gradle）
2. 导航到 `Tasks > intellij > runIde`
3. 双击运行

或使用命令行：
```bash
./gradlew runIde
```

## 项目结构

```
intercept-wave/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── org/zhongmiao/interceptwave/
│   │   │       ├── listeners/        # 事件监听器
│   │   │       ├── model/            # 数据模型
│   │   │       ├── services/         # 核心服务
│   │   │       ├── toolWindow/       # 工具窗口工厂
│   │   │       └── ui/               # UI 组件
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   ├── plugin.xml        # 插件配置
│   │       │   └── pluginIcon.svg    # 插件图标
│   │       └── messages/             # 国际化
│   └── test/
│       └── kotlin/                   # 单元测试和 UI 测试
├── .intercept-wave/                  # 示例配置
├── build.gradle.kts                  # 构建配置
├── gradle.properties                 # 插件属性
├── CHANGELOG.md                      # 变更历史
└── README.md                         # 用户文档
```

### 核心组件

- **MockServerService**：管理 Mock 服务器的核心服务
- **ConfigService**：处理配置持久化
- **ConfigDialog**：主配置界面
- **InterceptWaveToolWindowFactory**：创建工具窗口 UI
- **ProjectCloseListener**：项目关闭时的清理工作

## 开发工作流

### 1. 创建功能分支

```bash
git checkout -b feature/your-feature-name
```

分支命名约定：
- `feature/` 新功能
- `fix/` Bug 修复
- `docs/` 文档变更
- `refactor/` 代码重构
- `test/` 测试添加

### 2. 进行更改

- 遵循现有的代码风格（参见[代码风格](#代码风格)）
- 编写清晰、描述性的提交消息
- 为新功能添加测试
- 根据需要更新文档

### 3. 测试你的更改

提交前运行测试：
```bash
./gradlew test
```

运行 UI 测试（如适用）：
```bash
./gradlew testUi
```

手动测试插件：
```bash
./gradlew runIde
```

### 4. 保持分支更新

定期与上游仓库同步：
```bash
git fetch upstream
git rebase upstream/main
```

## 测试

### 单元测试

位于 `src/test/kotlin/`，单元测试覆盖各个组件和服务。

运行所有单元测试：
```bash
./gradlew test
```

运行特定测试类：
```bash
./gradlew test --tests "org.zhongmiao.interceptwave.services.MockServerServiceTest"
```

### UI 测试

UI 测试使用 IntelliJ 的 Remote Robot 框架，位于 `src/test/kotlin/org/zhongmiao/interceptwave/ui/`。

运行 UI 测试：
```bash
./gradlew testUi
```

注意：UI 测试需要更多内存和时间，因此与单元测试分开运行。

### 测试覆盖率

检查测试覆盖率：
```bash
./gradlew koverXmlReport
```

报告将生成在 `build/reports/kover/`。

### 手动测试检查清单

手动测试时，请验证：

- [ ] 插件加载无错误
- [ ] 配置对话框能正确打开和保存
- [ ] Mock 服务器能正常启动和停止
- [ ] Mock API 返回预期响应
- [ ] 代理模式正确转发请求
- [ ] CORS 头部已添加
- [ ] 全局 Cookie 按预期工作
- [ ] 多个代理组独立工作
- [ ] 配置在 IDE 重启后持久化

## 代码风格

### Kotlin 风格

遵循 [Kotlin 编码规范](https://kotlinlang.org/docs/coding-conventions.html)：

- 使用 4 个空格缩进
- 函数和变量名使用 camelCase
- 类名使用 PascalCase
- 最大行长度：120 字符
- 左大括号放在同一行
- 在需要清晰性时使用显式类型声明

### 示例

```kotlin
class ExampleService {
    private var isRunning: Boolean = false

    fun startService(port: Int): Boolean {
        if (isRunning) {
            return false
        }

        // 实现
        isRunning = true
        return true
    }
}
```

### IntelliJ Platform 指南

- 正确使用 IntelliJ Platform SDK API
- 避免使用已弃用的 API
- 正确处理 EDT（事件分发线程）
- 适当使用应用程序服务和项目服务

## 提交更改

### 1. 提交你的更改

编写清晰、描述性的提交消息：

```bash
git commit -m "feat: 在 mock 响应中添加自定义头部支持"
```

遵循约定式提交格式：
- `feat:` 新功能
- `fix:` Bug 修复
- `docs:` 文档变更
- `test:` 测试添加或变更
- `refactor:` 代码重构
- `chore:` 维护任务

### 2. 推送到你的 Fork

```bash
git push origin feature/your-feature-name
```

### 3. 创建 Pull Request

1. 前往[原始仓库](https://github.com/zhongmiao-org/intercept-wave)
2. 点击 **Pull Requests > New Pull Request**
3. 点击 **compare across forks**
4. 选择你的 fork 和分支
5. 填写 PR 模板，包括：
   - 清晰的变更描述
   - 相关的 issue 编号（如有）
   - 截图（针对 UI 变更）
   - 执行的测试

### 4. PR 审查流程

- 维护者将审查你的 PR
- 处理任何反馈或请求的更改
- 保持你的 PR 与主分支同步
- 批准后，维护者将合并你的 PR

## 报告问题

### 提交 Issue 之前

1. 检查现有 issue 以避免重复
2. 使用最新版本验证问题
3. 收集相关信息

### 创建 Issue

请包含以下信息：

- **插件版本**：在 Settings > Plugins 中查看
- **IntelliJ IDEA 版本**：Help > About
- **操作系统**：Windows、macOS、Linux
- **重现步骤**：清晰的编号步骤
- **预期行为**：应该发生什么
- **实际行为**：实际发生了什么
- **截图/日志**：如适用
- **配置**：相关的 config.json 内容（已脱敏）

### Issue 模板

```markdown
**插件版本**: 2.0.0
**IntelliJ IDEA 版本**: 2024.1
**操作系统**: macOS 14.0

**描述**
对问题的清晰描述。

**重现步骤**
1. 步骤一
2. 步骤二
3. ...

**预期行为**
你期望发生的事情。

**实际行为**
实际发生的事情。

**截图**
如适用，添加截图。

**其他信息**
任何其他相关信息。
```

## 功能建议

我们欢迎功能建议！提交功能请求时：

1. 检查功能是否已存在或已计划
2. 清晰描述功能及其用例
3. 解释为什么这个功能有价值
4. 如可能，提供示例或模型

## 开发指南

### 添加新功能

1. **先设计**：考虑用户体验和 API 设计
2. **更新模型**：在 `model/` 中添加或修改数据模型
3. **实现服务逻辑**：在 `services/` 中添加核心功能
4. **创建 UI 组件**：如需要，在 `ui/` 中添加 UI
5. **更新配置**：如配置变更，修改 `ConfigService`
6. **添加测试**：编写单元测试和 UI 测试
7. **更新文档**：更新 README.md 和 CHANGELOG.md

### 插件配置

插件在 `plugin.xml` 中配置。关键部分：

- **Extensions**：注册服务、工具窗口等
- **Actions**：定义菜单操作和工具栏按钮
- **Listeners**：注册应用程序和项目监听器

### 国际化

计划支持多语言。添加 UI 文本时：

1. 将键添加到 `messages/InterceptWaveBundle.properties`
2. 在代码中使用 `InterceptWaveBundle.message("key")`
3. 考虑创建特定语言的属性文件

## 有疑问？

如果你对贡献有疑问：

- 开启 [GitHub Discussion](https://github.com/zhongmiao-org/intercept-wave/discussions)
- 创建带有 "question" 标签的 issue
- 查看现有文档和 issue

## 许可证

通过为 Intercept Wave 做出贡献，你同意你的贡献将在与项目相同的许可证下获得许可。

---

感谢你为 Intercept Wave 做出贡献！