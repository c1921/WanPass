package io.github.c1921.wanpass.session

import io.github.c1921.wanpass.core.SearchNormalizer
import io.github.c1921.wanpass.data.local.VaultItemDao
import io.github.c1921.wanpass.domain.model.SearchEntry
import io.github.c1921.wanpass.security.VaultCryptoEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultSearchLoader @Inject constructor(
    private val vaultItemDao: VaultItemDao,
    private val cryptoEngine: VaultCryptoEngine,
) {
    suspend fun load(vaultKey: ByteArray): List<SearchEntry> = vaultItemDao.getActiveItemsSnapshot().map { entity ->
        SearchEntry(
            id = entity.id,
            normalizedText = SearchNormalizer.normalize(
                cryptoEngine.decryptString(vaultKey, entity.searchBlobCiphertext)
            ),
        )
    }
}
