package io.github.c1921.wanpass.data.webdav

import io.github.c1921.wanpass.core.Base64Codec
import io.github.c1921.wanpass.data.local.VaultItemEntity
import io.github.c1921.wanpass.domain.model.RemoteBackupInfo
import io.github.c1921.wanpass.domain.model.SyncState
import io.github.c1921.wanpass.domain.model.VaultRecoveryMaterial
import kotlinx.serialization.Serializable

@Serializable
data class WebDavManifest(
    val schemaVersion: Int = SchemaVersion,
    val activeDeviceId: String,
    val claimedAt: Long,
    val lastBackupAt: Long,
) {
    fun toInfo(itemCount: Int): RemoteBackupInfo = RemoteBackupInfo(
        hasBackup = true,
        activeDeviceId = activeDeviceId,
        claimedAt = claimedAt,
        lastBackupAt = lastBackupAt,
        itemCount = itemCount,
    )

    companion object {
        const val SchemaVersion = 1
    }
}

@Serializable
data class WebDavIndex(
    val items: List<WebDavIndexEntry>,
)

@Serializable
data class WebDavIndexEntry(
    val id: String,
    val revision: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class WebDavItemFile(
    val id: String,
    val type: String,
    val titleCiphertextBase64: String,
    val contentCiphertextBase64: String,
    val searchBlobCiphertextBase64: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val revision: Long,
)

@Serializable
data class WebDavRecoveryFile(
    val recoveryWrappedVaultKeyBase64: String,
    val recoverySaltBase64: String,
    val recoveryCodeCiphertextBase64: String,
) {
    fun toDomain(): VaultRecoveryMaterial = VaultRecoveryMaterial(
        recoveryWrappedVaultKeyBase64 = recoveryWrappedVaultKeyBase64,
        recoverySaltBase64 = recoverySaltBase64,
        recoveryCodeCiphertextBase64 = recoveryCodeCiphertextBase64,
    )

    companion object {
        fun fromDomain(material: VaultRecoveryMaterial): WebDavRecoveryFile = WebDavRecoveryFile(
            recoveryWrappedVaultKeyBase64 = material.recoveryWrappedVaultKeyBase64,
            recoverySaltBase64 = material.recoverySaltBase64,
            recoveryCodeCiphertextBase64 = material.recoveryCodeCiphertextBase64,
        )
    }
}

data class WebDavRemoteSnapshot(
    val info: RemoteBackupInfo,
    val recoveryMaterial: VaultRecoveryMaterial,
    val entities: List<VaultItemEntity>,
)

internal fun VaultItemEntity.toWebDavItemFile(): WebDavItemFile = WebDavItemFile(
    id = id,
    type = type,
    titleCiphertextBase64 = Base64Codec.encode(titleCiphertext),
    contentCiphertextBase64 = Base64Codec.encode(contentCiphertext),
    searchBlobCiphertextBase64 = Base64Codec.encode(searchBlobCiphertext),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    revision = revision,
)

internal fun WebDavItemFile.toEntity(): VaultItemEntity = VaultItemEntity(
    id = id,
    type = type,
    titleCiphertext = Base64Codec.decode(titleCiphertextBase64),
    contentCiphertext = Base64Codec.decode(contentCiphertextBase64),
    searchBlobCiphertext = Base64Codec.decode(searchBlobCiphertextBase64),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    revision = revision,
    syncState = SyncState.SYNCED.storageValue,
)
