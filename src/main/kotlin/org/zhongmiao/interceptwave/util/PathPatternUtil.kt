package org.zhongmiao.interceptwave.util

import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.net.ServerSocket

/**
 * 路径模式匹配与选择的轻量工具，与 IDE 服务解耦。
 *
 * 支持基于类 glob 的路径匹配：
 * - 单星号 `*`：匹配单个路径段（不包含斜杠 `/`）
 * - 双星号 `**`：匹配多个路径段（可以包含斜杠 `/`）
 */
object PathPatternUtil {
    /**
     * 将类 glob 的模式转换为带首尾锚点的正则表达式。
     * - `*` 转换为 `[^/]+`（匹配单段，不含斜杠）
     * - `**` 转换为 `.*`（匹配多段，可含斜杠）
     * 同时会对正则元字符进行转义，避免误匹配。
     */
    fun patternToRegex(pattern: String): Regex {
        val sb = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '*') {
                // check for ** (multi-segment)
                if (i + 1 < pattern.length && pattern[i + 1] == '*') {
                    sb.append(".*")
                    i += 2
                } else {
                    // single segment wildcard (no slash)
                    sb.append("[^/]+")
                    i += 1
                }
            } else {
                // escape regex meta characters
                if (".\\[]{}()+-?^$|".indexOf(c) >= 0) sb.append('\\')
                sb.append(c)
                i += 1
            }
        }
        return Regex("^$sb$")
    }

    /**
     * 判断给定路径是否匹配指定模式（支持 `*` 与 `**`）。
     */
    fun pathPatternMatches(pattern: String, path: String): Boolean {
        if (!pattern.contains('*')) return pattern == path
        val regex = patternToRegex(pattern)
        return regex.matches(path)
    }

    /**
     * 统计通配符数量，用于优先级排序（通配符越少，匹配越具体）。
     */
    private fun wildcardCount(pattern: String): Int = pattern.count { it == '*' }

    /**
     * 在给定的 ProxyConfig 中，按请求路径与方法查找匹配的 Mock API。
     * 优先级：通配符更少 > 方法更具体（非 ALL） > 模式更长。
     * 先尝试精确匹配，其次匹配通配符并按上述规则排序后取最优。
     */
    fun findMatchingMockApiInProxy(requestPath: String, method: String, config: ProxyConfig): MockApiConfig? {
        fun methodMatches(api: MockApiConfig): Boolean =
            api.enabled && (api.method == "ALL" || api.method.equals(method, ignoreCase = true))

        val exact = config.mockApis.find { api -> methodMatches(api) && api.path == requestPath }
        if (exact != null) return exact

        val wildcardCandidates = config.mockApis.filter { api ->
            methodMatches(api) && pathPatternMatches(api.path, requestPath)
        }
        if (wildcardCandidates.isEmpty()) return null

        return wildcardCandidates.sortedWith(
            compareBy<MockApiConfig> { wildcardCount(it.path) }
                .thenBy { if (it.method == "ALL") 1 else 0 }
                .thenByDescending { it.path.length }
        ).first()
    }

    /**
     * 轻量检测 TCP 端口是否被占用。
     */
    fun isPortOccupied(port: Int): Boolean {
        return try {
            ServerSocket(port).use {
                false
            }
        } catch (_: Exception) {
            true
        }
    }
}
