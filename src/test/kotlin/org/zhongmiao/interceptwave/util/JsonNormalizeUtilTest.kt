package org.zhongmiao.interceptwave.util

import org.junit.Assert.*
import org.junit.Test

class JsonNormalizeUtilTest {

    @Test
    fun `minifyJson normalizes js-like to strict JSON`() {
        val jsLike = """
            {
              // single-line comment
              unquoted_key: 'value',
              nested: {
                list: [1, 2, 3,], /* trailing comma */
                text: 'it\'s ok',
              },
            }
        """.trimIndent()

        val minified = JsonNormalizeUtil.minifyJson(jsLike)
        // Should be valid strict JSON that can be parsed strictly
        val reparsed = JsonNormalizeUtil.parseStrict(minified)
        assertNotNull(reparsed)

        // Basic expectations about normalization
        assertTrue(minified.contains("\"unquoted_key\""))
        assertTrue(minified.contains("\"value\""))
        assertFalse(minified.contains("//"))
        assertFalse(minified.contains("/*"))
        assertFalse(minified.contains(",}"))
        assertFalse(minified.contains(",]"))
    }

    @Test
    fun `prettyJson produces multi-line output`() {
        val jsLike = """{a:1,b:2}"""
        val pretty = JsonNormalizeUtil.prettyJson(jsLike)
        assertTrue(pretty.contains("\n"))
        // It should still be valid JSON
        val element = JsonNormalizeUtil.parseStrictOrNormalize(pretty)
        assertNotNull(element)
    }
}

