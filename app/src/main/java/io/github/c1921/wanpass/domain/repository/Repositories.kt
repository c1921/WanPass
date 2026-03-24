package io.github.c1921.wanpass.domain.repository

import io.github.c1921.wanpass.domain.model.AutoLockDuration
import io.github.c1921.wanpass.domain.model.LoginContent
import io.github.c1921.wanpass.domain.model.NoteContent
import io.github.c1921.wanpass.domain.model.SyncState
import io.github.c1921.wanpass.domain.model.VaultItem
import io.github.c1921.wanpass.domain.model.VaultItemSummary
import io.github.c1921.wanpass.domain.model.VaultKeyMetadata
import io.github.c1921.wanpass.domain.model.VaultSettings
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    fun observeSummaries(): Flow<List<VaultItemSummary>>
    fun observeItem(itemId: String): Flow<VaultItem?>
    suspend fun createLogin(content: LoginContent): String
    suspend fun createNote(content: NoteContent): String
    suspend fun updateLogin(itemId: String, content: LoginContent): String
    suspend fun updateNote(itemId: String, content: NoteContent): String
    suspend fun delete(itemId: String)
}

interface VaultSettingsRepository {
    val settingsFlow: Flow<VaultSettings>
    val recentViewedIdsFlow: Flow<List<String>>
    suspend fun setOnboardingComplete(value: Boolean)
    suspend fun setBiometricEnabled(value: Boolean)
    suspend fun setAutoLockDuration(value: AutoLockDuration)
    suspend fun recordRecentItem(itemId: String)
    suspend fun removeRecentItem(itemId: String)
    suspend fun loadKeyMetadata(): VaultKeyMetadata?
    suspend fun saveKeyMetadata(metadata: VaultKeyMetadata)
}

interface SyncStatusProvider {
    val syncStateFlow: Flow<SyncState>
    val statusTextFlow: Flow<String>
}
