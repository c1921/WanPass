package io.github.c1921.wanpass.data.repository

import io.github.c1921.wanpass.data.preferences.VaultPreferencesStore
import io.github.c1921.wanpass.domain.model.AutoLockDuration
import io.github.c1921.wanpass.domain.model.VaultKeyMetadata
import io.github.c1921.wanpass.domain.model.VaultSettings
import io.github.c1921.wanpass.domain.model.WebDavConfigDraft
import io.github.c1921.wanpass.domain.model.WebDavRuntimeConfig
import io.github.c1921.wanpass.domain.model.WebDavSettings
import io.github.c1921.wanpass.domain.repository.VaultSettingsRepository
import io.github.c1921.wanpass.security.WebDavCredentialCipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class VaultSettingsRepositoryImpl @Inject constructor(
    private val preferencesStore: VaultPreferencesStore,
    private val webDavCredentialCipher: WebDavCredentialCipher,
) : VaultSettingsRepository {
    override val settingsFlow: Flow<VaultSettings> = preferencesStore.settingsFlow
    override val recentViewedIdsFlow: Flow<List<String>> = preferencesStore.recentViewedIdsFlow
    override val webDavSettingsFlow: Flow<WebDavSettings> = preferencesStore.webDavSettingsFlow

    override suspend fun setOnboardingComplete(value: Boolean) {
        preferencesStore.setOnboardingComplete(value)
    }

    override suspend fun setBiometricEnabled(value: Boolean) {
        preferencesStore.setBiometricEnabled(value)
    }

    override suspend fun setAutoLockDuration(value: AutoLockDuration) {
        preferencesStore.setAutoLockDuration(value)
    }

    override suspend fun recordRecentItem(itemId: String) {
        preferencesStore.recordRecentItem(itemId)
    }

    override suspend fun removeRecentItem(itemId: String) {
        preferencesStore.removeRecentItem(itemId)
    }

    override suspend fun loadKeyMetadata(): VaultKeyMetadata? = preferencesStore.loadKeyMetadata()

    override suspend fun saveKeyMetadata(metadata: VaultKeyMetadata) {
        preferencesStore.saveKeyMetadata(metadata)
    }

    override suspend fun loadWebDavSettings(): WebDavSettings = preferencesStore.loadWebDavSettings()

    override suspend fun saveWebDavConfig(draft: WebDavConfigDraft) {
        val passwordCiphertext = when {
            draft.password.isNotBlank() -> webDavCredentialCipher.encrypt(draft.password)
            else -> null
        }
        preferencesStore.saveWebDavConfig(
            baseUrl = draft.baseUrl,
            remoteRoot = draft.remoteRoot,
            username = draft.username,
            passwordCiphertext = passwordCiphertext,
            preserveStoredPassword = draft.preserveStoredPassword,
        )
    }

    override suspend fun setWebDavEnabled(value: Boolean) {
        preferencesStore.setWebDavEnabled(value)
    }

    override suspend fun clearWebDavConfig() {
        preferencesStore.clearWebDavConfig()
    }

    override suspend fun loadWebDavRuntimeConfig(): WebDavRuntimeConfig? {
        val settings = preferencesStore.loadWebDavSettings()
        if (settings.baseUrl.isBlank() || settings.username.isBlank()) return null
        val passwordCiphertext = preferencesStore.loadWebDavPasswordCiphertext() ?: return null
        return WebDavRuntimeConfig(
            baseUrl = settings.baseUrl,
            remoteRoot = settings.remoteRoot,
            username = settings.username,
            password = webDavCredentialCipher.decrypt(passwordCiphertext),
            deviceId = preferencesStore.ensureWebDavDeviceId(),
        )
    }

    override suspend fun setWebDavSyncStatus(lastSyncAt: Long?, lastSyncError: String?) {
        preferencesStore.setWebDavSyncStatus(lastSyncAt = lastSyncAt, lastSyncError = lastSyncError)
    }

    override suspend fun ensureWebDavDeviceId(): String = preferencesStore.ensureWebDavDeviceId()
}
