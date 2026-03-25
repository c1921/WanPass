package io.github.c1921.wanpass.domain.model

data class WebDavSettings(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val remoteRoot: String = "WanPass",
    val username: String = "",
    val hasPassword: Boolean = false,
    val deviceId: String = "",
    val lastSyncAt: Long? = null,
    val lastSyncError: String? = null,
)

data class WebDavRuntimeConfig(
    val baseUrl: String,
    val remoteRoot: String,
    val username: String,
    val password: String,
    val deviceId: String,
)

data class WebDavConfigDraft(
    val baseUrl: String = "",
    val remoteRoot: String = "WanPass",
    val username: String = "",
    val password: String = "",
    val preserveStoredPassword: Boolean = false,
)

data class VaultRecoveryMaterial(
    val recoveryWrappedVaultKeyBase64: String,
    val recoverySaltBase64: String,
    val recoveryCodeCiphertextBase64: String,
)

data class RemoteBackupInfo(
    val hasBackup: Boolean = false,
    val activeDeviceId: String? = null,
    val claimedAt: Long? = null,
    val lastBackupAt: Long? = null,
    val itemCount: Int = 0,
)
