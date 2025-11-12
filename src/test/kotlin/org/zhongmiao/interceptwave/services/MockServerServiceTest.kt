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
        val svc = MockServerService(fakeProject())
        val f: Field = svc.javaClass.getDeclaredField("serverStatus")
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = f.get(svc) as ConcurrentHashMap<String, Boolean>
        map["cfg-1"] = true
        assertTrue(svc.getServerStatus("cfg-1"))
        assertFalse(svc.getServerStatus("cfg-2"))
    }
}

