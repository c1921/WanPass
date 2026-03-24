package io.github.c1921.wanpass.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.c1921.wanpass.domain.model.AutoLockDuration
import io.github.c1921.wanpass.domain.model.VaultKeyMetadata
import io.github.c1921.wanpass.domain.model.VaultSettings
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Singleton
class VaultPreferencesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json

    val settingsFlow: Flow<VaultSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            VaultSettings(
                onboardingComplete = preferences[Keys.OnboardingComplete] ?: false,
                biometricEnabled = preferences[Keys.BiometricEnabled] ?: true,
                autoLockDuration = AutoLockDuration.fromStorage(
                    preferences[Keys.AutoLockDuration] ?: AutoLockDuration.THIRTY_SECONDS.storageValue
                ),
            )
        }

    val recentViewedIdsFlow: Flow<List<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[Keys.RecentViewedIds]?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
        }

    suspend fun setOnboardingComplete(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.OnboardingComplete] = value }
    }

    suspend fun setBiometricEnabled(value: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.BiometricEnabled] = value }
    }

    suspend fun setAutoLockDuration(value: AutoLockDuration) {
        dataStore.edit { preferences -> preferences[Keys.AutoLockDuration] = value.storageValue }
    }

    suspend fun recordRecentItem(itemId: String) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.RecentViewedIds]?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
            val next = listOf(itemId) + current.filterNot { it == itemId }
            preferences[Keys.RecentViewedIds] = json.encodeToString(
                ListSerializer(String.serializer()),
                next.take(10)
            )
        }
    }

    suspend fun removeRecentItem(itemId: String) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.RecentViewedIds]?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
            preferences[Keys.RecentViewedIds] = json.encodeToString(
                ListSerializer(String.serializer()),
                current.filterNot { it == itemId }
            )
        }
    }

    suspend fun loadKeyMetadata(): VaultKeyMetadata? {
        val preferences = dataStore.data.first()
        val wrappedVaultKey = preferences[Keys.WrappedVaultKey] ?: return null
        val recoveryWrappedVaultKey = preferences[Keys.RecoveryWrappedVaultKey] ?: return null
        val recoverySalt = preferences[Keys.RecoverySalt] ?: return null
        val recoveryCodeCiphertext = preferences[Keys.RecoveryCodeCiphertext] ?: return null
        return VaultKeyMetadata(
            wrappedVaultKeyBase64 = wrappedVaultKey,
            recoveryWrappedVaultKeyBase64 = recoveryWrappedVaultKey,
            recoverySaltBase64 = recoverySalt,
            recoveryCodeCiphertextBase64 = recoveryCodeCiphertext,
        )
    }

    suspend fun saveKeyMetadata(metadata: VaultKeyMetadata) {
        dataStore.edit { preferences ->
            preferences[Keys.WrappedVaultKey] = metadata.wrappedVaultKeyBase64
            preferences[Keys.RecoveryWrappedVaultKey] = metadata.recoveryWrappedVaultKeyBase64
            preferences[Keys.RecoverySalt] = metadata.recoverySaltBase64
            preferences[Keys.RecoveryCodeCiphertext] = metadata.recoveryCodeCiphertextBase64
        }
    }

    private object Keys {
        val OnboardingComplete = booleanPreferencesKey("onboarding_complete")
        val BiometricEnabled = booleanPreferencesKey("biometric_enabled")
        val AutoLockDuration = stringPreferencesKey("auto_lock_duration")
        val RecentViewedIds = stringPreferencesKey("recent_viewed_ids")
        val WrappedVaultKey = stringPreferencesKey("wrapped_vault_key")
        val RecoveryWrappedVaultKey = stringPreferencesKey("recovery_wrapped_vault_key")
        val RecoverySalt = stringPreferencesKey("recovery_salt")
        val RecoveryCodeCiphertext = stringPreferencesKey("recovery_code_ciphertext")
    }
}
