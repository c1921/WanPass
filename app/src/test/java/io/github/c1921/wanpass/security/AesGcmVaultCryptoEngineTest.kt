package io.github.c1921.wanpass.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AesGcmVaultCryptoEngineTest {
    private val engine = AesGcmVaultCryptoEngine()

    @Test
    fun `encrypt and decrypt string should roundtrip`() {
        val key = engine.generateVaultKey()
        val plaintext = "淘宝账号 abc123!@#"

        val ciphertext = engine.encryptString(key, plaintext)
        val decrypted = engine.decryptString(key, ciphertext)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `deriveRecoveryKey should be deterministic for same input`() {
        val salt = engine.generateRecoverySalt()

        val first = engine.deriveRecoveryKey("ABCD-EFGH-IJKL-MNOP-QRST-UVWX", salt)
        val second = engine.deriveRecoveryKey("ABCD-EFGH-IJKL-MNOP-QRST-UVWX", salt)

        assertArrayEquals(first, second)
    }
}
