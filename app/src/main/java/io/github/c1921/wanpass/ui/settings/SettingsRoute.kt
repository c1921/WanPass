package io.github.c1921.wanpass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.c1921.wanpass.domain.model.AutoLockDuration
import io.github.c1921.wanpass.domain.repository.SyncStatusProvider
import io.github.c1921.wanpass.domain.repository.VaultSettingsRepository
import io.github.c1921.wanpass.security.VaultKeyManager
import io.github.c1921.wanpass.session.VaultSessionManager
import io.github.c1921.wanpass.ui.SecureWindowEffect
import io.github.c1921.wanpass.ui.rememberAuthPromptController
import io.github.c1921.wanpass.ui.isStrongBiometricAvailable
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val biometricEnabled: Boolean = true,
    val autoLockDuration: AutoLockDuration = AutoLockDuration.THIRTY_SECONDS,
    val syncStatusText: String = "未启用（后续预留 WebDAV）",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: VaultSettingsRepository,
    syncStatusProvider: SyncStatusProvider,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        syncStatusProvider.statusTextFlow,
    ) { settings, syncText ->
        SettingsUiState(
            biometricEnabled = settings.biometricEnabled,
            autoLockDuration = settings.autoLockDuration,
            syncStatusText = syncText,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setBiometricEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricEnabled(value) }
    }

    fun setAutoLockDuration(value: AutoLockDuration) {
        viewModelScope.launch { settingsRepository.setAutoLockDuration(value) }
    }
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onShowRecoveryCode: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val biometricAvailable = isStrongBiometricAvailable(context)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val promptController = rememberAuthPromptController(
        title = "再次验证身份",
        subtitle = "查看恢复码前请先确认你本人正在操作",
        onSuccess = onShowRecoveryCode,
        onFailure = {},
    )
    SettingsScreen(
        uiState = uiState,
        biometricAvailable = biometricAvailable,
        onBack = onBack,
        onBiometricEnabledChange = viewModel::setBiometricEnabled,
        onAutoLockDurationChange = viewModel::setAutoLockDuration,
        onShowRecoveryCode = promptController.authenticateDeviceCredential,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    uiState: SettingsUiState,
    biometricAvailable: Boolean,
    onBack: () -> Unit,
    onBiometricEnabledChange: (Boolean) -> Unit,
    onAutoLockDurationChange: (AutoLockDuration) -> Unit,
    onShowRecoveryCode: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(text = "生物识别解锁", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (biometricAvailable) "使用指纹或面容快速解锁" else "当前设备不支持强生物识别，只保留设备密码解锁",
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Switch(
                        checked = biometricAvailable && uiState.biometricEnabled,
                        onCheckedChange = onBiometricEnabledChange,
                        enabled = biometricAvailable,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            Card {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(text = "自动锁定时间", style = MaterialTheme.typography.titleMedium)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AutoLockDuration.entries.forEach { option ->
                            OutlinedButton(
                                onClick = { onAutoLockDurationChange(option) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = option.label(),
                                    modifier = Modifier.weight(1f),
                                )
                                if (option == uiState.autoLockDuration) {
                                    Icon(Icons.Rounded.Check, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "同步状态", style = MaterialTheme.typography.titleMedium)
                    Text(text = uiState.syncStatusText)
                }
            }

            OutlinedButton(
                onClick = onShowRecoveryCode,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("重新查看恢复码")
            }
        }
    }
}

private fun AutoLockDuration.label(): String = when (this) {
    AutoLockDuration.IMMEDIATELY -> "立即"
    AutoLockDuration.THIRTY_SECONDS -> "30 秒"
    AutoLockDuration.ONE_MINUTE -> "1 分钟"
    AutoLockDuration.FIVE_MINUTES -> "5 分钟"
}

data class RecoveryCodeUiState(
    val recoveryCode: String = "",
    val error: String? = null,
)

@HiltViewModel
class RecoveryCodeViewModel @Inject constructor(
    private val sessionManager: VaultSessionManager,
    private val vaultKeyManager: VaultKeyManager,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(RecoveryCodeUiState())
    val uiState: StateFlow<RecoveryCodeUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                vaultKeyManager.revealRecoveryCode(sessionManager.requireVaultKey())
            }.onSuccess { recoveryCode ->
                mutableUiState.update { it.copy(recoveryCode = recoveryCode) }
            }.onFailure { error ->
                mutableUiState.update { it.copy(error = error.message ?: "读取恢复码失败") }
            }
        }
    }
}

@Composable
fun RecoveryCodeRoute(
    onBack: () -> Unit,
    viewModel: RecoveryCodeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SecureWindowEffect(enabled = false)
    RecoveryCodeScreen(
        uiState = uiState,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryCodeScreen(
    uiState: RecoveryCodeUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("恢复码") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "请妥善保存这组恢复码。", style = MaterialTheme.typography.headlineSmall)
            Text(text = "它只用于当前设备重新绑定本地解锁能力，不会上传到任何服务端。")
            Card {
                Text(
                    text = uiState.recoveryCode.ifBlank { "正在读取..." },
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            if (uiState.error != null) {
                Text(text = uiState.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
