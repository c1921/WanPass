package io.github.c1921.wanpass.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [VaultItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultItemDao(): VaultItemDao
}
