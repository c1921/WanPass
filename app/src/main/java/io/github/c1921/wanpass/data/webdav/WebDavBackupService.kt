package io.github.c1921.wanpass.data.webdav

import io.github.c1921.wanpass.core.TimeProvider
import io.github.c1921.wanpass.data.local.VaultItemEntity
import io.github.c1921.wanpass.domain.model.RemoteBackupInfo
import io.github.c1921.wanpass.domain.model.VaultRecoveryMaterial
import io.github.c1921.wanpass.domain.model.WebDavRuntimeConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class WebDavBackupService @Inject constructor(
    private val client: WebDavClient,
    private val timeProvider: TimeProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun testConnection(config: WebDavRuntimeConfig) {
        client.testConnection(config)
    }

    suspend fun inspectRemote(config: WebDavRuntimeConfig): RemoteBackupInfo {
        client.ensureCollections(config)
        val rootUrl = client.rootUrl(config)
        val manifestText = client.getJsonIfExists(config, client.childUrl(rootUrl, "manifest.json")) ?: return RemoteBackupInfo()
        val indexText = client.getJsonIfExists(config, client.childUrl(rootUrl, "index.json")) ?: return RemoteBackupInfo()
        val manifest = json.decodeFromString<WebDavManifest>(manifestText)
        val index = json.decodeFromString<WebDavIndex>(indexText)
        return manifest.toInfo(itemCount = index.items.count { !it.deleted })
    }

    suspend fun uploadSnapshot(
        config: WebDavRuntimeConfig,
        allItems: List<VaultItemEntity>,
        recoveryMaterial: VaultRecoveryMaterial,
        claimedAt: Long? = null,
    ): RemoteBackupInfo {
        client.ensureCollections(config)
        val rootUrl = client.rootUrl(config)
        val itemsUrl = client.childUrl(rootUrl, "items")
        val remoteInfo = inspectRemote(config)
        val activeItems = allItems.filter { it.deletedAt == null }
        val activeIds = activeItems.map { it.id }.toSet()

        if (remoteInfo.hasBackup) {
            loadIndex(config).items
                .filterNot { it.deleted }
                .map { it.id }
                .filterNot { it in activeIds }
                .forEach { itemId ->
                    client.deleteIfExists(config, client.childUrl(itemsUrl, "$itemId.json"))
                }
        }

        activeItems.forEach { entity ->
            client.putJson(
                config = config,
                url = client.childUrl(itemsUrl, "${entity.id}.json"),
                content = json.encodeToString(WebDavItemFile.serializer(), entity.toWebDavItemFile()),
            )
        }

        val now = timeProvider.now()
        client.putJson(
            config = config,
            url = client.childUrl(rootUrl, "recovery.json"),
            content = json.encodeToString(WebDavRecoveryFile.serializer(), WebDavRecoveryFile.fromDomain(recoveryMaterial)),
        )
        client.putJson(
            config = config,
            url = client.childUrl(rootUrl, "index.json"),
            content = json.encodeToString(
                WebDavIndex.serializer(),
                WebDavIndex(
                    items = activeItems.map { entity ->
                        WebDavIndexEntry(
                            id = entity.id,
                            revision = entity.revision,
                            updatedAt = entity.updatedAt,
                            deleted = false,
                        )
                    }
                ),
            ),
        )
        val nextClaimedAt = claimedAt ?: remoteInfo.claimedAt ?: now
        val manifest = WebDavManifest(
            activeDeviceId = config.deviceId,
            claimedAt = nextClaimedAt,
            lastBackupAt = now,
        )
        client.putJson(
            config = config,
            url = client.childUrl(rootUrl, "manifest.json"),
            content = json.encodeToString(WebDavManifest.serializer(), manifest),
        )
        return manifest.toInfo(itemCount = activeItems.size)
    }

    suspend fun syncPending(
        config: WebDavRuntimeConfig,
        allItems: List<VaultItemEntity>,
        recoveryMaterial: VaultRecoveryMaterial,
    ): RemoteBackupInfo {
        client.ensureCollections(config)
        val rootUrl = client.rootUrl(config)
        val itemsUrl = client.childUrl(rootUrl, "items")
        val manifest = loadManifest(config)
        val activeItems = allItems.filter { it.deletedAt == null }
        val pendingUpserts = allItems.filter { it.syncState == "pending_create" || it.syncState == "pending_update" }
        val pendingDeletes = allItems.filter { it.syncState == "pending_delete" }

        pendingUpserts.forEach { entity ->
            client.putJson(
                config = config,
                url = client.childUrl(itemsUrl, "${entity.id}.json"),
                content = json.encodeToString(WebDavItemFile.serializer(), entity.toWebDavItemFile()),
            )
        }
        pendingDeletes.forEach { entity ->
            client.deleteIfExists(config, client.childUrl(itemsUrl, "${entity.id}.json"))
        }

        client.putJson(
            config = config,
            url = client.childUrl(rootUrl, "recovery.json"),
            content = json.encodeToString(WebDavRecoveryFile.serializer(), WebDavRecoveryFile.fromDomain(recoveryMaterial)),
        )
        client.putJson(
            config = config,
            url = client.childUrl(rootUrl, "index.json"),
            content = json.encodeToString(
                WebDavIndex.serializer(),
                WebDavIndex(
                    items = activeItems.map { entity ->
                        WebDavIndexEntry(
                            id = entity.id,
                            revision = entity.revision,
                            updatedAt = entity.updatedAt,
                            deleted = false,
                        )
                    }
                ),
            ),
        )
        val nextManifest = manifest.copy(
            activeDeviceId = config.deviceId,
            lastBackupAt = timeProvider.now(),
        )
        client.putJson(
            config = config,
            url = client.childUrl(rootUrl, "manifest.json"),
            content = json.encodeToString(WebDavManifest.serializer(), nextManifest),
        )
        return nextManifest.toInfo(itemCount = activeItems.size)
    }

    suspend fun loadRemoteSnapshot(config: WebDavRuntimeConfig): WebDavRemoteSnapshot {
        val rootUrl = client.rootUrl(config)
        val manifest = loadManifest(config)
        val index = loadIndex(config)
        val recovery = loadRecovery(config)
        val itemsUrl = client.childUrl(rootUrl, "items")
        val entities = index.items.filterNot { it.deleted }.map { entry ->
            val itemText = client.getJsonIfExists(config, client.childUrl(itemsUrl, "${entry.id}.json"))
                ?: error("远端缺少记录 ${entry.id}")
            json.decodeFromString<WebDavItemFile>(itemText).toEntity()
        }
        return WebDavRemoteSnapshot(
            info = manifest.toInfo(itemCount = entities.size),
            recoveryMaterial = recovery.toDomain(),
            entities = entities,
        )
    }

    private suspend fun loadManifest(config: WebDavRuntimeConfig): WebDavManifest {
        val rootUrl = client.rootUrl(config)
        val manifestText = client.getJsonIfExists(config, client.childUrl(rootUrl, "manifest.json"))
            ?: error("远端不存在 WanPass 备份")
        return json.decodeFromString(manifestText)
    }

    private suspend fun loadIndex(config: WebDavRuntimeConfig): WebDavIndex {
        val rootUrl = client.rootUrl(config)
        val indexText = client.getJsonIfExists(config, client.childUrl(rootUrl, "index.json"))
            ?: error("远端备份缺少索引文件")
        return json.decodeFromString(indexText)
    }

    private suspend fun loadRecovery(config: WebDavRuntimeConfig): WebDavRecoveryFile {
        val rootUrl = client.rootUrl(config)
        val recoveryText = client.getJsonIfExists(config, client.childUrl(rootUrl, "recovery.json"))
            ?: error("远端备份缺少恢复材料")
        return json.decodeFromString(recoveryText)
    }
}
