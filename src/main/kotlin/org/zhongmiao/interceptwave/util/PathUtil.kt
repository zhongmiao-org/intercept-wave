package org.zhongmiao.interceptwave.util

import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.ProxyConfig

/**
 * Small helpers to compute the path used for matching in HTTP/WS engines.
 */
object PathUtil {
    fun selectHttpRoute(config: ProxyConfig, requestPath: String): HttpRoute? {
        val candidates = config.routes.withIndex().filter { (_, route) -> routeMatches(route, requestPath) }
        if (candidates.isEmpty()) return null
        return candidates.maxWithOrNull(
            compareBy<IndexedValue<HttpRoute>> { normalizedPathPrefix(it.value.pathPrefix).length }
                .thenBy { -it.index }
        )?.value
    }

    fun computeHttpMatchPath(route: HttpRoute, requestPath: String): String {
        return computePathWithOptionalStrip(requestPath, normalizedPathPrefix(route.pathPrefix), route.stripPrefix)
    }

    fun computeHttpForwardPath(route: HttpRoute, requestPath: String): String {
        return computePathWithOptionalStrip(requestPath, normalizedPathPrefix(route.pathPrefix), route.stripPrefix)
    }

    /**
     * Compute WS match path. Do NOT inherit HTTP prefix when wsInterceptPrefix is empty.
     * Only when stripPrefix is enabled and wsInterceptPrefix is non-empty and matches, strip it.
     */
    @Suppress("DEPRECATION")
    fun computeWsMatchPath(config: ProxyConfig, requestPath: String): String {
        if (!config.stripPrefix) return requestPath
        val wsPrefix = config.wsInterceptPrefix ?: ""
        return if (wsPrefix.isNotEmpty() && requestPath.startsWith(wsPrefix))
            requestPath.removePrefix(wsPrefix).ifEmpty { "/" } else requestPath
    }

    private fun routeMatches(route: HttpRoute, requestPath: String): Boolean {
        val prefix = normalizedPathPrefix(route.pathPrefix)
        return prefix == "/" || requestPath.startsWith(prefix)
    }

    private fun normalizedPathPrefix(prefix: String): String {
        if (prefix.isBlank()) return "/"
        if (prefix == "/") return "/"
        return prefix.trimEnd('/').ifEmpty { "/" }
    }

    private fun computePathWithOptionalStrip(requestPath: String, prefix: String, stripPrefix: Boolean): String {
        if (!stripPrefix || prefix == "/") return requestPath.ifEmpty { "/" }
        return if (requestPath.startsWith(prefix)) requestPath.removePrefix(prefix).ifEmpty { "/" } else requestPath.ifEmpty { "/" }
    }
}
