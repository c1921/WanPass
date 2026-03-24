package io.github.c1921.wanpass.ui.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.c1921.wanpass.domain.model.UnlockResult
import io.github.c1921.wanpass.session.VaultSessionManager
import io.github.c1921.wanpass.ui.AuthPromptController
import io.github.c1921.wanpass.ui.SecureWindowEffect
import io.github.c1921.wanpass.ui.isStrongBiometricAvailable
import io.github.c1921.wanpass.ui.rememberAuthPromptController
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UnlockUiState(
    val message: String? = null,
    val recoveryMode: Boolean = false,
    val recoveryCodeInput: String = "",
    val submitting: Boolean = false,
)

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val sessionManager: VaultSessionManager,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = mutableUiState.asStateFlow()

    fun unlock() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(submitting = true, message = null) }
            when (val result = sessionManager.unlock()) {
                UnlockResult.Success -> mutableUiState.value = UnlockUiState()
                UnlockResult.NeedsRecovery -> mutableUiState.update {
                    it.copy(submitting = false, recoveryMode = true, message = "本地解锁凭证失效，请使用恢复码重新绑定。")
                }

                is UnlockResult.Failure -> mutableUiState.update {
                    it.copy(submitting = false, message = result.reason)
                }
            }
        }
    }

    fun setRecoveryMode(value: Boolean) {
        mutableUiState.update { it.copy(recoveryMode = value, message = null) }
    }

    fun updateRecoveryCode(value: String) {
        mutableUiState.update { it.copy(recoveryCodeInput = value) }
    }

    fun submitRecoveryCode() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(submitting = true, message = null) }
            when (val result = sessionManager.recoverAndUnlock(mutableUiState.value.recoveryCodeInput)) {
                UnlockResult.Success -> mutableUiState.value = UnlockUiState()
                is UnlockResult.Failure -> mutableUiState.update {
                    it.copy(submitting = false, message = result.reason)
                }

                UnlockResult.NeedsRecovery -> mutableUiState.update {
                    it.copy(submitting = false, message = "恢复失败，请检查恢复码。")
                }
            }
        }
    }

    fun showPromptError(message: String) {
        mutableUiState.update { it.copy(message = message, submitting = false) }
    }
}

@Composable
fun UnlockRoute(
    biometricEnabled: Boolean,
    viewModel: UnlockViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricAvailable = biometricEnabled && isStrongBiometricAvailable(context)
    val authPromptController = rememberUnlockPromptController(viewModel::unlock, viewModel::showPromptError)
    SecureWindowEffect(enabled = false)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "解锁 WanPass", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "应用内容默认隐藏，解锁后才会载入本地搜索索引和记录明文。",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!uiState.recoveryMode) {
            if (biometricAvailable) {
                Button(
                    onClick = authPromptController.authenticateBiometric,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    enabled = !uiState.submitting,
                ) {
                    Text(if (uiState.submitting) "正在解锁..." else "使用指纹/面容解锁")
                }
            }
            OutlinedButton(
                onClick = authPromptController.authenticateDeviceCredential,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                enabled = !uiState.submitting,
            ) {
                Text("使用设备密码")
            }
            OutlinedButton(
                onClick = { viewModel.setRecoveryMode(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                enabled = !uiState.submitting,
            ) {
                Text("使用恢复码")
            }
        } else {
            OutlinedTextField(
                value = uiState.recoveryCodeInput,
                onValueChange = viewModel::updateRecoveryCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                label = { Text("恢复码") },
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                onClick = viewModel::submitRecoveryCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                enabled = !uiState.submitting && uiState.recoveryCodeInput.isNotBlank(),
            ) {
                Text(if (uiState.submitting) "正在恢复..." else "恢复并重新绑定")
            }
            OutlinedButton(
                onClick = { viewModel.setRecoveryMode(false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                enabled = !uiState.submitting,
            ) {
                Text("返回解锁页")
            }
        }
        if (uiState.message != null) {
            Text(
                text = uiState.message.orEmpty(),
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun rememberUnlockPromptController(
    onUnlockSuccess: () -> Unit,
    onPromptError: (String) -> Unit,
): AuthPromptController = rememberAuthPromptController(
    title = "解锁 WanPass",
    subtitle = "验证通过后载入本地保险箱",
    onSuccess = onUnlockSuccess,
    onFailure = onPromptError,
)
