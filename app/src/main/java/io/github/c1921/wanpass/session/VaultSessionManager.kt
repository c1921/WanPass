package io.github.c1921.wanpass.session

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import io.github.c1921.wanpass.core.TimeProvider
import io.github.c1921.wanpass.domain.model.UnlockResult
import io.github.c1921.wanpass.domain.repository.VaultSettingsRepository
import io.github.c1921.wanpass.security.VaultKeyManager
import io.github.c1921.wanpass.security.WebDavCredentialSessionCache
import io.github.c1921.wanpass.security.securityActionMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

enum class VaultSessionState {
    LOCKED,
    UNLOCKED,
}

@Singleton
class VaultSessionManager @Inject constructor(
    private val vaultKeyManager: VaultKeyManager,
    private val searchLoader: VaultSearchLoader,
    private val searchIndex: SearchIndex,
    private val settingsRepository: VaultSettingsRepository,
    private val timeProvider: TimeProvider,
    private val webDavCredentialSessionCache: WebDavCredentialSessionCache,
) : VaultKeyProvider {
    private val mutableSessionState = MutableStateFlow(VaultSessionState.LOCKED)
    val sessionState: StateFlow<VaultSessionState> = mutableSessionState.asStateFlow()

    private val mutableVaultKeyFlow = MutableStateFlow<ByteArray?>(null)
    override val vaultKeyFlow: StateFlow<ByteArray?> = mutableVaultKeyFlow.asStateFlow()

    private var lastBackgroundedAt: Long? = null

    suspend fun unlock(): UnlockResult = try {
        val vaultKey = vaultKeyManager.unwrapVaultKey()
        activate(vaultKey)
        UnlockResult.Success
    } catch (_: KeyPermanentlyInvalidatedException) {
        UnlockResult.NeedsRecovery
    } catch (error: UserNotAuthenticatedException) {
        UnlockResult.Failure(error.securityActionMessage("请先完成系统身份验证后再继续"))
    } catch (error: Throwable) {
        UnlockResult.NeedsRecovery
    }

    suspend fun importFreshVault(vaultKey: ByteArray) {
        activate(vaultKey)
    }

    suspend fun recoverAndUnlock(recoveryCode: String): UnlockResult = try {
        val vaultKey = vaultKeyManager.recoverAndRebindVault(recoveryCode)
        activate(vaultKey)
        UnlockResult.Success
    } catch (error: UserNotAuthenticatedException) {
        UnlockResult.Failure(error.securityActionMessage("请先完成系统身份验证后再继续"))
    } catch (error: Throwable) {
        UnlockResult.Failure("恢复码无效")
    }

    suspend fun lock() {
        mutableVaultKeyFlow.value?.fill(0)
        mutableVaultKeyFlow.value = null
        mutableSessionState.value = VaultSessionState.LOCKED
        lastBackgroundedAt = null
        webDavCredentialSessionCache.clear()
        searchIndex.clear()
    }

    suspend fun onAppBackgrounded() {
        lastBackgroundedAt = timeProvider.now()
        val settings = settingsRepository.settingsFlow.first()
        if (settings.autoLockDuration.millis == 0L) {
            lock()
        }
    }

    suspend fun onAppForegrounded() {
        val backgroundedAt = lastBackgroundedAt ?: return
        val settings = settingsRepository.settingsFlow.first()
        if (mutableSessionState.value == VaultSessionState.UNLOCKED && settings.autoLockDuration.millis > 0L) {
            val elapsed = timeProvider.now() - backgroundedAt
            if (elapsed >= settings.autoLockDuration.millis) {
                lock()
            }
        }
        lastBackgroundedAt = null
    }

    override fun requireVaultKey(): ByteArray =
        mutableVaultKeyFlow.value?.copyOf() ?: error("Vault is locked")

    private suspend fun activate(vaultKey: ByteArray) {
        mutableVaultKeyFlow.value?.fill(0)
        mutableVaultKeyFlow.value = vaultKey.copyOf()
        mutableSessionState.value = VaultSessionState.UNLOCKED
        searchIndex.replace(searchLoader.load(vaultKey))
        lastBackgroundedAt = null
    }
}
