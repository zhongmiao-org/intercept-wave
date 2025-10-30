package org.zhongmiao.interceptwave.util

import org.junit.Assert.*
import org.junit.Test

class JsonNormalizeUtilEdgeCasesTest {

    @Test
    fun `normalize handles unterminated single-quoted string`() {
        val input = "{ key: 'unfinished" // no closing quote
        val normalized = JsonNormalizeUtil.normalizeJsLikeToStrictJson(input)
        // Should not throw; result should contain a double-quoted segment
        assertTrue(normalized.contains("\"unfinished"))
    }

    @Test
    fun `normalize handles unterminated block comment`() {
        val input = "{/* unterminated" // no closing */
        val normalized = JsonNormalizeUtil.normalizeJsLikeToStrictJson(input)
        // Should not throw and should not contain the opening comment sequence
        assertFalse(normalized.contains("/*"))
    }
}

