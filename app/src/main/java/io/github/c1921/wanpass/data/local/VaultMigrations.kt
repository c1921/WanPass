package io.github.c1921.wanpass.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object VaultMigrations {
    val Migration1To2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE vault_items ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "UPDATE vault_items SET sort_order = updated_at"
            )
        }
    }
}
