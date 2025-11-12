package org.zhongmiao.interceptwave.util

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.zhongmiao.interceptwave.model.WsPushRule

/**
 * WS 规则匹配工具：
 * 用于在消息上下文中判断“推送/拦截”规则是否命中。
 */
object WsRuleMatchUtil {
    /**
     * 判断规则在当前上下文中是否匹配（用于拦截/推送判定）。
     * @param rule 待评估的规则（需启用且开启拦截才会命中）
     * @param pathOnly 用于匹配的路径部分（已按需剥离前缀）
     * @param clientToUpstream 客户端→上游为 true；上游→客户端为 false
     * @param isText 文本消息为 true；二进制为 false
     * @param text 文本消息内容；非文本时为 null
     */
    fun matches(
        rule: WsPushRule,
        pathOnly: String,
        clientToUpstream: Boolean,
        isText: Boolean,
        text: String?
    ): Boolean {
        if (!rule.enabled || !rule.intercept) return false

        // 路径匹配
        if (rule.path.isNotBlank() && !PathPatternUtil.pathPatternMatches(rule.path, pathOnly)) return false

        // 方向匹配：in = 上游→客户端，out = 客户端→上游
        val dirOk = when (rule.direction.lowercase()) {
            "both" -> true
            "in" -> !clientToUpstream
            "out" -> clientToUpstream
            else -> true
        }
        if (!dirOk) return false

        // 可选事件键值匹配（仅顶层键）。value 支持通配符（通过 patternToRegex）
        val key = rule.eventKey?.trim().orEmpty()
        val value = rule.eventValue?.trim().orEmpty()
        if (key.isNotEmpty() && value.isNotEmpty()) {
            if (!isText || text == null) return false
            val matched = runCatching {
                val element = JsonNormalizeUtil.parseStrictOrNormalize(text)
                val obj: JsonObject = element.jsonObject
                val v = obj[key]
                val s = if (v is JsonPrimitive) v.content else v?.toString()
                s?.let { PathPatternUtil.patternToRegex(value).matches(it) } ?: false
            }.getOrElse { false }
            if (!matched) return false
        }

        return true
    }
}
