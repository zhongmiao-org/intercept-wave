package org.zhongmiao.interceptwave.services

import com.intellij.openapi.project.Project
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

class MockServerServiceTest {

    private fun fakeProject(): Project {
        val dir = Files.createTempDirectory("iw-ms").toFile()
        val cls = Project::class.java
        return Proxy.newProxyInstance(
            cls.classLoader,
            arrayOf(cls)
        ) { _, method, _ ->
            when (method.name) {
                "getBasePath" -> dir.absolutePath
                "isDisposed" -> false
                else -> null
            }
        } as Project
    }

    @Test
    fun serverStatusReflectiveAccess() {
        // Align with new design: status is derived from engines[configId].isRunning()
        val svc = MockServerService(fakeProject())

        // Reflectively access engines map (implementation detail, same as previous test intent)
        val f: Field = svc.javaClass.getDeclaredField("engines")
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val engines = f.get(svc) as ConcurrentHashMap<String, ServerEngine>

        // Insert a fake running engine and a non-existent id
        engines["cfg-1"] = object : ServerEngine {
            override fun start(): Boolean = true
            override fun stop() {}
            override fun isRunning(): Boolean = true
            override val lastError: String? = null
            override fun getUrl(): String = "http://localhost:0"
        }

        assertTrue(svc.getServerStatus("cfg-1"))
        assertFalse(svc.getServerStatus("cfg-2"))
    }
}
