package io.github.c1921.wanpass.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Note
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.c1921.wanpass.domain.model.VaultItemSummary
import io.github.c1921.wanpass.domain.model.VaultItemType
import io.github.c1921.wanpass.data.repository.WebDavSyncGateway
import io.github.c1921.wanpass.domain.repository.SyncStatusProvider
import io.github.c1921.wanpass.domain.repository.VaultRepository
import io.github.c1921.wanpass.domain.repository.VaultSettingsRepository
import io.github.c1921.wanpass.session.SearchIndex
import io.github.c1921.wanpass.ui.SecureWindowEffect
import io.github.c1921.wanpass.ui.formatTimestamp
import io.github.c1921.wanpass.ui.onboarding.OnboardingEntryMode
import io.github.c1921.wanpass.ui.onboarding.OnboardingRoute
import io.github.c1921.wanpass.ui.item.DetailRoute
import io.github.c1921.wanpass.ui.item.EditorRoute
import io.github.c1921.wanpass.ui.navigation.MainRoutes
import io.github.c1921.wanpass.ui.settings.RecoveryCodeRoute
import io.github.c1921.wanpass.ui.settings.SettingsRoute
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class HomeUiState(
    val query: String = "",
    val recentItems: List<VaultItemSummary> = emptyList(),
    val visibleItems: List<VaultItemSummary> = emptyList(),
    val emptyState: HomeEmptyState = HomeEmptyState.EmptyVault,
)

internal fun homeListItemKey(section: String, itemId: String): String = "$section:$itemId"

enum class HomeEmptyState {
    None,
    EmptyVault,
    NoSearchResults,
}

