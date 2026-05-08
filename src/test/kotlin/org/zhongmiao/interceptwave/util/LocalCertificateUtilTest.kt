package org.zhongmiao.interceptwave.util

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.security.KeyStore

class LocalCertificateUtilTest {

    @Test
    fun generateLocalPkcs12_creates_loadable_keystore() {
        assumeTrue("keytool is required for local certificate generation tests", LocalCertificateUtil.findKeytool() != null)
        val dir = Files.createTempDirectory("iw-local-cert-test")
        val target = dir.resolve(LocalCertificateUtil.DEFAULT_RELATIVE_PATH)

        try {
            LocalCertificateUtil.generateLocalPkcs12(target, LocalCertificateUtil.DEFAULT_PASSWORD, overwrite = true)
            assertTrue(Files.isRegularFile(target))

            val keyStore = KeyStore.getInstance("PKCS12")
            Files.newInputStream(target).use {
                keyStore.load(it, LocalCertificateUtil.DEFAULT_PASSWORD.toCharArray())
            }
            assertTrue(keyStore.containsAlias(LocalCertificateUtil.DEFAULT_ALIAS))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
