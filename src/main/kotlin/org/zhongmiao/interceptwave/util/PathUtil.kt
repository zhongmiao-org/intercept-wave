package org.zhongmiao.interceptwave.util

import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.ProxyConfig

/**
 * Small helpers to compute the path used for matching in HTTP/WS engines.
 */
object PathUtil {
    fun selectHttpRoute(config: ProxyConfig, requestPath: String): HttpRoute? {
        val candidates = config.routes.withIndex().filter { (_, route) -> routeMatches(route, requestPath) }
        if (candidates.isEmpty()) return config.routes.singleOrNull()
        return candidates.maxWithOrNull(
            compareBy<IndexedValue<HttpRoute>> { normalizedPathPrefix(it.value.pathPrefix).length }
                .thenBy { -it.index }
        )?.value
    }

    fun computeHttpMatchPath(route: HttpRoute, requestPath: String): String {
        return computeHttpRoutePath(route, requestPath)
    }

    fun computeHttpForwardPath(route: HttpRoute, requestPath: String): String {
        return computeHttpRoutePath(route, requestPath)
    }

    /**
     * Compute WS match path. Do NOT inherit HTTP prefix when wsInterceptPrefix is empty.
     * Only when stripPrefix is enabled and wsInterceptPrefix is non-empty and matches, strip it.
     */
    fun computeWsMatchPath(config: ProxyConfig, requestPath: String): String {
        if (!config.stripPrefix) return requestPath
        val wsPrefix = config.wsInterceptPrefix ?: ""
        return if (wsPrefix.isNotEmpty() && requestPath.startsWith(wsPrefix))
            requestPath.removePrefix(wsPrefix).ifEmpty { "/" } else requestPath
    }

    private fun routeMatches(route: HttpRoute, requestPath: String): Boolean {
        val prefix = normalizedPathPrefix(route.pathPrefix)
        return prefix == "/" || requestPath == prefix || requestPath.startsWith("$prefix/")
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

    private fun computeHttpRoutePath(route: HttpRoute, requestPath: String): String {
        val strippedPath = computePathWithOptionalStrip(
            requestPath,
            normalizedPathPrefix(route.pathPrefix),
            route.stripPrefix
        )
        return applyRewriteTargetPath(strippedPath, route.rewriteTargetPath)
    }

    private fun applyRewriteTargetPath(routeLocalPath: String, rewriteTargetPath: String): String {
        val rewriteBase = normalizedRewriteTargetPath(rewriteTargetPath) ?: return routeLocalPath
        if (rewriteBase == "/") return routeLocalPath
        val suffix = routeLocalPath.trimStart('/')
        return if (suffix.isEmpty()) rewriteBase else "$rewriteBase/$suffix"
    }

    private fun normalizedRewriteTargetPath(path: String): String? {
        if (path.isBlank()) return null
        val withLeadingSlash = if (path.startsWith("/")) path else "/$path"
        return withLeadingSlash.trimEnd('/').ifEmpty { "/" }
    }
}
