package org.zhongmiao.interceptwave.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zhongmiao.interceptwave.model.HeaderOverrideOperation

class HeaderImportUtilTest {

    @Test
    fun parse_chrome_raw_response_headers() {
        val rules = HeaderImportUtil.parse(
            """
            响应头：
            HTTP/1.1 200 OK
            Server: nginx/1.26.1
            Content-Type: text/plain; charset=utf-8
            Access-Control-Allow-Origin: *
            Access-Control-Max-Age: 172800
            """.trimIndent()
        )

        assertEquals(listOf("Server", "Content-Type", "Access-Control-Allow-Origin", "Access-Control-Max-Age"), rules.map { it.name })
        assertEquals("nginx/1.26.1", rules.first().value)
        assertTrue(rules.all { it.operation == HeaderOverrideOperation.SET && it.enabled })
    }

    @Test
    fun parse_chrome_raw_request_headers() {
        val rules = HeaderImportUtil.parse(
            """
            请求头：
            POST /goam/api/webget HTTP/1.1
            Accept: application/json, text/plain, */*
            Authorization: token
            Content-Type: application/json
            application: mes
            """.trimIndent()
        )

        assertEquals(listOf("Accept", "Authorization", "Content-Type", "application"), rules.map { it.name })
        assertEquals("application/json, text/plain, */*", rules[0].value)
        assertEquals("mes", rules.last().value)
    }

    @Test
    fun parse_chrome_formatted_headers() {
        val rules = HeaderImportUtil.parse(
            """
            access-control-allow-origin
            *
            content-type
            text/plain; charset=utf-8
            set-cookie
            a=1
            set-cookie
            b=2
            """.trimIndent()
        )

        assertEquals(4, rules.size)
        assertEquals("access-control-allow-origin", rules[0].name)
        assertEquals(HeaderOverrideOperation.SET, rules[0].operation)
        assertEquals("set-cookie", rules[2].name)
        assertEquals(HeaderOverrideOperation.SET, rules[2].operation)
        assertEquals("set-cookie", rules[3].name)
        assertEquals(HeaderOverrideOperation.ADD, rules[3].operation)
    }

    @Test
    fun parse_json_object_and_rule_array() {
        val objectRules = HeaderImportUtil.parse(
            """{"X-Trace":"abc","Set-Cookie":["a=1","b=2"]}"""
        )
        assertEquals(listOf("X-Trace", "Set-Cookie", "Set-Cookie"), objectRules.map { it.name })
        assertEquals(HeaderOverrideOperation.ADD, objectRules.last().operation)

        val arrayRules = HeaderImportUtil.parse(
            """
            [
              {"name":"X-Remove","value":"","operation":"REMOVE","enabled":false},
              {"name":"X-Set","value":"ok","operation":"SET","enabled":true}
            ]
            """.trimIndent()
        )
        assertEquals("X-Remove", arrayRules[0].name)
        assertEquals(HeaderOverrideOperation.REMOVE, arrayRules[0].operation)
        assertEquals(false, arrayRules[0].enabled)
        assertEquals("ok", arrayRules[1].value)
    }

    @Test
    fun pretty_json_round_trip_uses_rule_array() {
        val rules = HeaderImportUtil.parse("X-Test: one\nX-Test: two")
        val normalized = HeaderImportUtil.toPrettyJson(rules)
        val decoded = HeaderImportUtil.parsePrettyJson(normalized)

        assertEquals(rules, decoded)
        assertTrue(normalized.trim().startsWith("["))
    }
}
