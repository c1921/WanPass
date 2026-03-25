package io.github.c1921.wanpass.data.webdav

import io.github.c1921.wanpass.domain.model.SyncState

object WebDavSyncPolicies {
    fun upsertState(webDavEnabled: Boolean, isExistingItem: Boolean): SyncState = when {
        !webDavEnabled -> SyncState.DISABLED
        !isExistingItem -> SyncState.PENDING_CREATE
        else -> SyncState.PENDING_UPDATE
    }

    fun deleteState(webDavEnabled: Boolean): SyncState =
        if (webDavEnabled) SyncState.PENDING_DELETE else SyncState.DISABLED

    fun isRemoteWritable(activeDeviceId: String?, deviceId: String): Boolean =
        activeDeviceId.isNullOrBlank() || activeDeviceId == deviceId
}
