package org.zhongmiao.interceptwave.services

/**
 * Unified server engine interface for HTTP/WS engines.
 */
interface ServerEngine {
    fun start(): Boolean
    fun stop()
    fun isRunning(): Boolean
    val lastError: String?
    fun getUrl(): String
}
