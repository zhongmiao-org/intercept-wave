package org.zhongmiao.interceptwave.services

import org.zhongmiao.interceptwave.model.MockApiConfig
import org.zhongmiao.interceptwave.model.MockConfig
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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

    private var currentConfig: MockConfig = loadConfig()

    /**
     * 加载配置
     */
    private fun loadConfig(): MockConfig {
        return try {
            if (configFile.exists()) {
                val content = configFile.readText()
                // 先解析为JsonObject检查缺失的字段
                val jsonObject = json.parseToJsonElement(content).jsonObject
                val hasMissingFields = checkMissingFields(jsonObject)

                // 解码为配置对象（缺失字段会自动使用默认值）
                val loadedConfig = json.decodeFromString(MockConfig.serializer(), content)

                // 如果有缺失字段，保存完整配置回文件
                if (hasMissingFields) {
                    saveConfig(loadedConfig)
                    thisLogger().info("Config file updated with missing fields")
                }
                loadedConfig
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
     * 检查配置文件是否缺少必要的字段
     */
    private fun checkMissingFields(jsonObject: JsonObject): Boolean {
        val requiredFields = setOf("port", "interceptPrefix", "baseUrl", "stripPrefix", "globalCookie", "mockApis")
        val existingFields = jsonObject.keys
        val missingFields = requiredFields - existingFields

        if (missingFields.isNotEmpty()) {
            thisLogger().info("Missing config fields detected: $missingFields")
            return true
        }
        return false
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