package io.github.c1921.wanpass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.c1921.wanpass.data.repository.WebDavEnableResult
import io.github.c1921.wanpass.data.repository.WebDavSyncGateway
import io.github.c1921.wanpass.domain.model.AutoLockDuration
import io.github.c1921.wanpass.domain.model.RemoteBackupInfo
import io.github.c1921.wanpass.domain.model.VaultSettings
import io.github.c1921.wanpass.domain.model.WebDavConfigDraft
import io.github.c1921.wanpass.domain.model.WebDavSettings
import io.github.c1921.wanpass.domain.repository.SyncStatusProvider
import io.github.c1921.wanpass.domain.repository.VaultSettingsRepository
import io.github.c1921.wanpass.ui.formatTimestamp
import io.github.c1921.wanpass.ui.isStrongBiometricAvailable
import io.github.c1921.wanpass.ui.rememberAuthPromptController
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class WebDavEditorState(
    val baseUrl: String = "",
    val remoteRoot: String = "WanPass",
    val username: String = "",
    val password: String = "",
    val recoveryCode: String = "",
)

private data class PendingRemoteDecision(
    val remoteInfo: RemoteBackupInfo,
    val localHasData: Boolean,
)

private data class SettingsActionState(
    val busy: Boolean = false,
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false,
    val pendingRemoteDecision: PendingRemoteDecision? = null,
    val editorDirty: Boolean = false,
)

data class WebDavSettingsUiState(
    val biometricEnabled: Boolean = true,
    val autoLockDuration: AutoLockDuration = AutoLockDuration.THIRTY_SECONDS,
    val syncStatusText: String = "未启用 WebDAV 备份",
    val webDavEnabled: Boolean = false,
    val webDavHasPassword: Boolean = false,
    val webDavLastSyncAt: Long? = null,
    val webDavLastSyncError: String? = null,
    val webDavBaseUrl: String = "",
    val webDavRemoteRoot: String = "WanPass",
    val webDavUsername: String = "",
    val webDavPasswordInput: String = "",
    val webDavRecoveryCodeInput: String = "",
    val busy: Boolean = false,
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false,
    val pendingRemoteDecision: RemoteBackupInfo? = null,
    val pendingDecisionHasLocalData: Boolean = false,
)

