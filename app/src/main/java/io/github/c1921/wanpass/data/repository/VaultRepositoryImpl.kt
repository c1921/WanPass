package io.github.c1921.wanpass.data.repository

import io.github.c1921.wanpass.core.SearchNormalizer
import io.github.c1921.wanpass.core.TimeProvider
import io.github.c1921.wanpass.data.local.VaultItemDao
import io.github.c1921.wanpass.data.local.VaultItemEntity
import io.github.c1921.wanpass.data.webdav.WebDavSyncPolicies
import io.github.c1921.wanpass.domain.model.LoginContent
import io.github.c1921.wanpass.domain.model.NoteContent
import io.github.c1921.wanpass.domain.model.SearchEntry
import io.github.c1921.wanpass.domain.model.SyncState
import io.github.c1921.wanpass.domain.model.VaultItem
import io.github.c1921.wanpass.domain.model.VaultItemSummary
import io.github.c1921.wanpass.domain.model.VaultItemType
import io.github.c1921.wanpass.domain.repository.VaultRepository
import io.github.c1921.wanpass.domain.repository.VaultSettingsRepository
import io.github.c1921.wanpass.security.VaultCryptoEngine
import io.github.c1921.wanpass.session.SearchIndex
import io.github.c1921.wanpass.session.VaultKeyProvider
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json

