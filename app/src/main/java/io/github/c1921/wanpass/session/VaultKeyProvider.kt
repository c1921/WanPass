package io.github.c1921.wanpass.session

import kotlinx.coroutines.flow.StateFlow

interface VaultKeyProvider {
    val vaultKeyFlow: StateFlow<ByteArray?>
    fun requireVaultKey(): ByteArray
}
