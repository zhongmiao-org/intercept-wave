package org.zhongmiao.interceptwave.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object JsonNormalizeUtil {

    private val strictJson by lazy { Json { prettyPrint = false; ignoreUnknownKeys = false } }
    private val prettyFmt by lazy { Json { prettyPrint = true } }

    fun parseStrict(text: String): JsonElement = strictJson.parseToJsonElement(text)

    fun parseStrictOrNormalize(text: String): JsonElement {
        return try {
            parseStrict(text)
        } catch (_: Exception) {
            val normalized = normalizeJsLikeToStrictJson(text)
            parseStrict(normalized)
        }
    }

    fun prettyJson(text: String): String {
        val element = parseStrictOrNormalize(text)
        return prettyFmt.encodeToString(JsonElement.serializer(), element)
    }

    fun minifyJson(text: String): String {
        val element = parseStrictOrNormalize(text)
        return element.toString()
    }

    fun normalizeJsLikeToStrictJson(text: String): String {
        var s = text
        s = convertSingleQuotedStrings(s)
        s = removeJsCommentsOutsideStrings(s)
        s = quoteUnquotedObjectKeys(s)
        s = removeTrailingCommas(s)
        return s
    }

    private fun convertSingleQuotedStrings(input: String): String {
        val sb = StringBuilder()
        var i = 0
        var inDouble = false
        while (i < input.length) {
            val c = input[i]
            if (c == '"') {
                sb.append(c)
                inDouble = !inDouble
                i++
            } else if (!inDouble && c == '\'') {
                val start = i + 1
                var j = start
                var escape = false
                while (j < input.length) {
                    val ch = input[j]
                    if (escape) {
                        escape = false
                    } else if (ch == '\\') {
                        escape = true
                    } else if (ch == '\'') {
                        break
                    }
                    j++
                }
                val content = if (j <= input.length) input.substring(start, j) else input.substring(start)
                val escaped = content
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                sb.append('"').append(escaped).append('"')
                i = if (j < input.length && input[j] == '\'') j + 1 else j
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun removeJsCommentsOutsideStrings(input: String): String {
        val sb = StringBuilder()
        var i = 0
        var inDouble = false
        while (i < input.length) {
            val c = input[i]
            val next = if (i + 1 < input.length) input[i + 1] else '\u0000'
            if (c == '"') {
                inDouble = !inDouble
                sb.append(c)
                i++
            } else if (!inDouble && c == '/' && next == '/') {
                i += 2
                while (i < input.length && input[i] != '\n') i++
            } else if (!inDouble && c == '/' && next == '*') {
                i += 2
                while (i + 1 < input.length && !(input[i] == '*' && input[i + 1] == '/')) i++
                i = if (i + 1 < input.length) i + 2 else input.length
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun quoteUnquotedObjectKeys(input: String): String {
        val sb = StringBuilder()
        var i = 0
        var inDouble = false
        fun isKeyStart(ch: Char): Boolean = ch == '_' || ch == '$' || ch.isLetter()
        fun isKeyChar(ch: Char): Boolean = ch == '_' || ch == '-' || ch == '$' || ch.isLetterOrDigit()

        while (i < input.length) {
            val c = input[i]
            if (c == '"') {
                sb.append(c)
                inDouble = !inDouble
                i++
                continue
            }
            if (!inDouble && (c == '{' || c == ',')) {
                sb.append(c)
                i++
                while (i < input.length && input[i].isWhitespace()) sb.append(input[i++])
                if (i >= input.length) break
                if (input[i] == '"') {
                    continue
                }
                val keyStart = i
                if (isKeyStart(input[i])) {
                    var j = i + 1
                    while (j < input.length && isKeyChar(input[j])) j++
                    val key = input.substring(i, j)
                    var k = j
                    while (k < input.length && input[k].isWhitespace()) k++
                    if (k < input.length && input[k] == ':') {
                        sb.append('"').append(key).append('"')
                        while (j < k) sb.append(input[j++])
                        sb.append(':')
                        i = k + 1
                        continue
                    }
                }
                i = keyStart
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun removeTrailingCommas(input: String): String {
        val sb = StringBuilder()
        var inDouble = false
        for (ch in input) {
            if (ch == '"') {
                inDouble = !inDouble
                sb.append(ch)
                continue
            }
            if (!inDouble && (ch == '}' || ch == ']')) {
                var idx = sb.length - 1
                while (idx >= 0 && sb[idx].isWhitespace()) idx--
                if (idx >= 0 && sb[idx] == ',') {
                    sb.deleteCharAt(idx)
                }
                sb.append(ch)
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}

