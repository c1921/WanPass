package io.github.c1921.wanpass.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val type: String,
    @ColumnInfo(name = "title_ciphertext") val titleCiphertext: ByteArray,
    @ColumnInfo(name = "content_ciphertext") val contentCiphertext: ByteArray,
    @ColumnInfo(name = "search_blob_ciphertext") val searchBlobCiphertext: ByteArray,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    val revision: Long,
    @ColumnInfo(name = "sync_state") val syncState: String,
)
