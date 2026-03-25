package io.github.c1921.wanpass.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.c1921.wanpass.data.repository.WebDavSyncGateway
import io.github.c1921.wanpass.domain.model.WebDavConfigDraft
import io.github.c1921.wanpass.ui.SecureWindowEffect
import io.github.c1921.wanpass.ui.isStrongBiometricAvailable
import androidx.lifecycle.ViewModel
import io.github.c1921.wanpass.domain.model.PendingSetupVault
import io.github.c1921.wanpass.domain.repository.VaultSettingsRepository
import io.github.c1921.wanpass.security.VaultKeyManager
import io.github.c1921.wanpass.session.VaultSessionManager
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep {
    Welcome,
    RecoveryCode,
    WebDavRestore,
    Biometric,
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val pendingSetupVault: PendingSetupVault? = null,
    val biometricEnabled: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val restoreMode: Boolean = false,
    val webDavBaseUrl: String = "",
    val webDavRemoteRoot: String = "WanPass",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val recoveryCodeInput: String = "",
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val vaultKeyManager: VaultKeyManager,
    private val settingsRepository: VaultSettingsRepository,
    private val sessionManager: VaultSessionManager,
    private val webDavSyncGateway: WebDavSyncGateway,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = mutableUiState.asStateFlow()

    fun startSetup() {
        mutableUiState.update {
            it.copy(
                step = OnboardingStep.RecoveryCode,
                pendingSetupVault = vaultKeyManager.createPendingVault(),
                restoreMode = false,
                error = null,
            )
        }
    }

    fun startWebDavRestore() {
        mutableUiState.update {
            it.copy(
                step = OnboardingStep.WebDavRestore,
                pendingSetupVault = null,
                restoreMode = false,
                saving = false,
                error = null,
            )
        }
    }

    fun confirmRecoverySaved() {
        mutableUiState.update { it.copy(step = OnboardingStep.Biometric) }
    }

    fun setBiometricEnabled(value: Boolean) {
        mutableUiState.update { it.copy(biometricEnabled = value) }
    }

    fun updateWebDavBaseUrl(value: String) {
        mutableUiState.update { it.copy(webDavBaseUrl = value) }
    }

    fun updateWebDavRemoteRoot(value: String) {
        mutableUiState.update { it.copy(webDavRemoteRoot = value) }
    }

    fun updateWebDavUsername(value: String) {
        mutableUiState.update { it.copy(webDavUsername = value) }
    }

    fun updateWebDavPassword(value: String) {
        mutableUiState.update { it.copy(webDavPassword = value) }
    }

    fun updateRecoveryCodeInput(value: String) {
        mutableUiState.update { it.copy(recoveryCodeInput = value) }
    }

    fun testWebDavConnection() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(saving = true, error = null) }
            runCatching {
                webDavSyncGateway.testConnection(currentDraft())
            }.onSuccess {
                mutableUiState.update { it.copy(saving = false, error = "WebDAV 连接测试通过") }
            }.onFailure { error ->
                mutableUiState.update { it.copy(saving = false, error = error.message ?: "连接测试失败") }
            }
        }
    }

    fun restoreFromWebDav() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(saving = true, error = null) }
            runCatching {
                webDavSyncGateway.restoreFromRemote(
                    recoveryCode = mutableUiState.value.recoveryCodeInput.trim(),
                    draft = currentDraft(),
                )
            }.onSuccess {
                mutableUiState.update {
                    it.copy(
                        step = OnboardingStep.Biometric,
                        restoreMode = true,
                        saving = false,
                        webDavPassword = "",
                        recoveryCodeInput = "",
                        error = null,
                    )
                }
            }.onFailure { error ->
                mutableUiState.update { it.copy(saving = false, error = error.message ?: "远端恢复失败") }
            }
        }
    }

    fun finishSetup(biometricAvailable: Boolean) {
        val uiState = mutableUiState.value
        viewModelScope.launch {
            mutableUiState.update { it.copy(saving = true, error = null) }
            runCatching {
                if (uiState.restoreMode) {
                    settingsRepository.setBiometricEnabled(biometricAvailable && uiState.biometricEnabled)
                    settingsRepository.setOnboardingComplete(true)
                } else {
                    val pendingSetupVault = uiState.pendingSetupVault ?: error("初始化状态丢失")
                    vaultKeyManager.persistPendingVault(pendingSetupVault)
                    settingsRepository.setBiometricEnabled(biometricAvailable && uiState.biometricEnabled)
                    settingsRepository.setOnboardingComplete(true)
                    sessionManager.importFreshVault(pendingSetupVault.vaultKey)
                    pendingSetupVault.vaultKey.fill(0)
                }
            }.onSuccess {
                mutableUiState.value = OnboardingUiState()
            }.onFailure { error ->
                mutableUiState.update { it.copy(saving = false, error = error.message ?: "初始化失败") }
            }
        }
    }

    private fun currentDraft(): WebDavConfigDraft = WebDavConfigDraft(
        baseUrl = mutableUiState.value.webDavBaseUrl,
        remoteRoot = mutableUiState.value.webDavRemoteRoot,
        username = mutableUiState.value.webDavUsername,
        password = mutableUiState.value.webDavPassword,
        preserveStoredPassword = false,
    )
}

