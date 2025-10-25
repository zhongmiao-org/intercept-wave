package org.zhongmiao.interceptwave.util

import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import java.net.ServerSocket

/**
 * Small utility for path pattern matching and selection independent of IDE services.
 */
object PathPatternUtil {
    /** Convert a glob-like pattern to a regex with anchors. */
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

    /** True if the given path matches the pattern supporting * and **. */
    fun pathPatternMatches(pattern: String, path: String): Boolean {
        if (!pattern.contains('*')) return pattern == path
        val regex = patternToRegex(pattern)
        return regex.matches(path)
    }

    /** Count wildcards for prioritizing more specific matches. */
    private fun wildcardCount(pattern: String): Int = pattern.count { it == '*' }

    /**
     * Find a matching mock API within a proxy config for a request path and method.
     * Prefers: fewer wildcards > method-specific (not ALL) > longer pattern
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

    /** Lightweight check if a TCP port is occupied. */
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

