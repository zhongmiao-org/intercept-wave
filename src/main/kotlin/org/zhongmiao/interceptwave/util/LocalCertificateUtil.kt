package org.zhongmiao.interceptwave.util

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit

object LocalCertificateUtil {
    const val DEFAULT_ALIAS = "intercept-wave-local"
    const val DEFAULT_PASSWORD = "changeit"
    const val DEFAULT_RELATIVE_PATH = "certs/intercept-wave-local.p12"

    data class GenerationResult(
        val path: Path,
        val command: List<String>,
        val output: String
    )

    fun generateLocalPkcs12(
        outputPath: Path,
        password: String = DEFAULT_PASSWORD,
        overwrite: Boolean = false
    ): GenerationResult {
        require(password.length >= 6) { "PKCS#12 password must be at least 6 characters for keytool" }
        val normalized = outputPath.toAbsolutePath().normalize()
        if (Files.exists(normalized) && !overwrite) {
            throw IllegalArgumentException("Certificate file already exists: $normalized")
        }
        normalized.parent?.let { Files.createDirectories(it) }
        if (overwrite) Files.deleteIfExists(normalized)

        val keytool = findKeytool()
            ?: throw IllegalStateException("keytool was not found in the current IDE/JDK or PATH")
        val command = listOf(
            keytool,
            "-genkeypair",
            "-alias", DEFAULT_ALIAS,
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "825",
            "-storetype", "PKCS12",
            "-keystore", normalized.toString(),
            "-storepass", password,
            "-keypass", password,
            "-dname", "CN=localhost, OU=Intercept Wave, O=Local Development, L=Local, ST=Local, C=US",
            "-ext", "SAN=dns:localhost,ip:127.0.0.1"
        )
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            throw IllegalStateException("keytool timed out while generating the local certificate")
        }
        if (process.exitValue() != 0) {
            throw IllegalStateException("keytool failed with exit code ${process.exitValue()}: ${output.trim()}")
        }
        return GenerationResult(normalized, command, output.trim())
    }

    fun findKeytool(): String? {
        val exe = if (System.getProperty("os.name").lowercase(Locale.ROOT).contains("win")) "keytool.exe" else "keytool"
        val javaHome = System.getProperty("java.home")?.takeIf { it.isNotBlank() }
        if (javaHome != null) {
            val fromJavaHome = File(File(javaHome, "bin"), exe)
            if (fromJavaHome.isFile && fromJavaHome.canExecute()) return fromJavaHome.absolutePath
        }
        val path = System.getenv("PATH").orEmpty()
        return path.split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotBlank() }
            .map { File(it, exe) }
            .firstOrNull { it.isFile && it.canExecute() }
            ?.absolutePath
    }
}
