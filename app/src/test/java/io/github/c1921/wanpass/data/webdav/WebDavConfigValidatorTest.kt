package io.github.c1921.wanpass.data.webdav

import io.github.c1921.wanpass.domain.model.WebDavConfigDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class WebDavConfigValidatorTest {
    @Test
    fun `normalize should trim values and keep stored password when requested`() {
        val result = WebDavConfigValidator.normalize(
            draft = WebDavConfigDraft(
                baseUrl = " https://dav.example.com/root/ ",
                remoteRoot = " /WanPass/backups/ ",
                username = " alice ",
                password = "",
                preserveStoredPassword = true,
            ),
            deviceId = "device-1",
            existingPassword = "secret",
        )

        assertEquals("https://dav.example.com/root", result.baseUrl)
        assertEquals("WanPass/backups", result.remoteRoot)
        assertEquals("alice", result.username)
        assertEquals("secret", result.password)
        assertEquals("device-1", result.deviceId)
    }

    @Test
    fun `normalize should default blank remote root to WanPass`() {
        val result = WebDavConfigValidator.normalize(
            draft = WebDavConfigDraft(
                baseUrl = "https://dav.example.com",
                remoteRoot = "   ",
                username = "alice",
                password = "secret",
            ),
            deviceId = "device-1",
        )

        assertEquals("WanPass", result.remoteRoot)
    }

    @Test
    fun `normalize should reject non https url`() {
        try {
            WebDavConfigValidator.normalize(
                draft = WebDavConfigDraft(
                    baseUrl = "http://dav.example.com",
                    remoteRoot = "WanPass",
                    username = "alice",
                    password = "secret",
                ),
                deviceId = "device-1",
            )
            fail("Expected validator to reject http")
        } catch (error: IllegalArgumentException) {
            assertEquals("仅支持 HTTPS WebDAV 地址", error.message)
        }
    }
}
