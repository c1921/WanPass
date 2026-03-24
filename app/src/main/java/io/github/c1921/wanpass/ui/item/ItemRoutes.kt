package io.github.c1921.wanpass.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.c1921.wanpass.domain.model.LoginContent
import io.github.c1921.wanpass.domain.model.NoteContent
import io.github.c1921.wanpass.domain.model.VaultItem
import io.github.c1921.wanpass.domain.model.VaultItemType
import io.github.c1921.wanpass.domain.repository.VaultRepository
import io.github.c1921.wanpass.domain.repository.VaultSettingsRepository
import io.github.c1921.wanpass.ui.formatTimestamp
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val itemId: String? = null,
    val type: VaultItemType = VaultItemType.LOGIN,
    val title: String = "",
    val account: String = "",
    val password: String = "",
    val site: String = "",
    val body: String = "",
    val note: String = "",
    val noteExpanded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val savedItemId: String? = null,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
) : ViewModel() {
    private val itemId: String? = savedStateHandle["itemId"]
    private val typeArg: String? = savedStateHandle["type"]

    private val mutableUiState = MutableStateFlow(
        EditorUiState(
            itemId = itemId,
            type = typeArg?.let(VaultItemType::fromStorage) ?: VaultItemType.LOGIN,
        )
    )
    val uiState: StateFlow<EditorUiState> = mutableUiState.asStateFlow()

    private var initializedFromExisting = false

    init {
        if (itemId != null) {
            viewModelScope.launch {
                vaultRepository.observeItem(itemId).collect { item ->
                    if (item != null && !initializedFromExisting) {
                        initializedFromExisting = true
                        mutableUiState.value = when (item) {
                            is VaultItem.Login -> EditorUiState(
                                itemId = item.id,
                                type = VaultItemType.LOGIN,
                                title = item.title,
                                account = item.account,
                                password = item.password,
                                site = item.site,
                                note = item.note,
                                noteExpanded = item.note.isNotBlank(),
                            )

                            is VaultItem.Note -> EditorUiState(
                                itemId = item.id,
                                type = VaultItemType.NOTE,
                                title = item.title,
                                body = item.body,
                                note = item.note,
                                noteExpanded = item.note.isNotBlank(),
                            )
                        }
                    }
                }
            }
        }
    }

    fun updateTitle(value: String) = mutableUiState.update { it.copy(title = value, error = null) }
    fun updateAccount(value: String) = mutableUiState.update { it.copy(account = value, error = null) }
    fun updatePassword(value: String) = mutableUiState.update { it.copy(password = value, error = null) }
    fun updateSite(value: String) = mutableUiState.update { it.copy(site = value, error = null) }
    fun updateBody(value: String) = mutableUiState.update { it.copy(body = value, error = null) }
    fun updateNote(value: String) = mutableUiState.update { it.copy(note = value, error = null) }
    fun setNoteExpanded(value: Boolean) = mutableUiState.update { it.copy(noteExpanded = value) }

    fun save() {
        val uiState = mutableUiState.value
        if (uiState.title.isBlank()) {
            mutableUiState.update { it.copy(error = "标题不能为空") }
            return
        }
        if (uiState.type == VaultItemType.NOTE && uiState.body.isBlank()) {
            mutableUiState.update { it.copy(error = "正文不能为空") }
            return
        }
        viewModelScope.launch {
            mutableUiState.update { it.copy(loading = true, error = null) }
            runCatching {
                when {
                    uiState.itemId == null && uiState.type == VaultItemType.LOGIN ->
                        vaultRepository.createLogin(
                            LoginContent(
                                title = uiState.title.trim(),
                                account = uiState.account.trim(),
                                password = uiState.password,
                                site = uiState.site.trim(),
                                note = uiState.note.trim(),
                            )
                        )

                    uiState.itemId == null && uiState.type == VaultItemType.NOTE ->
                        vaultRepository.createNote(
                            NoteContent(
                                title = uiState.title.trim(),
                                body = uiState.body.trim(),
                                note = uiState.note.trim(),
                            )
                        )

                    uiState.type == VaultItemType.LOGIN ->
                        vaultRepository.updateLogin(
                            itemId = uiState.itemId.orEmpty(),
                            content = LoginContent(
                                title = uiState.title.trim(),
                                account = uiState.account.trim(),
                                password = uiState.password,
                                site = uiState.site.trim(),
                                note = uiState.note.trim(),
                            )
                        )

                    else -> vaultRepository.updateNote(
                        itemId = uiState.itemId.orEmpty(),
                        content = NoteContent(
                            title = uiState.title.trim(),
                            body = uiState.body.trim(),
                            note = uiState.note.trim(),
                        )
                    )
                }
            }.onSuccess { savedItemId ->
                mutableUiState.update { it.copy(loading = false, savedItemId = savedItemId) }
            }.onFailure { error ->
                mutableUiState.update { it.copy(loading = false, error = error.message ?: "保存失败") }
            }
        }
    }

    fun consumeSavedItemId() {
        mutableUiState.update { it.copy(savedItemId = null) }
    }
}

