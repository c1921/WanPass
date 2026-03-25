package io.github.c1921.wanpass.data.webdav

import io.github.c1921.wanpass.data.local.VaultItemEntity
import io.github.c1921.wanpass.domain.model.SyncState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavRemoteModelsTest {
    @Test
    fun `item file mapping should roundtrip encrypted entity fields`() {
        val entity = VaultItemEntity(
            id = "item-1",
            type = "login",
            titleCiphertext = byteArrayOf(1, 2, 3),
            contentCiphertext = byteArrayOf(4, 5, 6),
            searchBlobCiphertext = byteArrayOf(7, 8, 9),
            sortOrder = 30L,
            createdAt = 10L,
            updatedAt = 20L,
            deletedAt = null,
            revision = 3L,
            syncState = SyncState.PENDING_UPDATE.storageValue,
        )

        val mapped = entity.toWebDavItemFile().toEntity()

        assertEquals(entity.id, mapped.id)
        assertEquals(entity.type, mapped.type)
        assertArrayEquals(entity.titleCiphertext, mapped.titleCiphertext)
        assertArrayEquals(entity.contentCiphertext, mapped.contentCiphertext)
        assertArrayEquals(entity.searchBlobCiphertext, mapped.searchBlobCiphertext)
        assertEquals(entity.sortOrder, mapped.sortOrder)
        assertEquals(entity.createdAt, mapped.createdAt)
        assertEquals(entity.updatedAt, mapped.updatedAt)
        assertEquals(entity.revision, mapped.revision)
        assertEquals(SyncState.SYNCED.storageValue, mapped.syncState)
    }

    @Test
    fun `old item file without sort order should fall back to updated at`() {
        val json = Json { ignoreUnknownKeys = true }
        val itemFile = json.decodeFromString<WebDavItemFile>(
            """
            {
              "id": "item-1",
              "type": "login",
              "titleCiphertextBase64": "AQID",
              "contentCiphertextBase64": "BAUG",
              "searchBlobCiphertextBase64": "BwgJ",
              "createdAt": 10,
              "updatedAt": 20,
              "deletedAt": null,
              "revision": 3
            }
            """.trimIndent()
        )

        val mapped = itemFile.toEntity()

        assertEquals(20L, mapped.sortOrder)
    }

    @Test
    fun `recovery material mapping should roundtrip`() {
        val recovery = WebDavRecoveryFile(
            recoveryWrappedVaultKeyBase64 = "wrapped",
            recoverySaltBase64 = "salt",
            recoveryCodeCiphertextBase64 = "ciphertext",
        )

        val mapped = WebDavRecoveryFile.fromDomain(recovery.toDomain())

        assertEquals(recovery, mapped)
    }
}
