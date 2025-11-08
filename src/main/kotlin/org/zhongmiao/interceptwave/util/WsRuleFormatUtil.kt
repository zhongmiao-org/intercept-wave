package org.zhongmiao.interceptwave.util

import org.zhongmiao.interceptwave.model.WsPushRule

/**
 * Format a concise matcher description for a WS push rule.
 * Example: "route: /chat, event: action=JOIN, dir: in"
 */
fun formatWsRuleMatcher(r: WsPushRule): String {
    val parts = mutableListOf<String>()
    if (r.path.isNotBlank()) parts.add("route: ${r.path}")
    val key = r.eventKey?.trim().orEmpty()
    val value = r.eventValue?.trim().orEmpty()
    if (key.isNotEmpty() && value.isNotEmpty()) parts.add("event: ${key}=${value}")
    val dir = r.direction.lowercase()
    if (dir != "both") parts.add("dir: ${dir}")
    return if (parts.isEmpty()) "-" else parts.joinToString(", ")
}

