package io.github.c1921.wanpass.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreWrapCipher @Inject constructor() {
    fun wrap(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher.iv + cipher.doFinal(plaintext)
    }

    fun unwrap(ciphertext: ByteArray): ByteArray {
        require(ciphertext.size > IvSize) { "Invalid wrapped payload" }
        val iv = ciphertext.copyOfRange(0, IvSize)
        val payload = ciphertext.copyOfRange(IvSize, ciphertext.size)
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TagLength, iv))
        return cipher.doFinal(payload)
    }

    fun recreateKey() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(Alias)) {
            keyStore.deleteEntry(Alias)
        }
        getOrCreateKey()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = loadKeyStore()
        val existing = keyStore.getKey(Alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, Provider)
        val spec = KeyGenParameterSpec.Builder(
            Alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(Provider).apply { load(null) }

    private companion object {
        const val Alias = "wanpass_vault_wrap_key"
        const val Provider = "AndroidKeyStore"
        const val Transformation = "AES/GCM/NoPadding"
        const val IvSize = 12
        const val TagLength = 128
    }
}
