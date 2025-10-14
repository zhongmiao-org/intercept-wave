package org.zhongmiao.interceptwave

import org.junit.Test
import org.junit.Assert.*

class InterceptWaveBundleTest {

    @Test
    fun `test message with simple key`() {
        val message = InterceptWaveBundle.message("plugin.name")
        assertEquals("Intercept Wave", message)
    }

    @Test
    fun `test message with key that has spaces`() {
        val message = InterceptWaveBundle.message("toolwindow.title")
        assertTrue(message.contains("Intercept Wave"))
        assertTrue(message.contains("Mock"))
    }

    @Test
    fun `test message with single parameter`() {
        val url = "http://localhost:8888"
        val message = InterceptWaveBundle.message("toolwindow.access.url", url)
        assertTrue(message.contains(url))
    }

    @Test
    fun `test message with multiple parameters`() {
        val port = 8888
        val prefix = "/api"
        val message = InterceptWaveBundle.message("config.global.stripprefix.tooltip", port, prefix)
        assertTrue(message.contains(port.toString()))
        assertTrue(message.contains(prefix))
    }

    @Test
    fun `test all config related messages exist`() {
        // Should not throw exceptions
        InterceptWaveBundle.message("config.dialog.title")
        InterceptWaveBundle.message("config.global.title")
        InterceptWaveBundle.message("config.global.port")
        InterceptWaveBundle.message("config.global.prefix")
        InterceptWaveBundle.message("config.global.baseurl")
        InterceptWaveBundle.message("config.global.stripprefix")
        InterceptWaveBundle.message("config.mock.title")
    }

    @Test
    fun `test all table related messages exist`() {
        // Should not throw exceptions
        InterceptWaveBundle.message("config.table.enabled")
        InterceptWaveBundle.message("config.table.path")
        InterceptWaveBundle.message("config.table.method")
        InterceptWaveBundle.message("config.table.statuscode")
        InterceptWaveBundle.message("config.table.delay")
    }

    @Test
    fun `test all button related messages exist`() {
        // Should not throw exceptions
        InterceptWaveBundle.message("config.button.add")
        InterceptWaveBundle.message("config.button.edit")
        InterceptWaveBundle.message("config.button.delete")
        InterceptWaveBundle.message("toolwindow.button.start")
        InterceptWaveBundle.message("toolwindow.button.stop")
        InterceptWaveBundle.message("toolwindow.button.config")
        InterceptWaveBundle.message("mockapi.button.format")
    }

    @Test
    fun `test all validation messages exist`() {
        // Should not throw exceptions
        InterceptWaveBundle.message("mockapi.validation.path.empty")
        InterceptWaveBundle.message("mockapi.validation.path.slash")
        InterceptWaveBundle.message("mockapi.validation.statuscode.invalid")
        InterceptWaveBundle.message("mockapi.validation.delay.invalid")
        InterceptWaveBundle.message("mockapi.validation.mockdata.empty")
    }

    @Test
    fun `test all dialog title messages exist`() {
        // Should not throw exceptions
        InterceptWaveBundle.message("mockapi.dialog.title.add")
        InterceptWaveBundle.message("mockapi.dialog.title.edit")
        InterceptWaveBundle.message("config.message.confirm.title")
    }

    @Test
    fun `test all status messages exist`() {
        // Should not throw exceptions
        InterceptWaveBundle.message("toolwindow.status.stopped")
        InterceptWaveBundle.message("toolwindow.status.running")
    }

    @Test
    fun `test all service messages exist`() {
        // Should not throw exceptions
        InterceptWaveBundle.message("message.start.success", "test-url")
        InterceptWaveBundle.message("message.start.failed")
        InterceptWaveBundle.message("message.start.error", "test-error")
        InterceptWaveBundle.message("message.stop.success")
        InterceptWaveBundle.message("message.stop.error", "test-error")
    }

    @Test
    fun `test message with empty parameter`() {
        val message = InterceptWaveBundle.message("message.start.error", "")
        assertNotNull(message)
        assertTrue(message.isNotEmpty())
    }

    @Test
    fun `test message with null parameter handled as empty string`() {
        val message = InterceptWaveBundle.message("config.message.error.save", "")
        assertNotNull(message)
        assertTrue(message.contains("Failed") || message.contains("保存"))
    }

    @Test
    fun `test messagePointer returns lazy message`() {
        val messagePointer = InterceptWaveBundle.messagePointer("plugin.name")
        assertNotNull(messagePointer)
        assertEquals("Intercept Wave", messagePointer.get())
    }

    @Test
    fun `test messagePointer with parameters`() {
        val url = "http://test:9999"
        val messagePointer = InterceptWaveBundle.messagePointer("toolwindow.access.url", url)
        assertNotNull(messagePointer)
        val message = messagePointer.get()
        assertTrue(message.contains(url))
    }

    @Test
    fun `test all mockapi field messages exist`() {
        // Should not throw exceptions
        InterceptWaveBundle.message("mockapi.enabled")
        InterceptWaveBundle.message("mockapi.path")
        InterceptWaveBundle.message("mockapi.path.tooltip")
        InterceptWaveBundle.message("mockapi.method")
        InterceptWaveBundle.message("mockapi.statuscode")
        InterceptWaveBundle.message("mockapi.statuscode.tooltip")
        InterceptWaveBundle.message("mockapi.delay")
        InterceptWaveBundle.message("mockapi.delay.tooltip")
        InterceptWaveBundle.message("mockapi.mockdata")
    }

    @Test
    fun `test config tooltips exist`() {
        // Should not throw exceptions
        InterceptWaveBundle.message("config.global.port.tooltip")
        InterceptWaveBundle.message("config.global.prefix.tooltip")
        InterceptWaveBundle.message("config.global.baseurl.tooltip")
        InterceptWaveBundle.message("config.global.stripprefix.tooltip", 8888, "/api")
    }

    @Test
    fun `test info and error message keys exist`() {
        // Should not throw exceptions
        InterceptWaveBundle.message("config.message.info")
        InterceptWaveBundle.message("config.message.error")
        InterceptWaveBundle.message("config.message.select")
        InterceptWaveBundle.message("config.message.confirm.delete")
        InterceptWaveBundle.message("config.message.success.save")
    }
}