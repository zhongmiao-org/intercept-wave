package org.zhongmiao.interceptwave.util

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.ProxyConfig

/**
 * Utility to build the HTTP welcome JSON shown at root/prefix.
 */
object HttpWelcomeUtil {
    fun buildWelcomeJson(config: ProxyConfig): String {
        val scheme = if (config.httpsEnabled) "https" else "http"
        val routes = config.routes.ifEmpty { mutableListOf() }
        val enabledApis = routes.flatMap { route -> route.mockApis.filter { it.enabled } }
        val enabledApiCount = enabledApis.size
        val mockApiCount = routes.sumOf { it.mockApis.size }

        val examples = routes.flatMap { route ->
            route.mockApis.filter { it.enabled }.map { api ->
                val method = api.method
                val exampleUrl = if (route.stripPrefix) {
                    val prefix = if (route.pathPrefix.endsWith("/") && route.pathPrefix != "/") route.pathPrefix.dropLast(1) else route.pathPrefix
                    val path = if (api.path.startsWith("/")) api.path else "/${api.path}"
                    "$scheme://localhost:${config.port}" + if (prefix == "/") path else prefix + path
                } else {
                    val fullPath = if (api.path.startsWith("/")) api.path else "/${api.path}"
                    "$scheme://localhost:${config.port}" + fullPath
                }
                """{"route": "${route.name}", "method": "$method", "url": "$exampleUrl"}"""
            }
        }.joinToString(",\n    ")

        val routesJson = routes.joinToString(",\n    ") { route ->
            """{"name": "${route.name}", "pathPrefix": "${route.pathPrefix}", "targetType": "${route.targetType}", "targetBaseUrl": "${route.targetBaseUrl}", "staticRoot": "${route.staticRoot}", "stripPrefix": ${route.stripPrefix}, "rewriteTargetPath": "${route.rewriteTargetPath}", "spaFallbackPath": "${route.spaFallbackPath}", "spaFallback": ${route.spaFallback}, "enableMock": ${route.enableMock}, "requestHeaders": ${route.requestHeaders.count { it.enabled }}, "responseHeaders": ${route.responseHeaders.count { it.enabled }}, "mockApis": ${route.mockApis.size}}"""
        }

        return """
            {
              "status": "running",
              "message": "${message("welcome.running")}",
              "configGroup": "${config.name}",
              "server": {
                "port": ${config.port},
                "scheme": "$scheme",
                "httpsEnabled": ${config.httpsEnabled},
                "routes": ${routes.size}
              },
              "mockApis": {
                "total": $mockApiCount,
                "enabled": $enabledApiCount
              },
              "usage": {
                "description": "${message("welcome.usage.description")}",
                "example": "GET $scheme://localhost:${config.port}/your-api-path"
              },
              "routes": [
                $routesJson
              ],
              "examples": [
                $examples
              ]
            }
        """.trimIndent()
    }
}
