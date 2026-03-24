package io.github.c1921.wanpass.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AuthPromptController(
    val authenticateBiometric: () -> Unit,
    val authenticateDeviceCredential: () -> Unit,
)

@Composable
fun rememberAuthPromptController(
    title: String,
    subtitle: String? = null,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
): AuthPromptController {
    val activity = LocalContext.current.findActivity()
    val executor = remember(activity) { ContextCompat.getMainExecutor(activity) }
    val prompt = remember(activity, executor, onSuccess, onFailure) {
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val shouldIgnore = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    if (!shouldIgnore) {
                        onFailure(errString.toString())
                    }
                }
            },
        )
    }
    return remember(prompt, title, subtitle) {
        AuthPromptController(
            authenticateBiometric = {
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                        .setNegativeButtonText("取消")
                        .build()
                )
            },
            authenticateDeviceCredential = {
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        .build()
                )
            },
        )
    }
}

@Composable
fun SecureWindowEffect(enabled: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, enabled) {
        if (enabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

fun isStrongBiometricAvailable(context: Context): Boolean =
    BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

fun isDeviceSecure(context: Context): Boolean =
    context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

fun formatTimestamp(value: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .format(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()))

private tailrec fun Context.findActivity(): FragmentActivity = when (this) {
    is FragmentActivity -> this
    is ComponentActivity -> error("Main activity must extend FragmentActivity for BiometricPrompt")
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Activity not found")
}