@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val biometricAvailable = isStrongBiometricAvailable(LocalContext.current)
    val secureEnabled = uiState.step != OnboardingStep.RecoveryCode
    SecureWindowEffect(enabled = secureEnabled)
    when (uiState.step) {
        OnboardingStep.Welcome -> WelcomeStep(
            onStart = viewModel::startSetup,
            onRestoreFromWebDav = viewModel::startWebDavRestore,
        )
        OnboardingStep.RecoveryCode -> RecoveryCodeStep(
            recoveryCode = uiState.pendingSetupVault?.recoveryCode.orEmpty(),
            onContinue = viewModel::confirmRecoverySaved,
        )

        OnboardingStep.WebDavRestore -> WebDavRestoreStep(
            uiState = uiState,
            onBaseUrlChange = viewModel::updateWebDavBaseUrl,
            onRemoteRootChange = viewModel::updateWebDavRemoteRoot,
            onUsernameChange = viewModel::updateWebDavUsername,
            onPasswordChange = viewModel::updateWebDavPassword,
            onRecoveryCodeChange = viewModel::updateRecoveryCodeInput,
            onTestConnection = viewModel::testWebDavConnection,
            onRestore = viewModel::restoreFromWebDav,
            onBack = viewModel::startSetup,
        )

        OnboardingStep.Biometric -> BiometricStep(
            biometricAvailable = biometricAvailable,
            biometricEnabled = uiState.biometricEnabled,
            saving = uiState.saving,
            error = uiState.error,
            restoreMode = uiState.restoreMode,
            onBiometricChanged = viewModel::setBiometricEnabled,
            onFinish = { viewModel.finishSetup(biometricAvailable) },
        )
    }
}

@Composable
private fun WelcomeStep(
    onStart: () -> Unit,
    onRestoreFromWebDav: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "WanPass", style = MaterialTheme.typography.displaySmall)
        Text(
            text = "只做储存、查看、搜索和安全。所有核心功能默认离线可用。",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "首次创建后会生成恢复码，请抄下或截图保存。",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onStart,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text("开始创建本地保险箱")
        }
        OutlinedButton(
            onClick = onRestoreFromWebDav,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("从 WebDAV 备份恢复")
        }
    }
}

@Composable
private fun RecoveryCodeStep(
    recoveryCode: String,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(text = "保存恢复码", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "这是恢复当前保险箱的唯一恢复凭证。请立即抄下或截图保存，换机时可配合 WebDAV 备份恢复。",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Card(modifier = Modifier.padding(top = 24.dp)) {
            Text(
                text = recoveryCode,
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(
            text = "说明：卸载应用或清除数据后，本地记录将无法通过恢复码找回。",
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onContinue,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text("我已保存恢复码")
        }
    }
}

@Composable
private fun WebDavRestoreStep(
    uiState: OnboardingUiState,
    onBaseUrlChange: (String) -> Unit,
    onRemoteRootChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRecoveryCodeChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "从 WebDAV 恢复", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "输入 WebDAV 地址、账号密码和恢复码，将远端备份恢复到这台设备。",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = uiState.webDavBaseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("服务器地址") },
            singleLine = true,
            enabled = !uiState.saving,
        )
        OutlinedTextField(
            value = uiState.webDavRemoteRoot,
            onValueChange = onRemoteRootChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("远端目录") },
            singleLine = true,
            enabled = !uiState.saving,
        )
        OutlinedTextField(
            value = uiState.webDavUsername,
            onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("用户名") },
            singleLine = true,
            enabled = !uiState.saving,
        )
        OutlinedTextField(
            value = uiState.webDavPassword,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            singleLine = true,
            enabled = !uiState.saving,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = uiState.recoveryCodeInput,
            onValueChange = onRecoveryCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("恢复码") },
            singleLine = true,
            enabled = !uiState.saving,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (uiState.error != null) {
            Text(
                text = uiState.error,
                color = if (uiState.error.contains("通过")) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        OutlinedButton(
            onClick = onTestConnection,
            enabled = !uiState.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("测试连接")
        }
        Button(
            onClick = onRestore,
            enabled = !uiState.saving && uiState.recoveryCodeInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (uiState.saving) "正在恢复..." else "恢复到本机")
        }
        OutlinedButton(
            onClick = onBack,
            enabled = !uiState.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("返回并新建本地保险箱")
        }
    }
}

@Composable
private fun BiometricStep(
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    saving: Boolean,
    error: String?,
    restoreMode: Boolean,
    onBiometricChanged: (Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(text = "设置解锁方式", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "应用默认支持设备密码解锁。你也可以打开生物识别，让下次进入更快。",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            Card(modifier = Modifier.padding(top = 24.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "启用生物识别解锁", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (biometricAvailable) "使用指纹或面容快速解锁" else "当前设备不支持强生物识别，将仅使用设备密码",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = biometricAvailable && biometricEnabled,
                        onCheckedChange = onBiometricChanged,
                        enabled = biometricAvailable,
                    )
                }
            }
            if (error != null) {
                Text(
                    text = error,
                    modifier = Modifier.padding(top = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        OutlinedButton(
            onClick = onFinish,
            enabled = !saving,
        ) {
            Text(
                if (saving) {
                    if (restoreMode) "正在完成恢复..." else "正在初始化..."
                } else {
                    if (restoreMode) "完成恢复并进入保险箱" else "进入保险箱"
                }
            )
        }
    }
}
