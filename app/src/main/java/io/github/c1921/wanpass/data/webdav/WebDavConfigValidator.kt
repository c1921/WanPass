package io.github.c1921.wanpass.data.webdav

import io.github.c1921.wanpass.domain.model.WebDavConfigDraft
import io.github.c1921.wanpass.domain.model.WebDavRuntimeConfig
import java.net.URI

object WebDavConfigValidator {
    fun normalize(
        draft: WebDavConfigDraft,
        deviceId: String,
        existingPassword: String? = null,
    ): WebDavRuntimeConfig {
        val baseUrl = draft.baseUrl.trim().removeSuffix("/")
        require(baseUrl.startsWith("https://")) { "仅支持 HTTPS WebDAV 地址" }
        val uri = runCatching { URI(baseUrl) }.getOrNull()
        require(uri != null && !uri.host.isNullOrBlank()) { "请输入有效的 WebDAV 地址" }

        val remoteRoot = draft.remoteRoot.trim().trim('/').ifBlank { "WanPass" }
        val username = draft.username.trim()
        require(username.isNotBlank()) { "请输入 WebDAV 用户名" }

        val password = when {
            draft.password.isNotBlank() -> draft.password
            draft.preserveStoredPassword && !existingPassword.isNullOrBlank() -> existingPassword
            else -> error("请输入 WebDAV 密码")
        }

        return WebDavRuntimeConfig(
            baseUrl = baseUrl,
            remoteRoot = remoteRoot,
            username = username,
            password = password,
            deviceId = deviceId,
        )
    }
}
