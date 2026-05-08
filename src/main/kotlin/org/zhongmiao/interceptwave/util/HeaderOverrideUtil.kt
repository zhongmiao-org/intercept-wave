package org.zhongmiao.interceptwave.util

import com.sun.net.httpserver.Headers
import org.zhongmiao.interceptwave.model.HeaderOverrideOperation
import org.zhongmiao.interceptwave.model.HeaderOverrideRule
import java.net.http.HttpRequest

object HeaderOverrideUtil {
    val restrictedRequestHeaders = setOf(
        "host", "connection", "content-length", "date", "expect", "upgrade", "trailer", "te"
    )
    val restrictedResponseHeaders = setOf(
        "transfer-encoding", "content-length", "connection"
    )

    fun applyRequestRules(
        headers: Map<String, List<String>>,
        rules: List<HeaderOverrideRule>
    ): Map<String, List<String>> {
        val result = LinkedHashMap<String, MutableList<String>>()
        val displayNames = LinkedHashMap<String, String>()

        headers.forEach { (name, values) ->
            val key = name.lowercase()
            if (!restrictedRequestHeaders.contains(key)) {
                displayNames[key] = name
                result[key] = values.toMutableList()
            }
        }

        rules.filter { it.enabled && it.name.isNotBlank() }.forEach { rule ->
            val key = rule.name.trim().lowercase()
            if (restrictedRequestHeaders.contains(key)) return@forEach
            displayNames[key] = rule.name.trim()
            when (rule.operation) {
                HeaderOverrideOperation.SET -> result[key] = mutableListOf(rule.value)
                HeaderOverrideOperation.ADD -> result.getOrPut(key) { mutableListOf() }.add(rule.value)
                HeaderOverrideOperation.REMOVE -> {
                    result.remove(key)
                    displayNames.remove(key)
                }
            }
        }

        return result.mapKeys { (key, _) -> displayNames[key] ?: key }
    }

    fun addToRequestBuilder(builder: HttpRequest.Builder, headers: Map<String, List<String>>) {
        headers.forEach { (name, values) ->
            values.forEach { value -> builder.header(name, value) }
        }
    }

    fun applyResponseRules(headers: Headers, rules: List<HeaderOverrideRule>) {
        rules.filter { it.enabled && it.name.isNotBlank() }.forEach { rule ->
            val name = rule.name.trim()
            if (restrictedResponseHeaders.contains(name.lowercase())) return@forEach
            when (rule.operation) {
                HeaderOverrideOperation.SET -> {
                    removeHeader(headers, name)
                    headers.add(name, rule.value)
                }
                HeaderOverrideOperation.ADD -> headers.add(existingHeaderName(headers, name) ?: name, rule.value)
                HeaderOverrideOperation.REMOVE -> removeHeader(headers, name)
            }
        }
    }

    private fun removeHeader(headers: Headers, name: String) {
        existingHeaderName(headers, name)?.let { headers.remove(it) }
        headers.remove(name)
    }

    private fun existingHeaderName(headers: Headers, name: String): String? =
        headers.keys.firstOrNull { it.equals(name, ignoreCase = true) }
}
