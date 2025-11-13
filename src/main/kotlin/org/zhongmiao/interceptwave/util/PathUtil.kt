package org.zhongmiao.interceptwave.util

import org.zhongmiao.interceptwave.model.ProxyConfig

/**
 * Small helpers to compute the path used for matching in HTTP/WS engines.
 */
object PathUtil {
    /**
     * Compute HTTP match path. When stripPrefix is enabled and interceptPrefix is non-empty,
     * strip the prefix for matching; otherwise use original requestPath.
     */
    fun computeHttpMatchPath(config: ProxyConfig, requestPath: String): String {
        if (!config.stripPrefix) return requestPath
        val prefix = config.interceptPrefix
        return if (prefix.isNotEmpty() && requestPath.startsWith(prefix))
            requestPath.removePrefix(prefix).ifEmpty { "/" } else requestPath
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
}

