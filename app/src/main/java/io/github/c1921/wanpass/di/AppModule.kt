package io.github.c1921.wanpass.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.c1921.wanpass.core.SystemTimeProvider
import io.github.c1921.wanpass.core.TimeProvider
import io.github.c1921.wanpass.data.local.VaultDatabase
import io.github.c1921.wanpass.data.repository.WebDavSyncGateway
import io.github.c1921.wanpass.data.repository.VaultRepositoryImpl
import io.github.c1921.wanpass.data.repository.VaultSettingsRepositoryImpl
import io.github.c1921.wanpass.domain.repository.SyncStatusProvider
import io.github.c1921.wanpass.domain.repository.VaultRepository
import io.github.c1921.wanpass.domain.repository.VaultSettingsRepository
import io.github.c1921.wanpass.security.AesGcmVaultCryptoEngine
import io.github.c1921.wanpass.security.VaultCryptoEngine
import io.github.c1921.wanpass.session.VaultKeyProvider
import io.github.c1921.wanpass.session.VaultSessionManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds
    @Singleton
    abstract fun bindVaultRepository(impl: VaultRepositoryImpl): VaultRepository

    @Binds
    @Singleton
    abstract fun bindVaultSettingsRepository(impl: VaultSettingsRepositoryImpl): VaultSettingsRepository

    @Binds
    @Singleton
    abstract fun bindSyncStatusProvider(impl: WebDavSyncGateway): SyncStatusProvider

    @Binds
    @Singleton
    abstract fun bindVaultCryptoEngine(impl: AesGcmVaultCryptoEngine): VaultCryptoEngine

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds
    @Singleton
    abstract fun bindVaultKeyProvider(impl: VaultSessionManager): VaultKeyProvider
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = {
                context.filesDir.resolve("datastore").apply { mkdirs() }.resolve("wanpass.preferences_pb")
            }
        )

    @Provides
    @Singleton
    fun provideVaultDatabase(@ApplicationContext context: Context): VaultDatabase =
        Room.databaseBuilder(context, VaultDatabase::class.java, "wanpass.db").build()

    @Provides
    fun provideVaultItemDao(database: VaultDatabase) = database.vaultItemDao()
}
