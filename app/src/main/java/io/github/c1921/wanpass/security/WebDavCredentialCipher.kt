package io.github.c1921.wanpass.security

import io.github.c1921.wanpass.core.Base64Codec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavCredentialCipher @Inject constructor() {
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return Base64Codec.encode(cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
    }

    fun decrypt(ciphertextBase64: String): String {
        val ciphertext = Base64Codec.decode(ciphertextBase64)
        require(ciphertext.size > IvSize) { "Invalid WebDAV credential payload" }
        val iv = ciphertext.copyOfRange(0, IvSize)
        val payload = ciphertext.copyOfRange(IvSize, ciphertext.size)
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TagLength, iv))
        return cipher.doFinal(payload).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance(Provider).apply { load(null) }
        val existing = keyStore.getKey(Alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, Provider)
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            Alias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val Alias = "wanpass_webdav_password_key"
        const val Provider = "AndroidKeyStore"
        const val Transformation = "AES/GCM/NoPadding"
        const val IvSize = 12
        const val TagLength = 128
    }
}
