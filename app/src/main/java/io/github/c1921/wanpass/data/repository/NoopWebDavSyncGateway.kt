package io.github.c1921.wanpass.data.repository

import io.github.c1921.wanpass.domain.model.SyncState
import io.github.c1921.wanpass.domain.repository.SyncStatusProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Singleton
class NoopWebDavSyncGateway @Inject constructor() : SyncStatusProvider {
    override val syncStateFlow: Flow<SyncState> = flowOf(SyncState.DISABLED)
    override val statusTextFlow: Flow<String> = flowOf("未启用（后续预留 WebDAV）")
}