@Composable
fun EditorRoute(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.savedItemId) {
        uiState.savedItemId?.let {
            onSaved(it)
            viewModel.consumeSavedItemId()
        }
    }
    EditorScreen(
        uiState = uiState,
        onBack = onBack,
        onSave = viewModel::save,
        onTitleChange = viewModel::updateTitle,
        onAccountChange = viewModel::updateAccount,
        onPasswordChange = viewModel::updatePassword,
        onSiteChange = viewModel::updateSite,
        onBodyChange = viewModel::updateBody,
        onNoteChange = viewModel::updateNote,
        onNoteExpandedChange = viewModel::setNoteExpanded,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    uiState: EditorUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onTitleChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSiteChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onNoteExpandedChange: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (uiState.itemId == null) "新增记录" else "编辑记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !uiState.loading) {
                        Text(if (uiState.loading) "保存中..." else "保存")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标题") },
            )
            if (uiState.type == VaultItemType.LOGIN) {
                OutlinedTextField(
                    value = uiState.account,
                    onValueChange = onAccountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("账号") },
                )
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    value = uiState.site,
                    onValueChange = onSiteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("网站 / App 名称") },
                )
            } else {
                OutlinedTextField(
                    value = uiState.body,
                    onValueChange = onBodyChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    label = { Text("内容") },
                    minLines = 6,
                )
            }
            TextButton(onClick = { onNoteExpandedChange(!uiState.noteExpanded) }) {
                Text(if (uiState.noteExpanded) "收起备注" else "展开备注")
            }
            if (uiState.noteExpanded) {
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    minLines = 3,
                )
            }
            if (uiState.error != null) {
                Text(text = uiState.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

data class DetailUiState(
    val item: VaultItem? = null,
    val passwordVisible: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val settingsRepository: VaultSettingsRepository,
) : ViewModel() {
    private val itemId: String = savedStateHandle["itemId"] ?: error("Missing itemId")
    private val mutableUiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = mutableUiState.asStateFlow()
    private var recordRecentDone = false
    private var hidePasswordJob: Job? = null

    init {
        viewModelScope.launch {
            vaultRepository.observeItem(itemId).collect { item ->
                mutableUiState.update { it.copy(item = item, passwordVisible = if (item is VaultItem.Login) it.passwordVisible else false) }
                if (item != null && !recordRecentDone) {
                    recordRecentDone = true
                    settingsRepository.recordRecentItem(item.id)
                }
            }
        }
    }

    fun togglePasswordVisibility() {
        if (mutableUiState.value.item !is VaultItem.Login) return
        if (mutableUiState.value.passwordVisible) {
            hidePassword()
        } else {
            mutableUiState.update { it.copy(passwordVisible = true) }
            hidePasswordJob?.cancel()
            hidePasswordJob = viewModelScope.launch {
                kotlinx.coroutines.delay(8_000)
                hidePassword()
            }
        }
    }

    fun hidePassword() {
        hidePasswordJob?.cancel()
        mutableUiState.update { it.copy(passwordVisible = false) }
    }

    fun delete() {
        viewModelScope.launch {
            mutableUiState.update { it.copy(deleting = true, error = null) }
            runCatching { vaultRepository.delete(itemId) }
                .onSuccess { mutableUiState.update { it.copy(deleting = false, deleted = true) } }
                .onFailure { error ->
                    mutableUiState.update { it.copy(deleting = false, error = error.message ?: "删除失败") }
                }
        }
    }
}

@Composable
fun DetailRoute(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onDeleted()
    }
    DetailScreen(
        uiState = uiState,
        onBack = onBack,
        onEdit = { uiState.item?.id?.let(onEdit) },
        onDelete = viewModel::delete,
        onTogglePassword = viewModel::togglePasswordVisibility,
        onHidePassword = viewModel::hidePassword,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    uiState: DetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePassword: () -> Unit,
    onHidePassword: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var confirmDelete by remember { mutableStateOf(false) }
    val item = uiState.item

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条记录？") },
            text = { Text("删除后会从首页和搜索结果中隐藏。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        onHidePassword()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("记录详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (item != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除")
                        }
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (item) {
                null -> Text(text = uiState.error ?: "记录不存在")
                is VaultItem.Login -> {
                    Text(text = item.title, style = MaterialTheme.typography.headlineMedium)
                    Text(text = "最近更新时间：${formatTimestamp(item.updatedAt)}")
                    DetailRow(
                        label = "账号",
                        value = item.account.ifBlank { "未填写" },
                        onCopy = { clipboardManager.setText(AnnotatedString(item.account)) },
                    )
                    DetailRow(
                        label = "密码",
                        value = if (uiState.passwordVisible) item.password else "••••••••",
                        trailingContent = {
                            IconButton(onClick = onTogglePassword) {
                                Icon(
                                    imageVector = if (uiState.passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "显示密码",
                                )
                            }
                        },
                    )
                    DetailRow(label = "网站 / App", value = item.site.ifBlank { "未填写" })
                    if (item.note.isNotBlank()) {
                        DetailRow(label = "备注", value = item.note)
                    }
                }

                is VaultItem.Note -> {
                    Text(text = item.title, style = MaterialTheme.typography.headlineMedium)
                    Text(text = "最近更新时间：${formatTimestamp(item.updatedAt)}")
                    DetailRow(label = "内容", value = item.body)
                    if (item.note.isNotBlank()) {
                        DetailRow(label = "备注", value = item.note)
                    }
                }
            }
            if (uiState.error != null) {
                Text(text = uiState.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = value, modifier = Modifier.weight(1f))
            Row {
                if (onCopy != null) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "复制")
                    }
                }
                trailingContent?.invoke()
            }
        }
    }
}
