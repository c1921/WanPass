package io.github.c1921.wanpass.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultItemDao {
    @Query("SELECT * FROM vault_items WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeActiveItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :itemId LIMIT 1")
    fun observeItem(itemId: String): Flow<VaultItemEntity?>

    @Query("SELECT * FROM vault_items WHERE deleted_at IS NULL")
    suspend fun getActiveItemsSnapshot(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: String): VaultItemEntity?

    @Upsert
    suspend fun upsert(entity: VaultItemEntity)
}
