package io.github.c1921.wanpass.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.c1921.wanpass.security.VaultKeyManager
import io.github.c1921.wanpass.session.VaultSessionManager
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onShowRecoveryCode: () -> Unit,
) {
    WebDavSettingsRoute(
        onBack = onBack,
        onShowRecoveryCode = onShowRecoveryCode,
    )
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
            Text(text = "它可用于在新设备上从 WebDAV 备份恢复保险箱，并重新绑定当前设备的本地解锁能力。")
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
