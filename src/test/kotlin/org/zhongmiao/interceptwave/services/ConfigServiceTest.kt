package org.zhongmiao.interceptwave.services

import org.junit.Assert.*
import org.junit.Test
import com.intellij.openapi.project.Project
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.model.RootConfig
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files

class ConfigServiceTest {

    private fun fakeProject(base: File): Project {
        val cls = Project::class.java
        return Proxy.newProxyInstance(
            cls.classLoader,
            arrayOf(cls)
        ) { _, method, _ ->
            when (method.name) {
                "getBasePath" -> base.absolutePath
                "isDisposed" -> false
                "toString" -> "FakeProject(${base.absolutePath})"
                else -> null
            }
        } as Project
    }

    @Test
    fun defaultConfigCreatedAndLoaded() {
        val dir = Files.createTempDirectory("iw-conf").toFile()
        val svc = ConfigService(fakeProject(dir))
        val root = svc.getRootConfig()
        assertNotNull(root)
        assertTrue(File(dir, ".intercept-wave/config.json").exists())
        assertEquals(1, root.proxyGroups.size)
        assertEquals(1, svc.getEnabledProxyGroups().size)
        val pg = svc.getAllProxyGroups().first()
        assertNotNull(svc.getProxyGroup(pg.id))
    }

    @Test
    fun saveRootConfig_appliesMajorMinorVersion() {
        val dir = Files.createTempDirectory("iw-conf2").toFile()
        // Provide plugin version via system property fallback
        System.setProperty("intercept.wave.version", "3.7.9")
        val svc = ConfigService(fakeProject(dir))
        val custom = RootConfig(version = "1.0", proxyGroups = mutableListOf())
        svc.saveRootConfig(custom)
        val loaded = svc.getRootConfig()
        assertEquals("3.7", loaded.version)
    }

    @Test
    fun enabledGroupsFilter() {
        val dir = Files.createTempDirectory("iw-conf3").toFile()
        val svc = ConfigService(fakeProject(dir))
        val a = ProxyConfig(id = "A", name = "A", enabled = true)
        val b = ProxyConfig(id = "B", name = "B", enabled = false)
        svc.saveRootConfig(RootConfig(proxyGroups = mutableListOf(a, b)))
        val enabled = svc.getEnabledProxyGroups()
        assertEquals(1, enabled.size)
        assertEquals("A", enabled.first().id)
    }
}

