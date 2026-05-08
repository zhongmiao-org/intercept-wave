package org.zhongmiao.interceptwave.util

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.zhongmiao.interceptwave.model.HeaderOverrideOperation
import org.zhongmiao.interceptwave.model.HeaderOverrideRule

object HeaderImportUtil {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val strictJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val headerNameRegex = Regex("^[A-Za-z0-9!#$%&'*+.^_`|~-]+$")
    private val requestLineRegex = Regex("^[A-Z]+\\s+\\S+\\s+HTTP/\\d(?:\\.\\d)?$")

    fun parse(text: String): List<HeaderOverrideRule> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()
        parseJson(trimmed)?.let { return it }

        val lines = trimmed.lines()
            .map { it.trim().trimEnd('\r') }
            .filter { it.isNotBlank() }

        val colonHeaders = lines.mapNotNull { parseColonHeaderLine(it) }
        if (colonHeaders.isNotEmpty()) return toRules(colonHeaders)

        return toRules(parseFormattedPairs(lines))
    }

    fun toPrettyJson(rules: List<HeaderOverrideRule>): String =
        json.encodeToString(ListSerializer(HeaderOverrideRule.serializer()), rules)

    fun parsePrettyJson(text: String): List<HeaderOverrideRule> =
        strictJson.decodeFromString(ListSerializer(HeaderOverrideRule.serializer()), text)

    private fun parseJson(text: String): List<HeaderOverrideRule>? {
        val element = runCatching { JsonNormalizeUtil.parseStrictOrNormalize(text) }.getOrNull() ?: return null
        return when (element) {
            is JsonObject -> elementToRules(element)
            is JsonArray -> arrayToRules(element)
            else -> null
        }
    }

    private fun elementToRules(obj: JsonObject): List<HeaderOverrideRule> {
        val pairs = mutableListOf<Pair<String, String>>()
        obj.forEach { (name, value) ->
            when (value) {
                is JsonArray -> value.forEach { item -> pairs += name to primitiveText(item) }
                else -> pairs += name to primitiveText(value)
            }
        }
        return toRules(pairs)
    }

    private fun arrayToRules(array: JsonArray): List<HeaderOverrideRule> {
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull()?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            HeaderOverrideRule(
                name = name,
                value = obj["value"]?.jsonPrimitive?.contentOrNull().orEmpty(),
                operation = obj["operation"]?.jsonPrimitive?.contentOrNull()
                    ?.uppercase()
                    ?.let { runCatching { HeaderOverrideOperation.valueOf(it) }.getOrNull() }
                    ?: HeaderOverrideOperation.SET,
                enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull() ?: true
            )
        }
    }

    private fun parseColonHeaderLine(line: String): Pair<String, String>? {
        if (line.startsWith("HTTP/", true) || requestLineRegex.matches(line)) return null
        val idx = line.indexOf(':')
        if (idx <= 0) return null
        val name = line.substring(0, idx).trim()
        if (!headerNameRegex.matches(name)) return null
        return name to line.substring(idx + 1).trim()
    }

    private fun parseFormattedPairs(lines: List<String>): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        var index = 0
        while (index < lines.size - 1) {
            val name = lines[index].trim()
            val value = lines[index + 1].trim()
            if (headerNameRegex.matches(name) && !name.startsWith("HTTP/", true) && !requestLineRegex.matches(name)) {
                pairs += name to value
                index += 2
            } else {
                index++
            }
        }
        return pairs
    }

    private fun toRules(pairs: List<Pair<String, String>>): List<HeaderOverrideRule> {
        val seen = mutableSetOf<String>()
        return pairs.filter { it.first.isNotBlank() }.map { (name, value) ->
            val key = name.lowercase()
            val operation = if (seen.add(key)) HeaderOverrideOperation.SET else HeaderOverrideOperation.ADD
            HeaderOverrideRule(name = name, value = value, operation = operation, enabled = true)
        }
    }

    private fun primitiveText(element: JsonElement): String =
        (element as? JsonPrimitive)?.contentOrNull() ?: element.toString()

    private fun JsonPrimitive.contentOrNull(): String? = runCatching { content }.getOrNull()
    private fun JsonPrimitive.booleanOrNull(): Boolean? =
        runCatching { content.toBooleanStrictOrNull() }.getOrNull()
}
