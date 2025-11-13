package org.zhongmiao.interceptwave.util

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.ProxyConfig

/**
 * Utility to build the HTTP welcome JSON shown at root/prefix.
 */
object HttpWelcomeUtil {
    fun buildWelcomeJson(config: ProxyConfig): String {
        val mockApiCount = config.mockApis.size
        val enabledApis = config.mockApis.filter { it.enabled }
        val enabledApiCount = enabledApis.size

        val examples = enabledApis.joinToString(",\n    ") { api ->
            val method = api.method
            val exampleUrl = if (config.stripPrefix) {
                val prefix = if (config.interceptPrefix.endsWith("/")) config.interceptPrefix.dropLast(1) else config.interceptPrefix
                val path = if (api.path.startsWith("/")) api.path else "/${api.path}"
                "http://localhost:${config.port}" + prefix + path
            } else {
                val fullPath = if (api.path.startsWith("/")) api.path else "/${api.path}"
                "http://localhost:${config.port}" + fullPath
            }
            """{"method": "$method", "url": "$exampleUrl"}"""
        }

        val apisJson = enabledApis.joinToString(",\n    ") { api ->
            """{"path": "${api.path}", "method": "${api.method}", "enabled": ${api.enabled}}"""
        }

        return """
            {
              "status": "running",
              "message": "${message("welcome.running")}",
              "configGroup": "${config.name}",
              "server": {
                "port": ${config.port},
                "baseUrl": "${config.baseUrl}",
                "interceptPrefix": "${config.interceptPrefix}",
                "stripPrefix": ${config.stripPrefix}
              },
              "mockApis": {
                "total": $mockApiCount,
                "enabled": $enabledApiCount
              },
              "usage": {
                "description": "${message("welcome.usage.description")}",
                "example": "GET http://localhost:${config.port}${config.interceptPrefix}/your-api-path"
              },
              "apis": [
                $apisJson
              ],
              "examples": [
                $examples
              ]
            }
        """.trimIndent()
    }
}

