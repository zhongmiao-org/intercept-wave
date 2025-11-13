package org.zhongmiao.interceptwave.util

import com.sun.net.httpserver.HttpExchange
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration

/**
 * Helpers to build and send forward requests to upstream HTTP server.
 */
object HttpForwardUtil {
    private val restrictedRequestHeaders = setOf(
        "host", "connection", "content-length", "date", "expect", "upgrade", "trailer", "te"
    )

    fun createClient(): HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun buildRequest(targetUrl: String, exchange: HttpExchange): HttpRequest {
        val method = exchange.requestMethod
        val builder = HttpRequest.newBuilder(URI(targetUrl))
            .timeout(Duration.ofSeconds(30))

        // copy safe request headers
        exchange.requestHeaders.forEach { (key, values) ->
            if (!restrictedRequestHeaders.contains(key.lowercase())) {
                values.forEach { v -> builder.header(key, v) }
            }
        }

        val hasBody = method.equals("POST", true) || method.equals("PUT", true) || method.equals("PATCH", true)
        val publisher = if (hasBody) {
            val bytes = exchange.requestBody.readAllBytes()
            HttpRequest.BodyPublishers.ofByteArray(bytes)
        } else HttpRequest.BodyPublishers.noBody()
        return builder.method(method, publisher).build()
    }
}

