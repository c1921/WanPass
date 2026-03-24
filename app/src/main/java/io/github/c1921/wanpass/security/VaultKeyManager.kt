package io.github.c1921.wanpass.security

import io.github.c1921.wanpass.core.Base64Codec
import io.github.c1921.wanpass.core.RecoveryCodeFormatter
import io.github.c1921.wanpass.data.preferences.VaultPreferencesStore
import io.github.c1921.wanpass.domain.model.PendingSetupVault
import io.github.c1921.wanpass.domain.model.VaultKeyMetadata
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultKeyManager @Inject constructor(
    private val cryptoEngine: VaultCryptoEngine,
    private val wrapCipher: KeystoreWrapCipher,
    private val preferencesStore: VaultPreferencesStore,
) {
    fun createPendingVault(): PendingSetupVault = PendingSetupVault(
        vaultKey = cryptoEngine.generateVaultKey(),
        recoveryCode = RecoveryCodeFormatter.generate(),
    )

    suspend fun persistPendingVault(pendingSetupVault: PendingSetupVault) {
        val recoverySalt = cryptoEngine.generateRecoverySalt()
        val recoveryKey = cryptoEngine.deriveRecoveryKey(pendingSetupVault.recoveryCode, recoverySalt)
        preferencesStore.saveKeyMetadata(
            VaultKeyMetadata(
                wrappedVaultKeyBase64 = Base64Codec.encode(wrapCipher.wrap(pendingSetupVault.vaultKey)),
                recoveryWrappedVaultKeyBase64 = Base64Codec.encode(
                    cryptoEngine.encryptBytes(recoveryKey, pendingSetupVault.vaultKey)
                ),
                recoverySaltBase64 = Base64Codec.encode(recoverySalt),
                recoveryCodeCiphertextBase64 = Base64Codec.encode(
                    cryptoEngine.encryptString(pendingSetupVault.vaultKey, pendingSetupVault.recoveryCode)
                ),
            )
        )
    }

    suspend fun unwrapVaultKey(): ByteArray {
        val metadata = preferencesStore.loadKeyMetadata() ?: error("Vault metadata missing")
        return wrapCipher.unwrap(Base64Codec.decode(metadata.wrappedVaultKeyBase64))
    }

    suspend fun recoverAndRebindVault(recoveryCode: String): ByteArray {
        val metadata = preferencesStore.loadKeyMetadata() ?: error("Vault metadata missing")
        val recoveryKey = cryptoEngine.deriveRecoveryKey(
            recoveryCode,
            Base64Codec.decode(metadata.recoverySaltBase64),
        )
        val vaultKey = cryptoEngine.decryptBytes(
            recoveryKey,
            Base64Codec.decode(metadata.recoveryWrappedVaultKeyBase64),
        )
        wrapCipher.recreateKey()
        preferencesStore.saveKeyMetadata(
            metadata.copy(wrappedVaultKeyBase64 = Base64Codec.encode(wrapCipher.wrap(vaultKey)))
        )
        return vaultKey
    }

    suspend fun revealRecoveryCode(vaultKey: ByteArray): String {
        val metadata = preferencesStore.loadKeyMetadata() ?: error("Vault metadata missing")
        return cryptoEngine.decryptString(vaultKey, Base64Codec.decode(metadata.recoveryCodeCiphertextBase64))
    }
}
