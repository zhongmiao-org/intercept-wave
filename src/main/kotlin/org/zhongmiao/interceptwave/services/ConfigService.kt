package org.zhongmiao.interceptwave.services

import org.zhongmiao.interceptwave.InterceptWaveBundle.message
import org.zhongmiao.interceptwave.model.HttpRoute
import org.zhongmiao.interceptwave.model.MockConfig
import org.zhongmiao.interceptwave.model.ProxyConfig
import org.zhongmiao.interceptwave.model.RootConfig
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import com.intellij.ide.plugins.PluginManagerCore
import org.zhongmiao.interceptwave.util.PluginConstants
import org.zhongmiao.interceptwave.util.Env
import java.io.File
import java.lang.reflect.Modifier
import java.util.UUID

/**
 * 配置管理服务
 * 负责读取、滚动升级并管理 .intercept-wave 配置文件夹中的配置
 */
@Service(Service.Level.PROJECT)
class ConfigService(private val project: Project) {

    companion object {
        const val CURRENT_CONFIG_VERSION = "4.0"
    }

    @Serializable
    private data class LegacyHttpGroup(
        var interceptPrefix: String = "/api",
        var baseUrl: String = "http://localhost:8080",
        var stripPrefix: Boolean = true,
        var mockApis: MutableList<org.zhongmiao.interceptwave.model.MockApiConfig> = mutableListOf()
    )

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
     * 加载根配置。
     * 若配置不是当前版本，则按版本链逐步迁移到最新版本，
     * 回写磁盘后再以最新 schema 重新读取。
     */
    private fun loadRootConfig(): RootConfig {
        return try {
            readRootConfigFromDisk()
        } catch (e: Exception) {
            if (Env.isNoUi()) {
                thisLogger().warn("Failed to load config", e)
            } else {
                thisLogger().error("Failed to load config", e)
            }
            createDefaultConfig()
        }.also {
            rootConfig = it
        }
    }

    private fun readRootConfigFromDisk(): RootConfig {
        if (configFile.exists()) {
            val content = configFile.readText()
            val jsonObject = json.parseToJsonElement(content).jsonObject

            return if (jsonObject.containsKey("version") && jsonObject.containsKey("proxyGroups")) {
                val loaded = json.decodeFromString(RootConfig.serializer(), content)
                val migrated = migrateToLatest(loaded, jsonObject)
                val fixed = normalizeAndMinifyMockData(migrated.second)
                val withVersion = ensureVersionMajorMinor(fixed.second)
                val changed = migrated.first || fixed.first || withVersion.version != loaded.version
                persistAndReloadIfNeeded(changed, withVersion)
            } else {
                thisLogger().info("Detected v1.0 config, starting rolling migration...")
                val migratedV2 = migrateFromV1ToV2(content)
                val migrated = migrateToLatest(migratedV2, null)
                val fixed = normalizeAndMinifyMockData(migrated.second)
                val withVersion = ensureVersionMajorMinor(fixed.second)
                persistAndReloadIfNeeded(true, withVersion, notifyMigration = true)
            }
        }

        val created = createDefaultConfig()
        val withVersion = ensureVersionMajorMinor(created)
        return persistAndReloadIfNeeded(withVersion.version != created.version, withVersion)
    }

    /**
     * 当配置发生迁移或规范化变更时：
     * 1. 备份旧文件
     * 2. 写回最新版本配置
     * 3. 重新读取，确保运行时拿到的是最新 schema
     */
    private fun persistAndReloadIfNeeded(changed: Boolean, config: RootConfig, notifyMigration: Boolean = false): RootConfig {
        if (!changed) return config
        backupCurrentConfig()
        saveRootConfig(config)
        if (notifyMigration) notifyMigrationCompleted()
        return reloadSavedRootConfig()
    }

    private fun reloadSavedRootConfig(): RootConfig =
        json.decodeFromString(RootConfig.serializer(), configFile.readText())

    private fun backupCurrentConfig() {
        if (configFile.exists()) {
            configFile.copyTo(backupFile, overwrite = true)
            thisLogger().info("Old config backed up to config.json.backup")
        }
    }

