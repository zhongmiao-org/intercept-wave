package org.zhongmiao.interceptwave.services

import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.MockConfig
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 配置管理服务
 * 负责读取、写入和管理.intercept-wave配置文件夹中的配置
 */
@Service(Service.Level.PROJECT)
class ConfigService(private val project: Project) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val configDir: File
        get() = File(project.basePath, ".intercept-wave").also {
            if (!it.exists()) {
                it.mkdirs()
            }
        }

    private val configFile: File
        get() = File(configDir, "config.json")

    private var currentConfig: MockConfig = loadConfig()

    /**
     * 加载配置
     */
    fun loadConfig(): MockConfig {
        return try {
            if (configFile.exists()) {
                val content = configFile.readText()
                json.decodeFromString(MockConfig.serializer(), content)
            } else {
                MockConfig().also { saveConfig(it) }
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to load config", e)
            MockConfig()
        }.also {
            currentConfig = it
        }
    }

    /**
     * 保存配置
     */
    fun saveConfig(config: MockConfig) {
        try {
            configFile.writeText(json.encodeToString(config))
            currentConfig = config
            thisLogger().info("Config saved successfully")
        } catch (e: Exception) {
            thisLogger().error("Failed to save config", e)
            throw e
        }
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): MockConfig = currentConfig

    /**
     * 获取Mock接口配置
     */
    @Suppress("unused")
    fun getMockApi(path: String): MockApiConfig? {
        return currentConfig.mockApis.find { it.path == path && it.enabled }
    }
}