package io.github.c1921.wanpass.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
    Biometric,
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val pendingSetupVault: PendingSetupVault? = null,
    val biometricEnabled: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val vaultKeyManager: VaultKeyManager,
    private val settingsRepository: VaultSettingsRepository,
    private val sessionManager: VaultSessionManager,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = mutableUiState.asStateFlow()

    fun startSetup() {
        mutableUiState.update {
            it.copy(
                step = OnboardingStep.RecoveryCode,
                pendingSetupVault = vaultKeyManager.createPendingVault(),
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

    fun finishSetup(biometricAvailable: Boolean) {
        val pendingSetupVault = mutableUiState.value.pendingSetupVault ?: return
        viewModelScope.launch {
            mutableUiState.update { it.copy(saving = true, error = null) }
            runCatching {
                vaultKeyManager.persistPendingVault(pendingSetupVault)
                settingsRepository.setBiometricEnabled(biometricAvailable && mutableUiState.value.biometricEnabled)
                settingsRepository.setOnboardingComplete(true)
                sessionManager.importFreshVault(pendingSetupVault.vaultKey)
            }.onSuccess {
                pendingSetupVault.vaultKey.fill(0)
                mutableUiState.value = OnboardingUiState()
            }.onFailure { error ->
                mutableUiState.update { it.copy(saving = false, error = error.message ?: "初始化失败") }
            }
        }
    }
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
        OnboardingStep.Welcome -> WelcomeStep(onStart = viewModel::startSetup)
        OnboardingStep.RecoveryCode -> RecoveryCodeStep(
            recoveryCode = uiState.pendingSetupVault?.recoveryCode.orEmpty(),
            onContinue = viewModel::confirmRecoverySaved,
        )

        OnboardingStep.Biometric -> BiometricStep(
            biometricAvailable = biometricAvailable,
            biometricEnabled = uiState.biometricEnabled,
            saving = uiState.saving,
            error = uiState.error,
            onBiometricChanged = viewModel::setBiometricEnabled,
            onFinish = { viewModel.finishSetup(biometricAvailable) },
        )
    }
}

@Composable
private fun WelcomeStep(
    onStart: () -> Unit,
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
            text = "这是这台设备上重设解锁能力的唯一恢复凭证。请立即抄下或截图保存。",
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
private fun BiometricStep(
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    saving: Boolean,
    error: String?,
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
            Text(if (saving) "正在初始化..." else "进入保险箱")
        }
    }
}