@Singleton
class VaultRepositoryImpl @Inject constructor(
    private val vaultItemDao: VaultItemDao,
    private val cryptoEngine: VaultCryptoEngine,
    private val vaultKeyProvider: VaultKeyProvider,
    private val searchIndex: SearchIndex,
    private val settingsRepository: VaultSettingsRepository,
    private val timeProvider: TimeProvider,
    private val webDavSyncGateway: WebDavSyncGateway,
) : VaultRepository {
    private val json = Json

    override fun observeSummaries(): Flow<List<VaultItemSummary>> =
        combine(vaultItemDao.observeActiveItems(), vaultKeyProvider.vaultKeyFlow) { entities, vaultKey ->
            if (vaultKey == null) {
                emptyList()
            } else {
                entities.map { entity ->
                    VaultItemSummary(
                        id = entity.id,
                        title = cryptoEngine.decryptString(vaultKey, entity.titleCiphertext),
                        type = VaultItemType.fromStorage(entity.type),
                        createdAt = entity.createdAt,
                        updatedAt = entity.updatedAt,
                    )
                }
            }
        }

    override fun observeItem(itemId: String): Flow<VaultItem?> =
        combine(vaultItemDao.observeItem(itemId), vaultKeyProvider.vaultKeyFlow) { entity, vaultKey ->
            if (entity == null || vaultKey == null || entity.deletedAt != null) {
                null
            } else {
                entity.toDomain(vaultKey)
            }
        }

    override suspend fun createLogin(content: LoginContent): String {
        val itemId = UUID.randomUUID().toString()
        upsertLogin(itemId, content, existing = null)
        return itemId
    }

    override suspend fun createNote(content: NoteContent): String {
        val itemId = UUID.randomUUID().toString()
        upsertNote(itemId, content, existing = null)
        return itemId
    }

    override suspend fun updateLogin(itemId: String, content: LoginContent): String {
        val existing = vaultItemDao.getItemById(itemId) ?: error("Record not found")
        upsertLogin(itemId, content, existing)
        return itemId
    }

    override suspend fun updateNote(itemId: String, content: NoteContent): String {
        val existing = vaultItemDao.getItemById(itemId) ?: error("Record not found")
        upsertNote(itemId, content, existing)
        return itemId
    }

    override suspend fun delete(itemId: String) {
        val existing = vaultItemDao.getItemById(itemId) ?: return
        val now = timeProvider.now()
        val webDavEnabled = settingsRepository.loadWebDavSettings().enabled
        vaultItemDao.upsert(
            existing.copy(
                deletedAt = now,
                updatedAt = now,
                revision = existing.revision + 1,
                syncState = WebDavSyncPolicies.deleteState(webDavEnabled).storageValue,
            )
        )
        settingsRepository.removeRecentItem(itemId)
        searchIndex.replace(searchIndex.entries.value.filterNot { it.id == itemId })
        if (webDavEnabled) {
            webDavSyncGateway.requestSync()
        }
    }

    private suspend fun upsertLogin(itemId: String, content: LoginContent, existing: VaultItemEntity?) {
        val vaultKey = vaultKeyProvider.requireVaultKey()
        val now = timeProvider.now()
        val webDavEnabled = settingsRepository.loadWebDavSettings().enabled
        val searchBlob = SearchNormalizer.buildSearchBlob(
            listOf(content.title, content.account, content.site, content.note)
        )
        vaultItemDao.upsert(
            VaultItemEntity(
                id = itemId,
                type = VaultItemType.LOGIN.storageValue,
                titleCiphertext = cryptoEngine.encryptString(vaultKey, content.title),
                contentCiphertext = cryptoEngine.encryptString(
                    vaultKey,
                    json.encodeToString(LoginContent.serializer(), content)
                ),
                searchBlobCiphertext = cryptoEngine.encryptString(vaultKey, searchBlob),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
                revision = (existing?.revision ?: 0L) + 1,
                syncState = WebDavSyncPolicies.upsertState(
                    webDavEnabled = webDavEnabled,
                    isExistingItem = existing != null,
                ).storageValue,
            )
        )
        updateSearchIndex(itemId, searchBlob)
        if (webDavEnabled) {
            webDavSyncGateway.requestSync()
        }
    }

    private suspend fun upsertNote(itemId: String, content: NoteContent, existing: VaultItemEntity?) {
        val vaultKey = vaultKeyProvider.requireVaultKey()
        val now = timeProvider.now()
        val webDavEnabled = settingsRepository.loadWebDavSettings().enabled
        val searchBlob = SearchNormalizer.buildSearchBlob(
            listOf(content.title, content.body, content.note)
        )
        vaultItemDao.upsert(
            VaultItemEntity(
                id = itemId,
                type = VaultItemType.NOTE.storageValue,
                titleCiphertext = cryptoEngine.encryptString(vaultKey, content.title),
                contentCiphertext = cryptoEngine.encryptString(
                    vaultKey,
                    json.encodeToString(NoteContent.serializer(), content)
                ),
                searchBlobCiphertext = cryptoEngine.encryptString(vaultKey, searchBlob),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
                revision = (existing?.revision ?: 0L) + 1,
                syncState = WebDavSyncPolicies.upsertState(
                    webDavEnabled = webDavEnabled,
                    isExistingItem = existing != null,
                ).storageValue,
            )
        )
        updateSearchIndex(itemId, searchBlob)
        if (webDavEnabled) {
            webDavSyncGateway.requestSync()
        }
    }

    private fun updateSearchIndex(itemId: String, searchBlob: String) {
        val nextEntries = searchIndex.entries.value.filterNot { it.id == itemId } + SearchEntry(
            id = itemId,
            normalizedText = SearchNormalizer.normalize(searchBlob),
        )
        searchIndex.replace(nextEntries)
    }

    private fun VaultItemEntity.toDomain(vaultKey: ByteArray): VaultItem {
        val title = cryptoEngine.decryptString(vaultKey, titleCiphertext)
        return when (VaultItemType.fromStorage(type)) {
            VaultItemType.LOGIN -> {
                val content = json.decodeFromString<LoginContent>(
                    cryptoEngine.decryptString(vaultKey, contentCiphertext)
                )
                VaultItem.Login(
                    id = id,
                    title = title,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    revision = revision,
                    account = content.account,
                    password = content.password,
                    site = content.site,
                    note = content.note,
                )
            }

            VaultItemType.NOTE -> {
                val content = json.decodeFromString<NoteContent>(
                    cryptoEngine.decryptString(vaultKey, contentCiphertext)
                )
                VaultItem.Note(
                    id = id,
                    title = title,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    revision = revision,
                    body = content.body,
                    note = content.note,
                )
            }
        }
    }
}