    /**
     * 遍历所有组的 mockData，尝试宽容解析并最小化为紧凑 JSON。
     * 该步骤在版本迁移完成后执行，只作用于最新 schema。
     * @return Pair<changed, normalizedConfig>
     */
    private fun normalizeAndMinifyMockData(root: RootConfig): Pair<Boolean, RootConfig> {
        var changed = false
        val normalizedGroups = root.proxyGroups.map { group ->
            fun normalizeApis(apis: MutableList<org.zhongmiao.interceptwave.model.MockApiConfig>): MutableList<org.zhongmiao.interceptwave.model.MockApiConfig> {
                return apis.map { api ->
                    val original = api.mockData
                    try {
                        val minified = org.zhongmiao.interceptwave.util.JsonNormalizeUtil.minifyJson(original)
                        if (minified != original) {
                            changed = true
                            api.copy(mockData = minified)
                        } else api
                    } catch (e: Exception) {
                        thisLogger().warn("Normalize mockData failed for path=${api.path}: ${e.message}")
                        api
                    }
                }.toMutableList()
            }

            val newRoutes = group.routes.map { route ->
                route.copy(mockApis = normalizeApis(route.mockApis))
            }.toMutableList()

            // WS Push 模板与时间轴的 JSON 归一化（若为合法 JSON 则最小化，不强制要求）
            val newWsRules = group.wsPushRules.map { rule ->
                var ruleChanged = false
                var newMessage = rule.message
                // 尝试最小化 message
                runCatching {
                    val min = org.zhongmiao.interceptwave.util.JsonNormalizeUtil.minifyJson(rule.message)
                    if (min != rule.message) {
                        ruleChanged = true
                        newMessage = min
                    }
                }.onFailure {
                    // 不是严格 JSON 时忽略，允许发送纯文本
                }

                // 尝试最小化 timeline 每条 message
                val newTimeline = rule.timeline.map { item ->
                    var m = item.message
                    runCatching {
                        val min = org.zhongmiao.interceptwave.util.JsonNormalizeUtil.minifyJson(item.message)
                        if (min != item.message) {
                            ruleChanged = true
                            m = min
                        }
                    }
                    org.zhongmiao.interceptwave.model.WsTimelineItem(item.atMs, m)
                }.toMutableList()

                if (ruleChanged) changed = true
                rule.copy(message = newMessage, timeline = newTimeline)
            }.toMutableList()

            group.copy(routes = newRoutes, wsPushRules = newWsRules)
        }.toMutableList()

        return changed to root.copy(proxyGroups = normalizedGroups)
    }

    /**
     * 计算当前插件的主次版本号（x.y）。
     * 优先从插件管理器读取，失败时回退为现有 version 的主次或当前配置版本。
     */
    private fun currentMajorMinor(existing: String? = null): String {
        val fallbackExisting = existing ?: CURRENT_CONFIG_VERSION
        val fallback = fallbackExisting.split('.').let {
            if (it.size >= 2) it[0] + "." + it[1] else CURRENT_CONFIG_VERSION
        }
        // Prefer explicit system property override for tests/headless
        val sys = runCatching { System.getProperty("intercept.wave.version") }.getOrNull()
        val sysParts = sys?.split('.')
        if (sysParts != null && sysParts.size >= 2) return sysParts[0] + "." + sysParts[1]

        return try {
            val ver = resolvePluginVersionReflectively()
            val parts = ver?.split('.')
            if (parts != null && parts.size >= 2) parts[0] + "." + parts[1] else fallback
        } catch (_: Throwable) {
            // Fallback to sys or provided existing
            if (sysParts != null && sysParts.size >= 2) sysParts[0] + "." + sysParts[1] else fallback
        }
    }

    /**
     * Uses reflection to avoid generating bytecode that references PluginId.Companion,
     * which is not available on older IDE builds verified by the plugin verifier.
     */
    private fun resolvePluginVersionReflectively(): String? {
        val pluginIdClass = Class.forName("com.intellij.openapi.extensions.PluginId")
        val pluginIdFactory = pluginIdClass.methods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                (method.name == "getId" || method.name == "findId") &&
                method.parameterCount == 1 &&
                method.parameterTypes[0] == String::class.java
        } ?: return null

