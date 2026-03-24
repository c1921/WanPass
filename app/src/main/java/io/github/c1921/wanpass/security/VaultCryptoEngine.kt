package io.github.c1921.wanpass.security

import io.github.c1921.wanpass.core.RecoveryCodeFormatter
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

interface VaultCryptoEngine {
    fun generateVaultKey(): ByteArray
    fun generateRecoverySalt(): ByteArray
    fun encryptString(key: ByteArray, plaintext: String): ByteArray
    fun decryptString(key: ByteArray, ciphertext: ByteArray): String
    fun encryptBytes(key: ByteArray, plaintext: ByteArray): ByteArray
    fun decryptBytes(key: ByteArray, ciphertext: ByteArray): ByteArray
    fun deriveRecoveryKey(recoveryCode: String, salt: ByteArray): ByteArray
}

@Singleton
class AesGcmVaultCryptoEngine @Inject constructor() : VaultCryptoEngine {
    private val secureRandom = SecureRandom()

    override fun generateVaultKey(): ByteArray = ByteArray(32).also(secureRandom::nextBytes)

    override fun generateRecoverySalt(): ByteArray = ByteArray(16).also(secureRandom::nextBytes)

    override fun encryptString(key: ByteArray, plaintext: String): ByteArray =
        encryptBytes(key, plaintext.toByteArray(StandardCharsets.UTF_8))

    override fun decryptString(key: ByteArray, ciphertext: ByteArray): String =
        decryptBytes(key, ciphertext).toString(StandardCharsets.UTF_8)

    override fun encryptBytes(key: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(Transformation)
        val iv = ByteArray(IvSize).also(secureRandom::nextBytes)
        cipher.init(Cipher.ENCRYPT_MODE, key.asSecretKey(), GCMParameterSpec(TagLength, iv))
        return iv + cipher.doFinal(plaintext)
    }

    override fun decryptBytes(key: ByteArray, ciphertext: ByteArray): ByteArray {
        require(ciphertext.size > IvSize) { "Invalid ciphertext" }
        val cipher = Cipher.getInstance(Transformation)
        val iv = ciphertext.copyOfRange(0, IvSize)
        val payload = ciphertext.copyOfRange(IvSize, ciphertext.size)
        cipher.init(Cipher.DECRYPT_MODE, key.asSecretKey(), GCMParameterSpec(TagLength, iv))
        return cipher.doFinal(payload)
    }

    override fun deriveRecoveryKey(recoveryCode: String, salt: ByteArray): ByteArray {
        val normalized = RecoveryCodeFormatter.normalize(recoveryCode)
        val keySpec = PBEKeySpec(normalized.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(keySpec)
            .encoded
    }

    private fun ByteArray.asSecretKey(): SecretKey = SecretKeySpec(this, KeyAlgorithm)

    private companion object {
        const val KeyAlgorithm = "AES"
        const val Transformation = "AES/GCM/NoPadding"
        const val IvSize = 12
        const val TagLength = 128
    }
}
