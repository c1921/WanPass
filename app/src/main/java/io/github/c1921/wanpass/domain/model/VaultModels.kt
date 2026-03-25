package io.github.c1921.wanpass.domain.model

import kotlinx.serialization.Serializable

enum class VaultItemType(val storageValue: String) {
    LOGIN("login"),
    NOTE("note");

    companion object {
        fun fromStorage(value: String): VaultItemType = entries.firstOrNull { it.storageValue == value } ?: LOGIN
    }
}

enum class SyncState(val storageValue: String) {
    DISABLED("disabled"),
    SYNCED("synced"),
    PENDING_CREATE("pending_create"),
    PENDING_UPDATE("pending_update"),
    PENDING_DELETE("pending_delete"),
    CONFLICT("conflict");

    companion object {
        fun fromStorage(value: String): SyncState = entries.firstOrNull { it.storageValue == value } ?: DISABLED
    }
}

enum class AutoLockDuration(val storageValue: String, val millis: Long) {
    IMMEDIATELY("immediately", 0L),
    THIRTY_SECONDS("30_seconds", 30_000L),
    ONE_MINUTE("1_minute", 60_000L),
    FIVE_MINUTES("5_minutes", 300_000L);

    companion object {
        fun fromStorage(value: String): AutoLockDuration =
            entries.firstOrNull { it.storageValue == value } ?: THIRTY_SECONDS
    }
}

@Serializable
data class LoginContent(
    val title: String,
    val account: String = "",
    val password: String = "",
    val site: String = "",
    val note: String = "",
)

@Serializable
data class NoteContent(
    val title: String,
    val body: String,
    val note: String = "",
)

sealed interface VaultItem {
    val id: String
    val title: String
    val type: VaultItemType
    val createdAt: Long
    val updatedAt: Long
    val revision: Long

    data class Login(
        override val id: String,
        override val title: String,
        override val createdAt: Long,
        override val updatedAt: Long,
        override val revision: Long,
        val account: String,
        val password: String,
        val site: String,
        val note: String,
    ) : VaultItem {
        override val type: VaultItemType = VaultItemType.LOGIN
    }

    data class Note(
        override val id: String,
        override val title: String,
        override val createdAt: Long,
        override val updatedAt: Long,
        override val revision: Long,
        val body: String,
        val note: String,
    ) : VaultItem {
        override val type: VaultItemType = VaultItemType.NOTE
    }
}

data class VaultItemSummary(
    val id: String,
    val title: String,
    val type: VaultItemType,
    val sortOrder: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class SearchEntry(
    val id: String,
    val normalizedText: String,
)

data class VaultSettings(
    val onboardingComplete: Boolean = false,
    val biometricEnabled: Boolean = true,
    val autoLockDuration: AutoLockDuration = AutoLockDuration.THIRTY_SECONDS,
)

data class VaultKeyMetadata(
    val wrappedVaultKeyBase64: String,
    val recoveryWrappedVaultKeyBase64: String,
    val recoverySaltBase64: String,
    val recoveryCodeCiphertextBase64: String,
)

data class PendingSetupVault(
    val vaultKey: ByteArray,
    val recoveryCode: String,
)

sealed interface UnlockResult {
    data object Success : UnlockResult
    data object NeedsRecovery : UnlockResult
    data class Failure(val reason: String) : UnlockResult
}