@HiltViewModel
class WebDavSettingsViewModel @Inject constructor(
    private val settingsRepository: VaultSettingsRepository,
    syncStatusProvider: SyncStatusProvider,
    private val webDavSyncGateway: WebDavSyncGateway,
) : ViewModel() {
    private val appSettingsState = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VaultSettings(),
    )
    private val webDavSettingsState = settingsRepository.webDavSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WebDavSettings(),
    )
    private val syncStatusState = syncStatusProvider.statusTextFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "未启用 WebDAV 备份",
    )
    private val editorState = MutableStateFlow(WebDavEditorState())
    private val actionState = MutableStateFlow(SettingsActionState())
    private var editorInitialized = false

    val uiState: StateFlow<WebDavSettingsUiState> = combine(
        appSettingsState,
        webDavSettingsState,
        syncStatusState,
        editorState,
        actionState,
    ) { settings, webDavSettings, syncText, editor, action ->
        WebDavSettingsUiState(
            biometricEnabled = settings.biometricEnabled,
            autoLockDuration = settings.autoLockDuration,
            syncStatusText = syncText,
            webDavEnabled = webDavSettings.enabled,
            webDavHasPassword = webDavSettings.hasPassword,
            webDavLastSyncAt = webDavSettings.lastSyncAt,
            webDavLastSyncError = webDavSettings.lastSyncError,
            webDavBaseUrl = editor.baseUrl,
            webDavRemoteRoot = editor.remoteRoot,
            webDavUsername = editor.username,
            webDavPasswordInput = editor.password,
            webDavRecoveryCodeInput = editor.recoveryCode,
            busy = action.busy,
            feedbackMessage = action.feedbackMessage,
            feedbackIsError = action.feedbackIsError,
            pendingRemoteDecision = action.pendingRemoteDecision?.remoteInfo,
            pendingDecisionHasLocalData = action.pendingRemoteDecision?.localHasData == true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WebDavSettingsUiState(),
    )

    init {
        viewModelScope.launch {
            webDavSettingsState.collect { stored ->
                if (!editorInitialized || !actionState.value.editorDirty) {
                    editorInitialized = true
                    editorState.update {
                        it.copy(
                            baseUrl = stored.baseUrl,
                            remoteRoot = stored.remoteRoot,
                            username = stored.username,
                            password = "",
                        )
                    }
                    actionState.update { it.copy(editorDirty = false) }
                }
            }
        }
    }

    fun setBiometricEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricEnabled(value) }
    }

    fun setAutoLockDuration(value: AutoLockDuration) {
        viewModelScope.launch { settingsRepository.setAutoLockDuration(value) }
    }

    fun updateWebDavBaseUrl(value: String) = updateEditor { it.copy(baseUrl = value) }

    fun updateWebDavRemoteRoot(value: String) = updateEditor { it.copy(remoteRoot = value) }

    fun updateWebDavUsername(value: String) = updateEditor { it.copy(username = value) }

    fun updateWebDavPassword(value: String) = updateEditor { it.copy(password = value) }

    fun updateRecoveryCode(value: String) = updateEditor { it.copy(recoveryCode = value) }

    fun testWebDavConnection() {
        runAction("WebDAV 连接测试通过", "连接测试失败") {
            webDavSyncGateway.testConnection(currentDraft())
        }
    }

    fun setWebDavEnabled(value: Boolean) {
        viewModelScope.launch {
            actionState.update { it.copy(busy = true, feedbackMessage = null, feedbackIsError = false) }
            runCatching {
                if (value) {
                    webDavSyncGateway.configureAndEnable(currentDraft())
                } else {
                    webDavSyncGateway.disable()
                    WebDavEnableResult.Enabled
                }
            }.onSuccess { result ->
                when (result) {
                    WebDavEnableResult.Enabled -> {
                        actionState.update {
                            it.copy(
                                busy = false,
                                feedbackMessage = if (value) "WebDAV 备份已启用" else "WebDAV 备份已停用",
                                feedbackIsError = false,
                                pendingRemoteDecision = null,
                                editorDirty = false,
                            )
                        }
                    }

                    is WebDavEnableResult.RequiresRemoteDecision -> {
                        actionState.update {
                            it.copy(
                                busy = false,
                                feedbackMessage = if (result.localHasData) {
                                    "远端已有备份，请选择上传本地或从远端恢复"
                                } else {
                                    "远端已有备份，请输入恢复码恢复到本机"
                                },
                                feedbackIsError = false,
                                pendingRemoteDecision = PendingRemoteDecision(
                                    remoteInfo = result.remoteInfo,
                                    localHasData = result.localHasData,
                                ),
                                editorDirty = false,
                            )
                        }
                    }
                }
            }.onFailure { error ->
                actionState.update {
                    it.copy(
                        busy = false,
                        feedbackMessage = error.message ?: "WebDAV 配置失败",
                        feedbackIsError = true,
                    )
                }
            }
        }
    }

    fun syncNow() {
        runAction("已执行 WebDAV 同步", "同步失败") {
            webDavSyncGateway.syncNow()
        }
    }

    fun uploadLocalOverwriteRemote() {
        runAction("已上传本地数据并覆盖远端备份", "上传失败", clearPendingDecision = true) {
            webDavSyncGateway.uploadLocalOverwriteRemote()
        }
    }

    fun takeOverRemote() {
        runAction("当前设备已接管远端备份", "接管失败") {
            webDavSyncGateway.takeOverRemote()
        }
    }

    fun restoreFromRemote() {
        viewModelScope.launch {
            val recoveryCode = editorState.value.recoveryCode.trim()
            if (recoveryCode.isBlank()) {
                actionState.update {
                    it.copy(
                        feedbackMessage = "请输入恢复码后再执行远端恢复",
                        feedbackIsError = true,
                    )
                }
                return@launch
            }
            actionState.update { it.copy(busy = true, feedbackMessage = null, feedbackIsError = false) }
            runCatching {
                webDavSyncGateway.restoreFromRemote(
                    recoveryCode = recoveryCode,
                    draft = currentDraft(),
                )
            }.onSuccess {
                editorState.update { it.copy(password = "", recoveryCode = "") }
                actionState.update {
                    it.copy(
                        busy = false,
                        feedbackMessage = "已从 WebDAV 恢复到本机",
                        feedbackIsError = false,
                        pendingRemoteDecision = null,
                        editorDirty = false,
                    )
                }
            }.onFailure { error ->
                actionState.update {
                    it.copy(
                        busy = false,
                        feedbackMessage = error.message ?: "远端恢复失败",
                        feedbackIsError = true,
                    )
                }
            }
        }
    }

    private fun runAction(
        successMessage: String,
        failureFallback: String,
        clearPendingDecision: Boolean = false,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            actionState.update { it.copy(busy = true, feedbackMessage = null, feedbackIsError = false) }
            runCatching { block() }.onSuccess {
                actionState.update {
                    it.copy(
                        busy = false,
                        feedbackMessage = successMessage,
                        feedbackIsError = false,
                        pendingRemoteDecision = if (clearPendingDecision) null else it.pendingRemoteDecision,
                    )
                }
            }.onFailure { error ->
                actionState.update {
                    it.copy(
                        busy = false,
                        feedbackMessage = error.message ?: failureFallback,
                        feedbackIsError = true,
                    )
                }
            }
        }
    }

    private fun updateEditor(transform: (WebDavEditorState) -> WebDavEditorState) {
        editorState.update(transform)
        actionState.update { it.copy(editorDirty = true) }
    }

    private fun currentDraft(): WebDavConfigDraft = WebDavConfigDraft(
        baseUrl = editorState.value.baseUrl,
        remoteRoot = editorState.value.remoteRoot,
        username = editorState.value.username,
        password = editorState.value.password,
        preserveStoredPassword = editorState.value.password.isBlank() && webDavSettingsState.value.hasPassword,
    )
}

