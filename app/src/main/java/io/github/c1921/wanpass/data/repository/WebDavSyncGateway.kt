package io.github.c1921.wanpass.data.repository

import io.github.c1921.wanpass.data.local.VaultItemDao
import io.github.c1921.wanpass.data.local.VaultItemEntity
import io.github.c1921.wanpass.data.webdav.WebDavBackupService
import io.github.c1921.wanpass.data.webdav.WebDavConfigValidator
import io.github.c1921.wanpass.data.webdav.WebDavSyncPolicies
import io.github.c1921.wanpass.domain.model.RemoteBackupInfo
import io.github.c1921.wanpass.domain.model.SyncState
import io.github.c1921.wanpass.domain.model.WebDavConfigDraft
import io.github.c1921.wanpass.domain.model.WebDavRuntimeConfig
import io.github.c1921.wanpass.domain.model.WebDavSettings
import io.github.c1921.wanpass.domain.repository.SyncStatusProvider
import io.github.c1921.wanpass.domain.repository.VaultSettingsRepository
import io.github.c1921.wanpass.security.VaultKeyManager
import io.github.c1921.wanpass.session.VaultSessionManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface WebDavEnableResult {
    data object Enabled : WebDavEnableResult
    data class RequiresRemoteDecision(
        val remoteInfo: RemoteBackupInfo,
        val localHasData: Boolean,
    ) : WebDavEnableResult
}

