package io.github.c1921.wanpass.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultItemDao {
    @Query("SELECT * FROM vault_items WHERE deleted_at IS NULL ORDER BY sort_order DESC, updated_at DESC, id DESC")
    fun observeActiveItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :itemId LIMIT 1")
    fun observeItem(itemId: String): Flow<VaultItemEntity?>

    @Query("SELECT * FROM vault_items WHERE deleted_at IS NULL ORDER BY sort_order DESC, updated_at DESC, id DESC")
    suspend fun getActiveItemsSnapshot(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items ORDER BY updated_at DESC")
    suspend fun getAllItemsSnapshot(): List<VaultItemEntity>

    @Query("SELECT MAX(sort_order) FROM vault_items WHERE deleted_at IS NULL AND type = :type")
    suspend fun getMaxSortOrder(type: String): Long?

    @Query("SELECT * FROM vault_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: String): VaultItemEntity?

    @Query("SELECT COUNT(*) FROM vault_items WHERE deleted_at IS NULL")
    suspend fun countActiveItems(): Int

    @Query("UPDATE vault_items SET sync_state = :syncState WHERE id = :itemId")
    suspend fun updateSyncState(itemId: String, syncState: String)

    @Query("UPDATE vault_items SET sync_state = :syncState WHERE id IN (:itemIds)")
    suspend fun updateSyncStates(itemIds: List<String>, syncState: String)

    @Query("UPDATE vault_items SET sync_state = :syncState WHERE deleted_at IS NULL")
    suspend fun updateAllActiveSyncStates(syncState: String)

    @Query("DELETE FROM vault_items WHERE id IN (:itemIds)")
    suspend fun deleteByIds(itemIds: List<String>)

    @Query("DELETE FROM vault_items WHERE deleted_at IS NOT NULL")
    suspend fun purgeDeletedItems()

    @Query("DELETE FROM vault_items")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsert(entity: VaultItemEntity)

    @Upsert
    suspend fun upsertAll(entities: List<VaultItemEntity>)

    @androidx.room.Transaction
    suspend fun replaceAll(entities: List<VaultItemEntity>) {
        deleteAll()
        if (entities.isNotEmpty()) {
            upsertAll(entities)
        }
    }
}
