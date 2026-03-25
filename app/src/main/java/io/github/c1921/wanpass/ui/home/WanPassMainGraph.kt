package io.github.c1921.wanpass.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DragIndicator
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import io.github.c1921.wanpass.domain.repository.VaultRepository
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
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val query: String = "",
    val selectedTab: VaultItemType = VaultItemType.LOGIN,
    val visibleItems: List<VaultItemSummary> = emptyList(),
    val emptyState: HomeEmptyState = HomeEmptyState.EmptyVault,
)

internal fun homeListItemKey(section: String, itemId: String): String = "$section:$itemId"

enum class HomeEmptyState {
    None,
    EmptyVault,
    EmptyTab,
    NoSearchResults,
}

internal fun resolveHomeEmptyState(
    totalItemCount: Int,
    currentTabItemCount: Int,
    visibleItemCount: Int,
): HomeEmptyState = when {
    totalItemCount == 0 -> HomeEmptyState.EmptyVault
    currentTabItemCount == 0 -> HomeEmptyState.EmptyTab
    visibleItemCount > 0 -> HomeEmptyState.None
    else -> HomeEmptyState.NoSearchResults
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    searchIndex: SearchIndex,
    webDavSyncGateway: WebDavSyncGateway,
) : ViewModel() {
    private val queryFlow = MutableStateFlow("")
    private val selectedTabFlow = MutableStateFlow(VaultItemType.LOGIN)

    init {
        webDavSyncGateway.requestSync()
    }

    val uiState: StateFlow<HomeUiState> = combine(
        vaultRepository.observeSummaries(),
        queryFlow,
        selectedTabFlow,
        searchIndex.entries,
    ) { summaries, query, selectedTab, _ ->
        val matchedIds = searchIndex.matches(query)
        val tabItems = summaries.filter { it.type == selectedTab }
        val filtered = if (query.isBlank()) tabItems else tabItems.filter { it.id in matchedIds }
        HomeUiState(
            query = query,
            selectedTab = selectedTab,
            visibleItems = filtered,
            emptyState = resolveHomeEmptyState(
                totalItemCount = summaries.size,
                currentTabItemCount = tabItems.size,
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

    fun updateSelectedTab(value: VaultItemType) {
        selectedTabFlow.update { value }
    }

    fun reorderItem(
        itemId: String,
        previousItemId: String?,
        nextItemId: String?,
    ) {
        if (queryFlow.value.isNotBlank()) return
        viewModelScope.launch {
            vaultRepository.reorderItem(
                type = selectedTabFlow.value,
                itemId = itemId,
                previousItemId = previousItemId,
                nextItemId = nextItemId,
            )
        }
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
        onTabSelected = viewModel::updateSelectedTab,
        onOpenSettings = onOpenSettings,
        onAddNew = onAddNew,
        onOpenItem = onOpenItem,
        onReorder = viewModel::reorderItem,
        onRestoreFromBackup = onRestoreFromBackup,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onQueryChange: (String) -> Unit,
    onTabSelected: (VaultItemType) -> Unit,
    onOpenSettings: () -> Unit,
    onAddNew: () -> Unit,
    onOpenItem: (String) -> Unit,
    onReorder: (String, String?, String?) -> Unit,
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
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        top = innerPadding.calculateTopPadding() + 16.dp,
                        end = 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (uiState.selectedTab == VaultItemType.LOGIN) {
                                "搜索密码标题、账号、网站或备注"
                            } else {
                                "搜索笔记标题、正文或备注"
                            }
                        )
                    },
                    singleLine = true,
                )
                TabRow(selectedTabIndex = if (uiState.selectedTab == VaultItemType.LOGIN) 0 else 1) {
                    Tab(
                        selected = uiState.selectedTab == VaultItemType.LOGIN,
                        onClick = { onTabSelected(VaultItemType.LOGIN) },
                        text = { Text("密码") },
                        icon = {
                            Icon(Icons.Rounded.Password, contentDescription = null)
                        },
                    )
                    Tab(
                        selected = uiState.selectedTab == VaultItemType.NOTE,
                        onClick = { onTabSelected(VaultItemType.NOTE) },
                        text = { Text("笔记") },
                        icon = {
                            Icon(Icons.Rounded.Note, contentDescription = null)
                        },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = innerPadding.calculateBottomPadding(),
                    ),
            ) {
                when (uiState.emptyState) {
                    HomeEmptyState.EmptyVault -> EmptyVaultCard(onRestoreFromBackup = onRestoreFromBackup)
                    HomeEmptyState.EmptyTab -> EmptyTabCard(selectedTab = uiState.selectedTab)
                    HomeEmptyState.NoSearchResults -> NoSearchResultsCard()
                    HomeEmptyState.None -> HomeList(
                        items = uiState.visibleItems,
                        canReorder = uiState.query.isBlank(),
                        bottomPadding = 88.dp,
                        onOpenItem = onOpenItem,
                        onReorder = onReorder,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeList(
    items: List<VaultItemSummary>,
    canReorder: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpenItem: (String) -> Unit,
    onReorder: (String, String?, String?) -> Unit,
) {
    val listState = rememberLazyListState()
    val displayedItems = remember { mutableStateListOf<VaultItemSummary>() }
    val reorderState = remember(listState) {
        HomeReorderState(
            listState = listState,
            onMove = { fromIndex, toIndex -> displayedItems.move(fromIndex, toIndex) },
            onMoveCommitted = onReorder,
        )
    }
    LaunchedEffect(items, canReorder, reorderState.isDragging) {
        if (!canReorder) {
            reorderState.cancel()
        }
        if (!reorderState.isDragging) {
            displayedItems.clear()
            displayedItems.addAll(items)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(displayedItems, key = { it.id }) { item ->
            val showHandle = canReorder && displayedItems.size > 1
            VaultSummaryCard(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = reorderState.translationFor(item.id).roundToInt()) }
                    .zIndex(if (reorderState.isDragging(item.id)) 1f else 0f),
                item = item,
                onClick = { onOpenItem(item.id) },
                dragHandle = if (showHandle) {
                    {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .pointerInput(item.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            reorderState.startDrag(item.id)
                                        },
                                        onDrag = { _, dragAmount ->
                                            reorderState.dragBy(dragAmount.y)
                                        },
                                        onDragEnd = {
                                            reorderState.finish(displayedItems.map(VaultItemSummary::id))
                                        },
                                        onDragCancel = {
                                            reorderState.cancel()
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DragIndicator,
                                contentDescription = "拖动排序",
                            )
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}

private class HomeReorderState(
    private val listState: LazyListState,
    private val onMove: (Int, Int) -> Unit,
    private val onMoveCommitted: (String, String?, String?) -> Unit,
) {
    private var draggedItemId by mutableStateOf<String?>(null)
    private var initialIndex by mutableIntStateOf(-1)
    private var dragOffset by mutableFloatStateOf(0f)

    val isDragging: Boolean
        get() = draggedItemId != null

    fun isDragging(itemId: String): Boolean = draggedItemId == itemId

    fun translationFor(itemId: String): Float = if (draggedItemId == itemId) dragOffset else 0f

    fun startDrag(itemId: String) {
        draggedItemId = itemId
        initialIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemId }?.index ?: -1
        dragOffset = 0f
    }

    fun dragBy(deltaY: Float) {
        val currentItemId = draggedItemId ?: return
        dragOffset += deltaY
        val currentItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == currentItemId } ?: return
        moveIfNeeded(currentItem)
    }

    fun finish(itemIdsInOrder: List<String>) {
        val movedItemId = draggedItemId ?: return
        val newIndex = itemIdsInOrder.indexOf(movedItemId)
        val previousItemId = itemIdsInOrder.getOrNull(newIndex - 1)
        val nextItemId = itemIdsInOrder.getOrNull(newIndex + 1)
        val originalIndex = initialIndex
        reset()
        if (newIndex != -1 && originalIndex != -1 && newIndex != originalIndex) {
            onMoveCommitted(movedItemId, previousItemId, nextItemId)
        }
    }

    fun cancel() {
        reset()
    }

    private fun moveIfNeeded(currentItem: LazyListItemInfo) {
        if (dragOffset > 0f) {
            val nextItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentItem.index + 1 } ?: return
            val draggedBottom = currentItem.offset + currentItem.size + dragOffset
            val nextMidPoint = nextItem.offset + (nextItem.size / 2f)
            if (draggedBottom > nextMidPoint) {
                onMove(currentItem.index, nextItem.index)
                dragOffset -= nextItem.size
            }
            return
        }
        if (dragOffset < 0f) {
            val previousItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentItem.index - 1 } ?: return
            val draggedTop = currentItem.offset + dragOffset
            val previousMidPoint = previousItem.offset + (previousItem.size / 2f)
            if (draggedTop < previousMidPoint) {
                onMove(currentItem.index, previousItem.index)
                dragOffset += previousItem.size
            }
        }
    }

    private fun reset() {
        draggedItemId = null
        initialIndex = -1
        dragOffset = 0f
    }
}

private fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) return
    val item = removeAt(fromIndex)
    add(toIndex, item)
}

@Composable
private fun EmptyTabCard(
    selectedTab: VaultItemType,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (selectedTab == VaultItemType.LOGIN) "还没有密码" else "还没有笔记",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (selectedTab == VaultItemType.LOGIN) {
                    "点击右下角按钮添加第一条密码记录。"
                } else {
                    "点击右下角按钮添加第一条笔记。"
                }
            )
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
    modifier: Modifier = Modifier,
    item: VaultItemSummary,
    onClick: () -> Unit,
    dragHandle: (@Composable (() -> Unit))? = null,
) {
    Card(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
                        Text(text = if (item.type == VaultItemType.LOGIN) "密码" else "笔记")
                    }
                    Text(text = formatTimestamp(item.updatedAt))
                }
            }
            dragHandle?.invoke()
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
