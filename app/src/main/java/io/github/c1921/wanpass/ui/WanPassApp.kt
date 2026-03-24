package io.github.c1921.wanpass.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.c1921.wanpass.session.VaultSessionState
import io.github.c1921.wanpass.ui.home.WanPassMainGraph
import io.github.c1921.wanpass.ui.onboarding.OnboardingRoute
import io.github.c1921.wanpass.ui.theme.WanPassTheme
import io.github.c1921.wanpass.ui.unlock.UnlockRoute

@Composable
fun WanPassApp(
    viewModel: AppViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveProcessLifecycle(
        onBackgrounded = viewModel::onAppBackgrounded,
        onForegrounded = viewModel::onAppForegrounded,
    )

    WanPassTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                !uiState.deviceSecure -> DeviceSecurityRequiredScreen()
                !uiState.onboardingComplete -> OnboardingRoute()
                uiState.sessionState != VaultSessionState.UNLOCKED -> UnlockRoute(
                    biometricEnabled = uiState.biometricEnabled
                )
                else -> WanPassMainGraph()
            }
        }
    }
}

@Composable
private fun ObserveProcessLifecycle(
    onBackgrounded: () -> Unit,
    onForegrounded: () -> Unit,
) {
    val lifecycleOwner = ProcessLifecycleOwner.get()
    val latestBackgrounded by rememberUpdatedState(onBackgrounded)
    val latestForegrounded by rememberUpdatedState(onForegrounded)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> latestBackgrounded()
                Lifecycle.Event.ON_START -> latestForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun DeviceSecurityRequiredScreen() {
    SecureWindowEffect(enabled = false)
    val context = LocalContext.current
    val openSettings = {
        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "需要先开启系统锁屏", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "WanPass 依赖设备密码或生物识别保护保险箱。请先在系统设置中启用锁屏密码，再返回继续。",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = openSettings,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text("打开系统安全设置")
        }
    }
}