@Composable
fun WebDavSettingsRoute(
    onBack: () -> Unit,
    onShowRecoveryCode: () -> Unit,
    viewModel: WebDavSettingsViewModel = hiltViewModel(),
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
    WebDavSettingsScreen(
        uiState = uiState,
        biometricAvailable = biometricAvailable,
        onBack = onBack,
        onBiometricEnabledChange = viewModel::setBiometricEnabled,
        onAutoLockDurationChange = viewModel::setAutoLockDuration,
        onWebDavEnabledChange = viewModel::setWebDavEnabled,
        onWebDavBaseUrlChange = viewModel::updateWebDavBaseUrl,
        onWebDavRemoteRootChange = viewModel::updateWebDavRemoteRoot,
        onWebDavUsernameChange = viewModel::updateWebDavUsername,
        onWebDavPasswordChange = viewModel::updateWebDavPassword,
        onRecoveryCodeChange = viewModel::updateRecoveryCode,
        onTestConnection = viewModel::testWebDavConnection,
        onSyncNow = viewModel::syncNow,
        onUploadLocalOverwriteRemote = viewModel::uploadLocalOverwriteRemote,
        onTakeOverRemote = viewModel::takeOverRemote,
        onRestoreFromRemote = viewModel::restoreFromRemote,
        onShowRecoveryCode = promptController.authenticateDeviceCredential,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavSettingsScreen(
    uiState: WebDavSettingsUiState,
    biometricAvailable: Boolean,
    onBack: () -> Unit,
    onBiometricEnabledChange: (Boolean) -> Unit,
    onAutoLockDurationChange: (AutoLockDuration) -> Unit,
    onWebDavEnabledChange: (Boolean) -> Unit,
    onWebDavBaseUrlChange: (String) -> Unit,
    onWebDavRemoteRootChange: (String) -> Unit,
    onWebDavUsernameChange: (String) -> Unit,
    onWebDavPasswordChange: (String) -> Unit,
    onRecoveryCodeChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSyncNow: () -> Unit,
    onUploadLocalOverwriteRemote: () -> Unit,
    onTakeOverRemote: () -> Unit,
    onRestoreFromRemote: () -> Unit,
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
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text = "WebDAV 备份", style = MaterialTheme.typography.titleMedium)
                    Text(text = uiState.syncStatusText)
                    if (uiState.webDavLastSyncAt != null) {
                        Text(text = "最近同步：${formatTimestamp(uiState.webDavLastSyncAt)}")
                    }
                    if (!uiState.webDavLastSyncError.isNullOrBlank()) {
                        Text(
                            text = "最近异常：${uiState.webDavLastSyncError}",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Switch(
                        checked = uiState.webDavEnabled,
                        onCheckedChange = onWebDavEnabledChange,
                        enabled = !uiState.busy,
                    )
                    OutlinedTextField(
                        value = uiState.webDavBaseUrl,
                        onValueChange = onWebDavBaseUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("服务器地址") },
                        singleLine = true,
                        enabled = !uiState.busy,
                    )
                    OutlinedTextField(
                        value = uiState.webDavRemoteRoot,
                        onValueChange = onWebDavRemoteRootChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("远端目录") },
                        singleLine = true,
                        enabled = !uiState.busy,
                    )
                    OutlinedTextField(
                        value = uiState.webDavUsername,
                        onValueChange = onWebDavUsernameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("用户名") },
                        singleLine = true,
                        enabled = !uiState.busy,
                    )
                    OutlinedTextField(
                        value = uiState.webDavPasswordInput,
                        onValueChange = onWebDavPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                if (uiState.webDavHasPassword) {
                                    "密码（留空则保持已保存）"
                                } else {
                                    "密码"
                                }
                            )
                        },
                        singleLine = true,
                        enabled = !uiState.busy,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        value = uiState.webDavRecoveryCodeInput,
                        onValueChange = onRecoveryCodeChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("恢复码（用于从远端恢复）") },
                        singleLine = true,
                        enabled = !uiState.busy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    OutlinedButton(
                        onClick = onTestConnection,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.busy,
                    ) {
                        Text("测试连接")
                    }
                    if (uiState.webDavEnabled) {
                        OutlinedButton(
                            onClick = onSyncNow,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.busy,
                        ) {
                            Text("立即同步")
                        }
                    }
                    if (uiState.pendingRemoteDecision != null) {
                        Text(
                            text = "远端共有 ${uiState.pendingRemoteDecision.itemCount} 条记录，最近备份时间：${
                                uiState.pendingRemoteDecision.lastBackupAt?.let(::formatTimestamp) ?: "未知"
                            }",
                        )
                        if (uiState.pendingDecisionHasLocalData) {
                            OutlinedButton(
                                onClick = onUploadLocalOverwriteRemote,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.busy,
                            ) {
                                Text("上传本地覆盖远端")
                            }
                        }
                        OutlinedButton(
                            onClick = onRestoreFromRemote,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.busy,
                        ) {
                            Text("从远端恢复到本机")
                        }
                    }
                    if (uiState.webDavEnabled) {
                        OutlinedButton(
                            onClick = onTakeOverRemote,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.busy,
                        ) {
                            Text("接管当前备份")
                        }
                    }
                    if (uiState.feedbackMessage != null) {
                        Text(
                            text = uiState.feedbackMessage,
                            color = if (uiState.feedbackIsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
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