        val pluginId = pluginIdFactory.invoke(null, PluginConstants.PLUGIN_ID) ?: return null
        val getPluginMethod = PluginManagerCore::class.java.methods.firstOrNull { method ->
            method.name == "getPlugin" &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].name == "com.intellij.openapi.extensions.PluginId"
        } ?: return null

        val pluginDescriptor = getPluginMethod.invoke(null, pluginId) ?: return null
        val getVersionMethod = pluginDescriptor.javaClass.methods.firstOrNull { method ->
            method.name == "getVersion" && method.parameterCount == 0
        } ?: return null

        return getVersionMethod.invoke(pluginDescriptor) as? String
    }

    /** 确保配置 version 为当前插件的 x.y */
    private fun ensureVersionMajorMinor(root: RootConfig): RootConfig {
        val desired = currentMajorMinor(root.version)
        return if (root.version != desired) root.copy(version = desired) else root
    }

    /**
     * 将最早的单配置文件结构提升为 v2.0 RootConfig 结构，
     * 后续版本再继续沿版本链滚动升级。
     */
    private fun migrateFromV1ToV2(oldContent: String): RootConfig {
        val oldConfig = json.decodeFromString(MockConfig.serializer(), oldContent)
        return RootConfig(
            version = "2.0",
            proxyGroups = mutableListOf(
                ProxyConfig(
                    id = UUID.randomUUID().toString(),
                    name = message("config.group.default"),
                    port = oldConfig.port,
                    routes = mutableListOf(
                        HttpRoute(
                            name = "API",
                            pathPrefix = oldConfig.interceptPrefix,
                            targetBaseUrl = oldConfig.baseUrl,
                            stripPrefix = oldConfig.stripPrefix,
                            enableMock = true,
                            mockApis = oldConfig.mockApis
                        )
                    ),
                    stripPrefix = oldConfig.stripPrefix,
                    globalCookie = oldConfig.globalCookie,
                    enabled = true
                )
            )
        )
    }

    /**
     * 创建默认配置
     */
    private fun createDefaultConfig(): RootConfig {
        val defaultConfig = RootConfig(
            version = CURRENT_CONFIG_VERSION,
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
            routes = mutableListOf(defaultHttpRoute()),
            stripPrefix = true,
            globalCookie = "",
            enabled = true
        )
    }

    /**
     * 保存根配置
     */
    fun saveRootConfig(config: RootConfig) {
        try {
            val routed = ensureHttpRoutes(config).second
            val versioned = ensureVersionMajorMinor(routed)
            configFile.writeText(json.encodeToString(versioned))
            rootConfig = versioned
            thisLogger().info("Root config saved successfully")
        } catch (e: Exception) {
            if (Env.isNoUi()) {
                thisLogger().warn("Failed to save root config", e)
            } else {
                thisLogger().error("Failed to save root config", e)
            }
            throw e
        }
    }

    fun ensureConfigFile(): File {
        if (!configFile.exists()) {
            rootConfig = createDefaultConfig()
        }
        return configFile
    }

    fun reloadFromDisk(): RootConfig {
        val previous = rootConfig
        return try {
            readRootConfigFromDisk().also { rootConfig = it }
        } catch (e: Exception) {
            rootConfig = previous
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

    private fun defaultHttpRoute(source: LegacyHttpGroup? = null): HttpRoute {
        val prefix = source?.interceptPrefix?.ifBlank { "/" } ?: "/api"
        return HttpRoute(
            name = "API",
            pathPrefix = prefix,
            targetBaseUrl = source?.baseUrl ?: "http://localhost:8080",
            stripPrefix = source?.stripPrefix ?: true,
            enableMock = true,
            mockApis = source?.mockApis?.map { it.copy() }?.toMutableList() ?: mutableListOf()
        )
    }

    private fun shouldReplacePlaceholderRoute(group: ProxyConfig, legacy: LegacyHttpGroup?): Boolean {
        if (group.routes.isEmpty()) return true
        if (legacy == null || group.routes.size != 1) return false
        val route = group.routes.first()
        val routeMatchesLegacy =
            route.pathPrefix == legacy.interceptPrefix &&
                route.targetBaseUrl == legacy.baseUrl &&
                route.stripPrefix == legacy.stripPrefix &&
                route.mockApis.map { it.path } == legacy.mockApis.map { it.path }
        if (routeMatchesLegacy) return false

        return route.name == "API" &&
            route.pathPrefix == "/api" &&
            route.targetBaseUrl == "http://localhost:8080" &&
            route.stripPrefix &&
            route.enableMock &&
            route.mockApis.isEmpty()
    }

    private fun ensureHttpRoutes(root: RootConfig, rootJson: JsonObject? = null): Pair<Boolean, RootConfig> {
        var changed = false
        val groups = root.proxyGroups.mapIndexed { index, group ->
            if (!group.protocol.equals("HTTP", ignoreCase = true)) return@mapIndexed group
            val legacy = extractLegacyHttpGroup(rootJson, index)
            val shouldReplace = shouldReplacePlaceholderRoute(group, legacy)
            if (group.routes.isNotEmpty() && !shouldReplace) return@mapIndexed group
            changed = true
            group.copy(routes = mutableListOf(defaultHttpRoute(legacy)))
        }.toMutableList()
        return changed to root.copy(proxyGroups = groups)
    }

    private fun extractLegacyHttpGroup(rootJson: JsonObject?, index: Int): LegacyHttpGroup? {
        val groups = rootJson?.get("proxyGroups") ?: return null
        val groupElement = groups.jsonArray.getOrNull(index) ?: return null
        val groupObject = groupElement.jsonObject
        val hasLegacyFields =
            groupObject.containsKey("interceptPrefix") ||
                groupObject.containsKey("baseUrl") ||
                groupObject.containsKey("mockApis")
        if (!hasLegacyFields) return null
        return runCatching { json.decodeFromJsonElement(LegacyHttpGroup.serializer(), groupObject) }.getOrNull()
    }

    /**
     * 按配置 version 逐步滚动升级到当前版本。
     * 例如：2.0 -> 3.0 -> 4.0
     */
    private fun migrateToLatest(root: RootConfig, rootJson: JsonObject?): Pair<Boolean, RootConfig> {
        var changed = false
        var current = root

        while (current.version != CURRENT_CONFIG_VERSION) {
            current = when (current.version.substringBefore('.')) {
                "2" -> {
                    changed = true
                    migrateV2ToV3(current)
                }
                "3" -> {
                    changed = true
                    migrateV3ToV4(current, rootJson)
                }
                else -> {
                    val normalized = ensureVersionMajorMinor(current)
                    if (normalized.version == current.version) {
                        error("Unsupported config version: ${current.version}")
                    }
                    changed = true
                    normalized
                }
            }
        }

        return changed to current
    }

    private fun migrateV2ToV3(root: RootConfig): RootConfig {
        thisLogger().info("Migrating config from v2.0 to v3.0")
        return root.copy(version = "3.0")
    }

    /**
     * v3.0 -> v4.0:
     * 将旧 HTTP 单规则结构转换为 routes 多规则结构。
     */
    private fun migrateV3ToV4(root: RootConfig, rootJson: JsonObject?): RootConfig {
        thisLogger().info("Migrating config from v3.0 to v4.0")
        val withRoutes = ensureHttpRoutes(root.copy(version = "4.0"), rootJson).second
        return withRoutes.copy(version = "4.0")
    }

    private fun notifyMigrationCompleted() {
        if (Env.isNoUi()) {
            thisLogger().info("Skip migration notification in headless/CI environment")
            return
        }
        runCatching {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("InterceptWave")
                .createNotification(
                    message("config.migration.title"),
                    message("config.migration.message"),
                    NotificationType.INFORMATION
                )
                .notify(project)
        }.onFailure { t ->
            if (Env.isNoUi()) {
                thisLogger().info("Skip showing migration notification in tests: ${t.message}")
            } else {
                thisLogger().warn("Failed to show migration notification: ${t.message}")
            }
        }
    }

}
