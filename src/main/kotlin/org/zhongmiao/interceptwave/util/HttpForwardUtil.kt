package org.zhongmiao.interceptwave.util

import com.sun.net.httpserver.HttpExchange
import org.zhongmiao.interceptwave.model.HeaderOverrideRule
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration

/**
 * Helpers to build and send forward requests to upstream HTTP server.
 */
object HttpForwardUtil {

    fun createClient(): HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    fun buildRequest(
        targetUrl: String,
        exchange: HttpExchange,
        headerRules: List<HeaderOverrideRule> = emptyList()
    ): HttpRequest {
        val method = exchange.requestMethod
        val builder = HttpRequest.newBuilder(URI(targetUrl))
            .timeout(Duration.ofSeconds(30))

        val headers = HeaderOverrideUtil.applyRequestRules(exchange.requestHeaders, headerRules)
        HeaderOverrideUtil.addToRequestBuilder(builder, headers)

        val hasBody = method.equals("POST", true) || method.equals("PUT", true) || method.equals("PATCH", true)
        val publisher = if (hasBody) {
            val bytes = exchange.requestBody.readAllBytes()
            HttpRequest.BodyPublishers.ofByteArray(bytes)
        } else HttpRequest.BodyPublishers.noBody()
        return builder.method(method, publisher).build()
    }
}