@Singleton
class WebDavSyncGateway @Inject constructor(
    private val settingsRepository: VaultSettingsRepository,
    private val vaultItemDao: VaultItemDao,
    private val backupService: WebDavBackupService,
    private val vaultKeyManager: VaultKeyManager,
    private val sessionManager: VaultSessionManager,
) : SyncStatusProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val mutableSyncState = MutableStateFlow(SyncState.DISABLED)
    override val syncStateFlow: Flow<SyncState> = mutableSyncState
    private val mutableStatusText = MutableStateFlow("未启用 WebDAV 备份")
    override val statusTextFlow: Flow<String> = mutableStatusText

    init {
        scope.launch {
            settingsRepository.webDavSettingsFlow.collectLatest(::renderStoredStatus)
        }
    }

    suspend fun testConnection(draft: WebDavConfigDraft) {
        val config = normalizeDraft(draft)
        mutableSyncState.value = SyncState.PENDING_UPDATE
        mutableStatusText.value = "正在测试 WebDAV 连接..."
        backupService.testConnection(config)
        mutableStatusText.value = "WebDAV 连接可用"
    }

    suspend fun configureAndEnable(draft: WebDavConfigDraft): WebDavEnableResult = syncMutex.withLock {
        val config = normalizeDraft(draft)
        saveNormalizedConfig(config)
        settingsRepository.setWebDavEnabled(false)
        settingsRepository.setWebDavSyncStatus(lastSyncAt = null, lastSyncError = null)

        val remoteInfo = backupService.inspectRemote(config)
        val localCount = vaultItemDao.countActiveItems()
        return when {
            !remoteInfo.hasBackup -> {
                uploadLocalOverwriteRemoteInternal(config)
                WebDavEnableResult.Enabled
            }

            localCount == 0 -> {
                mutableSyncState.value = SyncState.CONFLICT
                mutableStatusText.value = "远端已有备份，请使用恢复码恢复到本机"
                WebDavEnableResult.RequiresRemoteDecision(remoteInfo = remoteInfo, localHasData = false)
            }

            else -> {
                mutableSyncState.value = SyncState.CONFLICT
                mutableStatusText.value = "远端已有备份，请选择上传本地或从远端恢复"
                WebDavEnableResult.RequiresRemoteDecision(remoteInfo = remoteInfo, localHasData = true)
            }
        }
    }

    suspend fun disable() = syncMutex.withLock {
        settingsRepository.setWebDavEnabled(false)
        vaultItemDao.updateAllActiveSyncStates(SyncState.DISABLED.storageValue)
        vaultItemDao.purgeDeletedItems()
        mutableSyncState.value = SyncState.DISABLED
        mutableStatusText.value = "未启用 WebDAV 备份"
    }

    fun requestSync() {
        scope.launch {
            runCatching { syncNow() }.onFailure { error ->
                settingsRepository.setWebDavSyncStatus(lastSyncAt = null, lastSyncError = error.message ?: "同步失败")
                mutableSyncState.value = SyncState.CONFLICT
                mutableStatusText.value = "同步失败：${error.message ?: "未知错误"}"
            }
        }
    }

    suspend fun syncNow() = syncMutex.withLock {
        val settings = settingsRepository.loadWebDavSettings()
        if (!settings.enabled) {
            mutableSyncState.value = SyncState.DISABLED
            mutableStatusText.value = "未启用 WebDAV 备份"
            return
        }
        val config = settingsRepository.loadWebDavRuntimeConfig() ?: error("请先填写完整的 WebDAV 配置")
        val remoteInfo = backupService.inspectRemote(config)
        if (remoteInfo.hasBackup && !WebDavSyncPolicies.isRemoteWritable(remoteInfo.activeDeviceId, config.deviceId)) {
            val message = "远端备份当前由其他设备接管，请恢复或手动接管"
            settingsRepository.setWebDavSyncStatus(lastSyncAt = settings.lastSyncAt, lastSyncError = message)
            mutableSyncState.value = SyncState.CONFLICT
            mutableStatusText.value = message
            return
        }

        val allItems = vaultItemDao.getAllItemsSnapshot()
        val pendingItems = allItems.filter { it.syncState in PendingSyncStates }
        val recoveryMaterial = vaultKeyManager.loadRecoveryMaterial() ?: error("本地恢复材料缺失，无法备份")
        if (!remoteInfo.hasBackup) {
            uploadLocalOverwriteRemoteInternal(config)
            return
        }
        if (pendingItems.isEmpty()) {
            mutableSyncState.value = SyncState.SYNCED
            mutableStatusText.value = buildSyncedMessage(settings)
            return
        }

        mutableSyncState.value = SyncState.PENDING_UPDATE
        mutableStatusText.value = "正在同步到 WebDAV..."
        val syncedInfo = backupService.syncPending(
            config = config,
            allItems = allItems,
            recoveryMaterial = recoveryMaterial,
        )
        markPendingItemsSynced(pendingItems)
        settingsRepository.setWebDavEnabled(true)
        settingsRepository.setWebDavSyncStatus(lastSyncAt = syncedInfo.lastBackupAt, lastSyncError = null)
        mutableSyncState.value = SyncState.SYNCED
        mutableStatusText.value = "WebDAV 已同步至 ${formatTimestamp(syncedInfo.lastBackupAt ?: System.currentTimeMillis())}"
    }

    suspend fun uploadLocalOverwriteRemote() = syncMutex.withLock {
        val config = settingsRepository.loadWebDavRuntimeConfig() ?: error("请先填写完整的 WebDAV 配置")
        uploadLocalOverwriteRemoteInternal(config)
    }

    suspend fun takeOverRemote() = uploadLocalOverwriteRemote()

    suspend fun restoreFromRemote(
        recoveryCode: String,
        draft: WebDavConfigDraft? = null,
    ) = syncMutex.withLock {
        val config = draft?.let { normalizeDraft(it) }
            ?: (settingsRepository.loadWebDavRuntimeConfig() ?: error("请先填写完整的 WebDAV 配置"))
        if (draft != null) {
            saveNormalizedConfig(config)
        }
        mutableSyncState.value = SyncState.PENDING_UPDATE
        mutableStatusText.value = "正在从 WebDAV 恢复..."

        val snapshot = backupService.loadRemoteSnapshot(config)
        val vaultKey = vaultKeyManager.recoverVaultKey(recoveryCode, snapshot.recoveryMaterial)
        vaultKeyManager.persistRecoveredVault(vaultKey, snapshot.recoveryMaterial)
        vaultItemDao.replaceAll(snapshot.entities)
        settingsRepository.setWebDavEnabled(true)
        settingsRepository.setWebDavSyncStatus(lastSyncAt = snapshot.info.lastBackupAt, lastSyncError = null)
        sessionManager.importFreshVault(vaultKey)
        runCatching {
            backupService.uploadSnapshot(
                config = config,
                allItems = vaultItemDao.getAllItemsSnapshot(),
                recoveryMaterial = snapshot.recoveryMaterial,
                claimedAt = snapshot.info.claimedAt,
            )
        }.onSuccess { info ->
            settingsRepository.setWebDavSyncStatus(lastSyncAt = info.lastBackupAt, lastSyncError = null)
        }.onFailure { error ->
            settingsRepository.setWebDavSyncStatus(
                lastSyncAt = snapshot.info.lastBackupAt,
                lastSyncError = "已恢复到本机，但接管远端失败：${error.message ?: "未知错误"}",
            )
        }
        mutableSyncState.value = SyncState.SYNCED
        mutableStatusText.value = "已从 WebDAV 恢复并接管当前备份"
    }

    private suspend fun uploadLocalOverwriteRemoteInternal(config: WebDavRuntimeConfig) {
        val allItems = vaultItemDao.getAllItemsSnapshot()
        val recoveryMaterial = vaultKeyManager.loadRecoveryMaterial() ?: error("本地恢复材料缺失，无法备份")
        mutableSyncState.value = SyncState.PENDING_UPDATE
        mutableStatusText.value = "正在上传本地数据到 WebDAV..."
        val remoteInfo = backupService.uploadSnapshot(
            config = config,
            allItems = allItems,
            recoveryMaterial = recoveryMaterial,
        )
        vaultItemDao.updateAllActiveSyncStates(SyncState.SYNCED.storageValue)
        vaultItemDao.purgeDeletedItems()
        settingsRepository.setWebDavEnabled(true)
        settingsRepository.setWebDavSyncStatus(lastSyncAt = remoteInfo.lastBackupAt, lastSyncError = null)
        mutableSyncState.value = SyncState.SYNCED
        mutableStatusText.value = "WebDAV 已同步至 ${formatTimestamp(remoteInfo.lastBackupAt ?: System.currentTimeMillis())}"
    }

    private suspend fun markPendingItemsSynced(pendingItems: List<VaultItemEntity>) {
        val deleteIds = pendingItems.filter { it.syncState == SyncState.PENDING_DELETE.storageValue }.map { it.id }
        val upsertIds = pendingItems.filterNot { it.syncState == SyncState.PENDING_DELETE.storageValue }.map { it.id }
        if (upsertIds.isNotEmpty()) {
            vaultItemDao.updateSyncStates(upsertIds, SyncState.SYNCED.storageValue)
        }
        if (deleteIds.isNotEmpty()) {
            vaultItemDao.deleteByIds(deleteIds)
        }
    }

    private suspend fun normalizeDraft(draft: WebDavConfigDraft): WebDavRuntimeConfig {
        val existingPassword = if (draft.preserveStoredPassword) {
            settingsRepository.loadWebDavRuntimeConfig()?.password
        } else {
            null
        }
        return WebDavConfigValidator.normalize(
            draft = draft,
            deviceId = settingsRepository.ensureWebDavDeviceId(),
            existingPassword = existingPassword,
        )
    }

    private suspend fun saveNormalizedConfig(config: WebDavRuntimeConfig) {
        settingsRepository.saveWebDavConfig(
            WebDavConfigDraft(
                baseUrl = config.baseUrl,
                remoteRoot = config.remoteRoot,
                username = config.username,
                password = config.password,
                preserveStoredPassword = false,
            )
        )
    }

    private fun renderStoredStatus(settings: WebDavSettings) {
        if (!settings.enabled) {
            mutableSyncState.value = SyncState.DISABLED
            if (mutableStatusText.value.startsWith("远端已有备份")) return
            mutableStatusText.value = "未启用 WebDAV 备份"
            return
        }
        if (!settings.lastSyncError.isNullOrBlank()) {
            mutableSyncState.value = SyncState.CONFLICT
            mutableStatusText.value = "同步异常：${settings.lastSyncError}"
            return
        }
        if (settings.lastSyncAt != null) {
            mutableSyncState.value = SyncState.SYNCED
            mutableStatusText.value = buildSyncedMessage(settings)
            return
        }
        mutableSyncState.value = SyncState.PENDING_UPDATE
        mutableStatusText.value = "已启用 WebDAV，等待首次同步"
    }

    private fun buildSyncedMessage(settings: WebDavSettings): String =
        if (settings.lastSyncAt == null) {
            "已启用 WebDAV，等待首次同步"
        } else {
            "WebDAV 已同步至 ${formatTimestamp(settings.lastSyncAt)}"
        }

    private fun formatTimestamp(value: Long): String = TimestampFormatter.format(
        Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
    )

    private companion object {
        val PendingSyncStates = setOf(
            SyncState.PENDING_CREATE.storageValue,
            SyncState.PENDING_UPDATE.storageValue,
            SyncState.PENDING_DELETE.storageValue,
        )
        val TimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
