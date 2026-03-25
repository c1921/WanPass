package io.github.c1921.wanpass.data.webdav

import io.github.c1921.wanpass.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavSyncPoliciesTest {
    @Test
    fun `upsert state should reflect create update and disabled cases`() {
        assertEquals(SyncState.DISABLED, WebDavSyncPolicies.upsertState(webDavEnabled = false, isExistingItem = false))
        assertEquals(SyncState.PENDING_CREATE, WebDavSyncPolicies.upsertState(webDavEnabled = true, isExistingItem = false))
        assertEquals(SyncState.PENDING_UPDATE, WebDavSyncPolicies.upsertState(webDavEnabled = true, isExistingItem = true))
    }

    @Test
    fun `delete state should be pending only when webdav is enabled`() {
        assertEquals(SyncState.DISABLED, WebDavSyncPolicies.deleteState(webDavEnabled = false))
        assertEquals(SyncState.PENDING_DELETE, WebDavSyncPolicies.deleteState(webDavEnabled = true))
    }

    @Test
    fun `remote writable should allow same device or unclaimed owner`() {
        assertTrue(WebDavSyncPolicies.isRemoteWritable(activeDeviceId = null, deviceId = "device-1"))
        assertTrue(WebDavSyncPolicies.isRemoteWritable(activeDeviceId = "device-1", deviceId = "device-1"))
        assertFalse(WebDavSyncPolicies.isRemoteWritable(activeDeviceId = "device-2", deviceId = "device-1"))
    }
}
