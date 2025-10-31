package org.zhongmiao.interceptwave.services

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.MockConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.model.RootConfig
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import kotlinx.serialization.encodeToString
import org.zhongmiao.interceptwave.util.PluginConstants
import java.io.File
import java.util.UUID

/**
 * 配置管理服务 - v2.0 支持多配置组
 * 负责读取、写入和管理.intercept-wave配置文件夹中的配置
 */
@Service(Service.Level.PROJECT)
class ConfigService(private val project: Project) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true  // 确保即使是默认值也会被序列化
    }

    private val configDir: File
        get() = File(project.basePath, ".intercept-wave").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

    private val configFile: File
        get() = File(configDir, "config.json")

    private val backupFile: File
        get() = File(configDir, "config.json.backup")

    private var rootConfig: RootConfig = loadRootConfig()

    /**
     * 加载根配置，自动处理版本迁移
     */
    private fun loadRootConfig(): RootConfig {
        return try {
            if (configFile.exists()) {
                val content = configFile.readText()
                val jsonObject = json.parseToJsonElement(content).jsonObject

                // 检查是否为 v2.0 配置
                if (jsonObject.containsKey("version") && jsonObject.containsKey("proxyGroups")) {
                    // v2.0 配置，直接加载
                    val loaded = json.decodeFromString(RootConfig.serializer(), content)
                    // 兼容性处理：规范化历史 mockData（单引号、未引号键、尾逗号）并最小化，并确保版本为当前主次版本
                    val fixed = normalizeAndMinifyMockData(loaded)
                    val withVersion = ensureVersionMajorMinor(fixed.second)
                    if (fixed.first || withVersion.version != loaded.version) {
                        saveRootConfig(withVersion)
                    }
                    withVersion
                } else {
                    // v1.0 配置，执行迁移
                    thisLogger().info("Detected v1.0 config, starting migration...")
                    val migrated = migrateFromV1(content)
                    // 迁移后再做一次规范化（以防旧数据中的 mockData 不是严格 JSON），并确保版本
                    val fixed = normalizeAndMinifyMockData(migrated)
                    val withVersion = ensureVersionMajorMinor(fixed.second)
                    if (fixed.first || withVersion.version != migrated.version) {
                        saveRootConfig(withVersion)
                    }
                    withVersion
                }
            } else {
                // 新安装，创建默认配置
                val created = createDefaultConfig()
                val withVersion = ensureVersionMajorMinor(created)
                if (withVersion.version != created.version) {
                    saveRootConfig(withVersion)
                }
                withVersion
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to load config", e)
            createDefaultConfig()
        }.also {
            rootConfig = it
        }
    }

    /**
     * 遍历所有组的 mockData，尝试宽容解析并最小化为紧凑 JSON。
     * @return Pair<changed, normalizedConfig>
     */
    private fun normalizeAndMinifyMockData(root: RootConfig): Pair<Boolean, RootConfig> {
        var changed = false
        val normalizedGroups = root.proxyGroups.map { group ->
            val newApis = group.mockApis.map { api ->
                val original = api.mockData
                try {
                    val minified = org.zhongmiao.interceptwave.util.JsonNormalizeUtil.minifyJson(original)
                    if (minified != original) {
                        changed = true
                        api.copy(mockData = minified)
                    } else api
                } catch (e: Exception) {
                    // 宽容解析失败则保留原样，但记录日志
                    thisLogger().warn("Normalize mockData failed for path=${api.path}: ${e.message}")
                    api
                }
            }.toMutableList()
            group.copy(mockApis = newApis)
        }.toMutableList()

        return changed to root.copy(proxyGroups = normalizedGroups)
    }

    /**
     * 计算当前插件的主次版本号（x.y）。
     * 优先从插件管理器读取，失败时回退为现有 version 的主次或 2.0。
     */
    private fun currentMajorMinor(existing: String? = null): String {
        val fallbackExisting = existing ?: "2.0"
        val fallback = fallbackExisting.split('.').let { if (it.size >= 2) it[0] + "." + it[1] else "2.0" }
        return try {
            val ver = PluginManagerCore.getPlugin(PluginId.getId(PluginConstants.PLUGIN_ID))?.version
            val parts = ver?.split('.')
            if (parts != null && parts.size >= 2) parts[0] + "." + parts[1] else fallback
        } catch (_: Throwable) {
            val sys = try { System.getProperty("intercept.wave.version") } catch (_: Throwable) { null }
            val parts = sys?.split('.')
            if (parts != null && parts.size >= 2) parts[0] + "." + parts[1] else fallback
        }
    }

    /** 确保配置 version 为当前插件的 x.y */
    private fun ensureVersionMajorMinor(root: RootConfig): RootConfig {
        val desired = currentMajorMinor(root.version)
        return if (root.version != desired) root.copy(version = desired) else root
    }

    /**
     * 从 v1.0 配置迁移到 v2.0
     */
    private fun migrateFromV1(oldContent: String): RootConfig {
        try {
            // 备份旧配置
            configFile.copyTo(backupFile, overwrite = true)
            thisLogger().info("Old config backed up to config.json.backup")

            // 解析旧配置
            val oldConfig = json.decodeFromString(MockConfig.serializer(), oldContent)

            // 创建新配置结构
                    val newConfig = RootConfig(
                        version = "2.0",
                        proxyGroups = mutableListOf(
                            ProxyConfig(
                                id = UUID.randomUUID().toString(),
                                name = message("config.group.default"),
                                port = oldConfig.port,
                                interceptPrefix = oldConfig.interceptPrefix,
                                baseUrl = oldConfig.baseUrl,
                                stripPrefix = oldConfig.stripPrefix,
                                globalCookie = oldConfig.globalCookie,
                        enabled = true,
                        mockApis = oldConfig.mockApis
                    )
                )
            )

            // 保存新配置
            saveRootConfig(newConfig)
            thisLogger().info("Config migrated from v1.0 to v2.0 successfully")

            // 通知用户
            NotificationGroupManager.getInstance()
                .getNotificationGroup("InterceptWave")
                .createNotification(
                    message("config.migration.title"),
                    message("config.migration.message"),
                    NotificationType.INFORMATION
                )
                .notify(project)

            return newConfig
        } catch (e: Exception) {
            thisLogger().error("Failed to migrate config from v1.0", e)
            // 迁移失败，创建默认配置
            return createDefaultConfig()
        }
    }

    /**
     * 创建默认配置
     */
    private fun createDefaultConfig(): RootConfig {
        val defaultConfig = RootConfig(
            version = "2.0",
            proxyGroups = mutableListOf(
                createDefaultProxyConfig(0, message("config.group.default"))
            )
        )
        saveRootConfig(defaultConfig)
        return defaultConfig
    }

    /**
     * 创建默认代理配置
     */
    fun createDefaultProxyConfig(index: Int, name: String? = null): ProxyConfig {
        return ProxyConfig(
            id = UUID.randomUUID().toString(),
            name = name ?: message("config.group.default.indexed", index + 1),
            port = 8888 + index,
            interceptPrefix = "/api",
            baseUrl = "http://localhost:8080",
            stripPrefix = true,
            globalCookie = "",
            enabled = true,
            mockApis = mutableListOf()
        )
    }

    /**
     * 保存根配置
     */
    fun saveRootConfig(config: RootConfig) {
        try {
            val versioned = ensureVersionMajorMinor(config)
            configFile.writeText(json.encodeToString(versioned))
            rootConfig = versioned
            thisLogger().info("Root config saved successfully")
        } catch (e: Exception) {
            thisLogger().error("Failed to save root config", e)
            throw e
        }
    }

    /**
     * 获取根配置
     */
    fun getRootConfig(): RootConfig = rootConfig

    /**
     * 获取所有配置组
     */
    fun getAllProxyGroups(): List<ProxyConfig> = rootConfig.proxyGroups

    /**
     * 获取启用的配置组
     */
    fun getEnabledProxyGroups(): List<ProxyConfig> = rootConfig.proxyGroups.filter { it.enabled }

    /**
     * 根据 ID 获取配置组
     */
    fun getProxyGroup(id: String): ProxyConfig? = rootConfig.proxyGroups.find { it.id == id }

}