internal fun resolveHomeEmptyState(
    totalItemCount: Int,
    visibleItemCount: Int,
): HomeEmptyState = when {
    visibleItemCount > 0 -> HomeEmptyState.None
    totalItemCount == 0 -> HomeEmptyState.EmptyVault
    else -> HomeEmptyState.NoSearchResults
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    vaultRepository: VaultRepository,
    vaultSettingsRepository: VaultSettingsRepository,
    searchIndex: SearchIndex,
    syncStatusProvider: SyncStatusProvider,
    webDavSyncGateway: WebDavSyncGateway,
) : ViewModel() {
    private val queryFlow = MutableStateFlow("")

    init {
        webDavSyncGateway.requestSync()
    }

    val uiState: StateFlow<HomeUiState> = combine(
        vaultRepository.observeSummaries(),
        vaultSettingsRepository.recentViewedIdsFlow,
        queryFlow,
        searchIndex.entries,
        syncStatusProvider.statusTextFlow,
    ) { summaries, recentIds, query, _, _ ->
        val matchedIds = searchIndex.matches(query)
        val filtered = if (query.isBlank()) summaries else summaries.filter { it.id in matchedIds }
        val recent = recentIds.mapNotNull { id -> summaries.firstOrNull { it.id == id } }
        HomeUiState(
            query = query,
            recentItems = recent,
            visibleItems = filtered,
            emptyState = resolveHomeEmptyState(
                totalItemCount = summaries.size,
                visibleItemCount = filtered.size,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun updateQuery(value: String) {
        queryFlow.update { value }
    }
}

@Composable
fun WanPassMainGraph() {
    val navController = rememberNavController()
    SecureWindowEffect(enabled = true)

    NavHost(
        navController = navController,
        startDestination = MainRoutes.Home,
    ) {
        composable(MainRoutes.Home) {
            HomeRoute(
                onOpenSettings = { navController.navigate(MainRoutes.Settings) },
                onAddNew = { navController.navigate(MainRoutes.CreateType) },
                onOpenItem = { navController.navigate(MainRoutes.detail(it)) },
                onRestoreFromBackup = { navController.navigate(MainRoutes.RestoreOnboarding) },
            )
        }
        composable(MainRoutes.CreateType) {
            CreateTypeRoute(
                onTypeSelected = { navController.navigate(MainRoutes.newItem(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = MainRoutes.NewItem,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) {
            EditorRoute(
                onBack = { navController.popBackStack() },
                onSaved = { itemId ->
                    navController.navigate(MainRoutes.detail(itemId)) {
                        popUpTo(MainRoutes.Home)
                    }
                },
            )
        }
        composable(
            route = MainRoutes.EditItem,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) {
            EditorRoute(
                onBack = { navController.popBackStack() },
                onSaved = { itemId ->
                    navController.navigate(MainRoutes.detail(itemId)) {
                        popUpTo(MainRoutes.Home)
                    }
                },
            )
        }
        composable(
            route = MainRoutes.Detail,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) {
            DetailRoute(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(MainRoutes.editItem(it)) },
                onDeleted = { navController.popBackStack(MainRoutes.Home, false) },
            )
        }
        composable(MainRoutes.Settings) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onShowRecoveryCode = { navController.navigate(MainRoutes.RecoveryCode) },
            )
        }
        composable(MainRoutes.RecoveryCode) {
            RecoveryCodeRoute(onBack = { navController.popBackStack() })
        }
        composable(MainRoutes.RestoreOnboarding) {
            OnboardingRoute(
                entryMode = OnboardingEntryMode.RestoreReentry,
                onExit = { navController.popBackStack() },
                onCompleted = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun HomeRoute(
    onOpenSettings: () -> Unit,
    onAddNew: () -> Unit,
    onOpenItem: (String) -> Unit,
    onRestoreFromBackup: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onQueryChange = viewModel::updateQuery,
        onOpenSettings = onOpenSettings,
        onAddNew = onAddNew,
        onOpenItem = onOpenItem,
        onRestoreFromBackup = onRestoreFromBackup,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onAddNew: () -> Unit,
    onOpenItem: (String) -> Unit,
    onRestoreFromBackup: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("WanPass") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNew) {
                Icon(Icons.Rounded.Add, contentDescription = "新增")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("搜索标题、账号、网站或正文") },
                    singleLine = true,
                )
            }
            if (uiState.recentItems.isNotEmpty()) {
                item {
                    Text(text = "最近查看", style = MaterialTheme.typography.titleMedium)
                }
                items(uiState.recentItems, key = { homeListItemKey("recent", it.id) }) { item ->
                    VaultSummaryCard(item = item, onClick = { onOpenItem(item.id) })
                }
            }
            item {
                Text(text = "全部记录", style = MaterialTheme.typography.titleMedium)
            }
            if (uiState.emptyState != HomeEmptyState.None) {
                item {
                    when (uiState.emptyState) {
                        HomeEmptyState.EmptyVault -> EmptyVaultCard(onRestoreFromBackup = onRestoreFromBackup)
                        HomeEmptyState.NoSearchResults -> NoSearchResultsCard()
                        HomeEmptyState.None -> Unit
                    }
                }
            } else {
                items(uiState.visibleItems, key = { homeListItemKey("all", it.id) }) { item ->
                    VaultSummaryCard(item = item, onClick = { onOpenItem(item.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyVaultCard(
    onRestoreFromBackup: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "还没有记录", style = MaterialTheme.typography.titleMedium)
            Text(text = "点击右下角按钮添加第一条登录信息，或从已有 WebDAV 备份恢复。")
            OutlinedButton(
                onClick = onRestoreFromBackup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("从 WebDAV 备份恢复")
            }
        }
    }
}

@Composable
private fun NoSearchResultsCard() {
    Card {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = "没有匹配记录", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "换个关键词再试试，恢复入口只会在仓库为空时出现。",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun VaultSummaryCard(
    item: VaultItemSummary,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = item.title, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = if (item.type == VaultItemType.LOGIN) Icons.Rounded.Password else Icons.Rounded.Note,
                        contentDescription = null,
                    )
                    Text(text = if (item.type == VaultItemType.LOGIN) "登录信息" else "私密笔记")
                }
                Text(text = formatTimestamp(item.updatedAt))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTypeRoute(
    onTypeSelected: (VaultItemType) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("新增记录") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TypeCard(
                title = "登录信息",
                description = "储存标题、账号、密码、网站和备注",
                onClick = { onTypeSelected(VaultItemType.LOGIN) },
            )
            TypeCard(
                title = "私密笔记",
                description = "储存正文和简短备注",
                onClick = { onTypeSelected(VaultItemType.NOTE) },
            )
        }
    }
}

@Composable
private fun TypeCard(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = description,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
