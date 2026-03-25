package io.github.c1921.wanpass.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class WebDavEnablePathTest {
    @Test
    fun `remote backup should enable directly when remote is empty`() {
        val result = classifyWebDavEnablePath(
            remoteHasBackup = false,
            localItemCount = 0,
        )

        assertEquals(WebDavEnablePath.ENABLE_DIRECTLY, result)
    }

    @Test
    fun `remote backup should require restore onboarding when local vault is empty`() {
        val result = classifyWebDavEnablePath(
            remoteHasBackup = true,
            localItemCount = 0,
        )

        assertEquals(WebDavEnablePath.RESTORE_ONBOARDING, result)
    }

    @Test
    fun `remote backup should require overwrite confirmation when local vault has data`() {
        val result = classifyWebDavEnablePath(
            remoteHasBackup = true,
            localItemCount = 3,
        )

        assertEquals(WebDavEnablePath.OVERWRITE_REMOTE, result)
    }
}
