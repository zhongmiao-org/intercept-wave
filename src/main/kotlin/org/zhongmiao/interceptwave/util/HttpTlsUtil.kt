package org.zhongmiao.interceptwave.util

import org.zhongmiao.interceptwave.model.ProxyConfig
import java.io.FileInputStream
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

object HttpTlsUtil {
    fun buildSslContext(config: ProxyConfig, projectBasePath: String?): SSLContext {
        val keystorePath = resolveKeystorePath(config.httpsKeystorePath, projectBasePath)
            ?: throw IllegalArgumentException("HTTPS keystore path is not configured")
        if (!java.nio.file.Files.isRegularFile(keystorePath)) {
            throw IllegalArgumentException("HTTPS keystore file does not exist: $keystorePath")
        }
        if (config.httpsKeystorePassword.isEmpty()) {
            throw IllegalArgumentException("HTTPS keystore password is not configured")
        }

        try {
            val password = config.httpsKeystorePassword.toCharArray()
            val keyStore = KeyStore.getInstance("PKCS12")
            FileInputStream(keystorePath.toFile()).use { keyStore.load(it, password) }
            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore, password)
            return SSLContext.getInstance("TLS").apply {
                init(keyManagerFactory.keyManagers, null, null)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to load HTTPS PKCS#12 keystore: ${e.message ?: e::class.java.simpleName}", e)
        }
    }

    fun resolveKeystorePath(value: String, projectBasePath: String?): Path? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val configured = Paths.get(trimmed)
            val base = projectBasePath?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
                ?: Paths.get(System.getProperty("user.dir"))
            val resolved = if (configured.isAbsolute) configured else base.resolve(configured)
            resolved.toAbsolutePath().normalize()
        } catch (_: InvalidPathException) {
            null
        }
    }
}
