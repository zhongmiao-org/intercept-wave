package org.zhongmiao.interceptwave.util

import com.intellij.openapi.application.ApplicationManager

object Env {
    @JvmStatic
    fun isUnitTestMode(): Boolean = try {
        ApplicationManager.getApplication()?.isUnitTestMode == true
    } catch (_: Throwable) {
        false
    }

    /**
     * 是否处于无头环境（无图形界面）。
     * 优先使用 IntelliJ 的 isHeadlessEnvironment，其次回退到 AWT 的 GraphicsEnvironment 判定。
     */
    @JvmStatic
    fun isHeadless(): Boolean = try {
        (ApplicationManager.getApplication()?.isHeadlessEnvironment == true)
                || java.awt.GraphicsEnvironment.isHeadless()
    } catch (_: Throwable) {
        // 当 Application 尚不可用时，回退到 AWT 判定
        java.awt.GraphicsEnvironment.isHeadless()
    }

    /**
     * 是否在 CI 环境运行（GitHub Actions / GitLab CI / Jenkins / TeamCity / CircleCI 等）。
     * 通过常见环境变量进行判定。
     */
    @JvmStatic
    fun isCI(): Boolean {
        val env = try { System.getenv() } catch (_: Throwable) { emptyMap<String, String>() }
        if (env["CI"]?.equals("true", ignoreCase = true) == true) return true
        val markers = listOf(
            "GITHUB_ACTIONS", // GitHub Actions
            "GITLAB_CI",      // GitLab CI
            "JENKINS_URL",    // Jenkins
            "TEAMCITY_VERSION", // TeamCity
            "CIRCLECI",       // CircleCI
            "BUILDKITE",      // Buildkite
            "TRAVIS"          // Travis CI
        )
        return markers.any { !env[it].isNullOrEmpty() }
    }

    /**
     * 聚合判断：是否为“无 UI 环境”（单测/Headless/CI）。
     */
    @JvmStatic
    fun isNoUi(): Boolean = isUnitTestMode() || isHeadless() || isCI()
}
