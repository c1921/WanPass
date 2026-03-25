package io.github.c1921.wanpass.security

import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException

internal const val KeystoreAuthTimeoutSeconds = 300
internal const val KeystoreAuthTypes = KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL

internal fun Throwable.securityActionMessage(fallback: String): String = when (this) {
    is UserNotAuthenticatedException -> "请先完成系统身份验证后再继续"
    else -> message ?: fallback
}
