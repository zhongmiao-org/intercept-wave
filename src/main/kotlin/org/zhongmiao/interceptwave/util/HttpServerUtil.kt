package org.zhongmiao.interceptwave.util

import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange

/**
 * Small helpers for HTTP server responses to avoid duplication
 * across handlers: CORS, JSON error, header copy, preflight.
 */
object HttpServerUtil {

    /**
     * Apply standard CORS headers. Uses set to avoid duplicates.
     */
    fun applyCors(headers: Headers) {
        headers.set("Access-Control-Allow-Origin", "*")
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization")
    }

    /**
     * True if the request is a simple CORS preflight (OPTIONS).
     */
    fun isPreflight(exchange: HttpExchange): Boolean =
        exchange.requestMethod.equals("OPTIONS", ignoreCase = true)

    /**
     * Send a minimal JSON error body with status code.
     */
    fun sendJsonError(exchange: HttpExchange, statusCode: Int, message: String) {
        try {
            val errorJson = """{"error": "$message"}"""
            val bytes = errorJson.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json; charset=UTF-8")
            exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } catch (_: Exception) {
            // best-effort; suppress to avoid masking original error
        }
    }

    /**
     * Copy upstream response headers while filtering unsafe or conflicting ones.
     * CORS headers are intentionally skipped (they are applied by [applyCors]).
     */
    fun copySafeResponseHeaders(from: Map<String, List<String>>, to: Headers) {
        from.forEach { (key, values) ->
            val k = key.lowercase()
            if (k != "transfer-encoding" && k != "content-length" &&
                k != "access-control-allow-origin" && k != "access-control-allow-methods" &&
                k != "access-control-allow-headers") {
                values.forEach { v -> to.add(key, v) }
            }
        }
    }
}

