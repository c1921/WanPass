package io.github.c1921.wanpass.data.repository

import io.github.c1921.wanpass.data.preferences.VaultPreferencesStore
import io.github.c1921.wanpass.domain.model.AutoLockDuration
import io.github.c1921.wanpass.domain.model.VaultKeyMetadata
import io.github.c1921.wanpass.domain.model.VaultSettings
import io.github.c1921.wanpass.domain.repository.VaultSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class VaultSettingsRepositoryImpl @Inject constructor(
    private val preferencesStore: VaultPreferencesStore,
) : VaultSettingsRepository {
    override val settingsFlow: Flow<VaultSettings> = preferencesStore.settingsFlow
    override val recentViewedIdsFlow: Flow<List<String>> = preferencesStore.recentViewedIdsFlow

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
}
