package org.zhongmiao.interceptwave.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginConstantsTest {
    @Test
    fun `plugin id is stable`() {
        assertEquals("org.zhongmiao.interceptwave", PluginConstants.PLUGIN_ID)
    }
}

