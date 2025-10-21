package org.zhongmiao.interceptwave.services

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.MockConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.model.RootConfig
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
                    json.decodeFromString(RootConfig.serializer(), content)
                } else {
                    // v1.0 配置，执行迁移
                    thisLogger().info("Detected v1.0 config, starting migration...")
                    migrateFromV1(content)
                }
            } else {
                // 新安装，创建默认配置
                createDefaultConfig()
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to load config", e)
            createDefaultConfig()
        }.also {
            rootConfig = it
        }
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
                        name = "默认配置",
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
            Notifications.Bus.notify(
                Notification(
                    "Intercept Wave",
                    message("config.migration.title"),
                    message("config.migration.message"),
                    NotificationType.INFORMATION
                ),
                project
            )

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
                createDefaultProxyConfig(0, "默认配置")
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
            name = name ?: "配置组 ${index + 1}",
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
            configFile.writeText(json.encodeToString(config))
            rootConfig = config
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