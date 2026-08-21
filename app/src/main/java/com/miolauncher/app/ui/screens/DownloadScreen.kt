package com.miolauncher.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miolauncher.app.data.GameVersion
import com.miolauncher.app.data.GameVersionType
import com.miolauncher.app.data.InstallProgress
import com.miolauncher.app.data.McLoader
import com.miolauncher.app.data.ModInfo
import com.miolauncher.app.data.ModpackInfo
import com.miolauncher.app.data.ModLoader
import com.miolauncher.app.data.ShaderInfo
import com.miolauncher.app.data.WorldInfo
import com.miolauncher.app.ui.components.DownloadRow
import com.miolauncher.app.ui.theme.MioGreen
import com.miolauncher.app.viewmodel.VersionListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    versionListViewModel: VersionListViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    selectedVersionId: String? = null,
) {
    val tabs = listOf(com.miolauncher.app.ui.theme.I18n.tr("dl.tab_versions"), com.miolauncher.app.ui.theme.I18n.tr("dl.tab_mods"), com.miolauncher.app.ui.theme.I18n.tr("dl.tab_shaders"), com.miolauncher.app.ui.theme.I18n.tr("dl.tab_worlds"), com.miolauncher.app.ui.theme.I18n.tr("dl.tab_modpacks"))

    LaunchedEffect(Unit) {
        versionListViewModel.loadIfNeeded()
    }
    val versions by versionListViewModel.versions.collectAsState()
    val loading by versionListViewModel.loading.collectAsState()
    val error by versionListViewModel.error.collectAsState()
    val installProgress by versionListViewModel.installProgress.collectAsState()
    val installMessage by versionListViewModel.installMessage.collectAsState()

    var pendingInstallVersion by remember { mutableStateOf<String?>(null) }
    var showInstallPanel by remember { mutableStateOf(false) }
    var installMinimized by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }

    // 记录安装面板是否已初始化过（避免进度更新时反复重置最小化状态）
    var installPanelInit by remember { mutableStateOf(false) }

    // 安装开始时自动弹出面板（仅在首次出现时弹出一次；后续进度更新不再改变最小化/收起状态）
    LaunchedEffect(installProgress) {
        val p = installProgress
        if (p != null && !p.isDone) {
            if (!installPanelInit) {
                installPanelInit = true
                showInstallPanel = true
                installMinimized = false
            }
        } else {
            installPanelInit = false
        }
    }

    // 当前已安装版本（用于按版本匹配模组/光影），自动带出加载器后缀（如 1.21.1-fabric）
    var installedVersions by remember { mutableStateOf<List<GameVersion>>(emptyList()) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        installedVersions = withContext(Dispatchers.IO) {
            runCatching { com.miolauncher.app.data.MioRepository(context).loadInstalledVersions() }.getOrDefault(emptyList())
        }
    }
    val activeVersion = installedVersions.firstOrNull { it.id == selectedVersionId }
        ?: installedVersions.firstOrNull()
    val resourceCompat = remember(activeVersion?.id) {
        if (activeVersion == null) ResourceCompat(null, emptyList())
        else {
            val id = activeVersion.id
            val loader = LoaderSuffix.entries.firstOrNull { id.endsWith("-${it.suffix}") }
            val base = if (loader != null) id.removeSuffix("-${loader.suffix}") else id
            ResourceCompat(base, if (loader != null) listOf(loader.modrinthName) else emptyList())
        }
    }

    var detailItem by remember { mutableStateOf<ResourceDetail?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                Text(
                    text = com.miolauncher.app.ui.theme.I18n.tr("dl.title"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MioGreen,
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { onTabSelected(index) },
                            text = {
                                Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            val current = detailItem
            if (current != null) {
                ResourceDetailScreen(
                    detail = current,
                    onBack = { detailItem = null },
                    onInstalled = { detailItem = null },
                )
            } else {
                when (selectedTab) {
                    0 -> VersionDownloadList(
                        versions = versions,
                        loading = loading,
                        error = error,
                        onRetry = { versionListViewModel.refresh() },
                        onInstall = { pendingInstallVersion = it },
                    )
                    1 -> ModDownloadList(onOpen = { item -> detailItem = item.toDetail(resourceCompat) })
                    2 -> ShaderDownloadList(onOpen = { item -> detailItem = item.toDetail(resourceCompat) })
                    3 -> WorldDownloadList(onOpen = { item -> detailItem = item.toDetail(resourceCompat) })
                    4 -> ModpackDownloadList(onOpen = { item -> detailItem = item.toDetail(resourceCompat) })
                }
            }
        }
    }

    // 安装进度面板
    val currentProgress = installProgress
    if (showInstallPanel && currentProgress != null) {
        if (installMinimized) {
            // 最小化为悬浮球
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                DraggableDownloadBall(
                    progress = currentProgress,
                    onClick = { installMinimized = false },
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            // 全屏进度弹窗
            InstallProgressDialog(
                progress = currentProgress,
                onMinimize = { installMinimized = true },
                onCloseRequest = {
                    showCloseConfirm = true
                },
            )
        }
        // 完成后自动关闭
        if (currentProgress.isDone) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                showInstallPanel = false
                versionListViewModel.dismissInstall()
            }
        }
    }

    // 完成提示
    installMessage?.let { msg ->
        if (!showInstallPanel) {
            LaunchedEffect(msg) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                versionListViewModel.dismissInstall()
            }
        }
    }

    // 加载器选择对话框
    pendingInstallVersion?.let { versionId ->
        LoaderPickerDialog(
            versionId = versionId,
            onConfirm = { loader ->
                pendingInstallVersion = null
                versionListViewModel.installVersion(versionId, loader)
            },
            onDismiss = { pendingInstallVersion = null },
        )
    }

    // 关闭下载确认框
    if (showCloseConfirm) {
        CloseDownloadConfirmDialog(
            onConfirm = {
                showCloseConfirm = false
                showInstallPanel = false
                versionListViewModel.cancelInstall()
            },
            onDismiss = { showCloseConfirm = false },
        )
    }
}

/**
 * 关闭下载确认对话框。
 */
@Composable
private fun CloseDownloadConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关闭所有下载？") },
        text = { Text("关闭后，当前所有下载任务将停止。已下载的文件会保留。") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(
                    text = "是",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("否")
            }
        },
    )
}

/**
 * 可拖动下载悬浮球：显示在界面角落，带环形进度，支持拖拽移动。
 */
@Composable
private fun DraggableDownloadBall(
    progress: com.miolauncher.app.data.InstallProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var offsetX by remember { mutableStateOf(-20f) }
    var offsetY by remember { mutableStateOf(-80f) }
    var dragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    },
                )
            },
    ) {
        androidx.compose.material3.Surface(
            onClick = { if (!dragging) onClick() },
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            border = BorderStroke(2.dp, MioGreen),
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { progress.overallProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 3.dp,
                    color = MioGreen,
                )
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "下载进度",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * 安装进度全屏弹窗：带边框，与背景区分。
 */
@Composable
private fun InstallProgressDialog(
    progress: com.miolauncher.app.data.InstallProgress,
    onMinimize: () -> Unit,
    onCloseRequest: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onMinimize,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            InstallProgressPanel(
                progress = progress,
                onMinimize = onMinimize,
                onClose = onCloseRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun LoaderPickerDialog(
    versionId: String,
    onConfirm: (McLoader) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安装版本 $versionId") },
        text = {
            Column {
                Text("选择要安装的加载器（模组 API）：")
                Spacer(Modifier.height(8.dp))
                McLoader.entries.forEach { loader ->
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(loader.label, fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text(when (loader) {
                                McLoader.NONE -> "纯净原版"
                                McLoader.FABRIC -> "轻量级，模组生态丰富"
                                McLoader.QUILT -> "Fabric 的分支"
                                McLoader.FORGE -> "经典加载器，兼容性好"
                                McLoader.NEO_FORGE -> "Forge 的继任者"
                                McLoader.LITELOADER -> "老版本专用"
                                McLoader.OPTIFINE -> "光影 + 优化"
                            })
                        },
                        modifier = Modifier.clickable { onConfirm(loader) },
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun InstallProgressPanel(
    progress: com.miolauncher.app.data.InstallProgress,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    val done = progress.isDone
    // 关联全局下载任务（版本安装为百分比模式；模组等为字节模式），实时读取该任务
    val taskId = "version-${progress.versionId}-${progress.loader.id}"
    val allTasks by com.miolauncher.app.data.DownloadManager.tasks.collectAsState()
    val task = remember(allTasks, taskId) { allTasks.firstOrNull { it.id == taskId } }

    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, MioGreen),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { progress.overallProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(30.dp),
                    color = MioGreen,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (done && progress.error != null) "安装失败"
                        else if (done) "安装完成"
                        else "正在安装 ${progress.versionId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (progress.loader == McLoader.NONE) "${progress.currentStage}"
                        else "${progress.loader.label} · ${progress.currentStage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 展开/收起切换
                if (!done) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.Info,
                            contentDescription = if (expanded) "收起详情" else "展开详情",
                        )
                    }
                }
                // 最小化按钮：收回为悬浮球
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Filled.Remove, contentDescription = "最小化到悬浮球")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            // 主进度条（展开时更高更明显）
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress.overallProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(if (expanded) 10.dp else 6.dp),
                color = if (progress.error != null) MaterialTheme.colorScheme.error else MioGreen,
            )

            // 统计行：已下载 / 剩余 / 速度 / 预计剩余
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = when {
                        task != null && task!!.total > 0 ->
                            "已下载 ${com.miolauncher.app.data.DownloadManager.formatBytes(task!!.downloaded)} / ${com.miolauncher.app.data.DownloadManager.formatBytes(task!!.total)}"
                        else -> "${(progress.overallProgress * 100).toInt()}%"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (!done) {
                    Text(
                        text = task?.let {
                            if (it.total > 0) "${com.miolauncher.app.data.DownloadManager.formatBytes(it.speed.toLong())}/s"
                            else "${String.format("%.1f", it.speed)}%/s"
                        } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MioGreen,
                    )
                }
            }
            if (!done && task != null) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = when {
                            task!!.total > 0 ->
                                "剩余 ${com.miolauncher.app.data.DownloadManager.formatBytes(task!!.remaining)} · 预计还需 ${task!!.etaText}"
                            else ->
                                "预计还需 ${task!!.etaText} · 文件 ${task!!.filesDone}/${task!!.filesTotal}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    if (task!!.total <= 0) {
                        Text(
                            text = "文件 ${task!!.filesDone}/${task!!.filesTotal}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (done) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = progress.error ?: "安装完成，可以开始游戏了",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (progress.error != null) MaterialTheme.colorScheme.error else MioGreen,
                )
            }

            // 展开的详细文件列表
            if (expanded && progress.items.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("下载文件", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                val visibleItems = progress.items.takeLast(8)
                visibleItems.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { item.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.width(120.dp).height(6.dp),
                            color = if (item.state == com.miolauncher.app.data.DownloadItemState.DONE) MioGreen else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (item.state == com.miolauncher.app.data.DownloadItemState.DONE) "✓"
                            else "${(item.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.state == com.miolauncher.app.data.DownloadItemState.DONE) MioGreen
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
 private fun VersionDownloadList(
    versions: List<GameVersion>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onInstall: (String) -> Unit,
) {
    var filterType by remember { mutableStateOf<GameVersionType?>(null) }
    val filtered = if (filterType == null) versions else versions.filter { it.type == filterType }

    when {
        loading && versions.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(color = MioGreen)
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载版本列表…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        error != null && versions.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("加载失败", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.Button(onClick = onRetry) {
                        Text("重试")
                    }
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilterChip("全部", filterType == null) { filterType = null }
                        FilterChip("正式版", filterType == GameVersionType.RELEASE) { filterType = GameVersionType.RELEASE }
                        FilterChip("快照", filterType == GameVersionType.SNAPSHOT) { filterType = GameVersionType.SNAPSHOT }
                        FilterChip("旧版", filterType == GameVersionType.BETA || filterType == GameVersionType.ALPHA) {
                            filterType = if (filterType == GameVersionType.BETA || filterType == GameVersionType.ALPHA) null else GameVersionType.BETA
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text("暂无版本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(filtered, key = { it.id }) { v ->
                    DownloadRow(
                        title = v.id,
                        subtitle = "${v.type.label} · ${v.releaseTime}",
                        extra = when {
                            v.isDownloaded -> "已安装"
                            v.size > 0 -> "${formatSize(v.size)} · 点击下载"
                            else -> "点击下载 · 可选加载器"
                        },
                        installed = v.isDownloaded,
                        icon = { },
                        onClick = {
                            if (!v.isDownloaded) onInstall(v.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MioGreen else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        border = if (!selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) else null,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ModDownloadList(onOpen: (ModInfo) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<com.miolauncher.app.data.ModrinthApi.SearchHit>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load(reset: Boolean) {
        if (reset) {
            items = emptyList()
            offset = 0
            loading = true
            error = null
        } else {
            loadingMore = true
        }
        scope.launch {
            val page = withContext(Dispatchers.IO) {
                com.miolauncher.app.data.ModrinthApi.search(
                    query.trim(), "mod",
                    selectedCategory?.let { listOf(it) } ?: emptyList(),
                    offset, 30,
                )
            }
            if (reset) {
                items = page
                if (page.isEmpty()) error = if (query.isNotBlank()) "未找到相关模组" else "暂无模组"
            } else {
                items = items + page
            }
            hasMore = page.size == 30
            offset += page.size
            loading = false
            loadingMore = false
        }
    }

    LaunchedEffect(Unit) { load(true) }

    Column(Modifier.fillMaxSize()) {
        // 共享目录警示（2d=A）
        androidx.compose.material3.Surface(
            color = MioGreen.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                com.miolauncher.app.ui.theme.I18n.tr("dl.mod_shared_warn"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(com.miolauncher.app.ui.theme.I18n.tr("dl.search_mods")) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Button(
                onClick = { load(true) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MioGreen),
                enabled = query.isNotBlank() && !searching,
            ) {
                if (searching) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(com.miolauncher.app.ui.theme.I18n.tr("dl.search"))
                }
            }
        }

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = MioGreen)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 分类筛选
                    item {
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    text = com.miolauncher.app.ui.theme.I18n.tr("dl.all"),
                                    selected = selectedCategory == null,
                                    onClick = { selectedCategory = null; load(true) },
                                )
                            }
                            items(com.miolauncher.app.data.ModrinthApi.MOD_CATEGORIES) { (code, label) ->
                                FilterChip(
                                    text = label,
                                    selected = selectedCategory == code,
                                    onClick = { selectedCategory = code; load(true) },
                                )
                            }
                        }
                    }

                    if (error != null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (items.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                Text("暂无模组，换个分类或关键词试试", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    items(items, key = { it.slug }) { h ->
                        DownloadRow(
                            title = h.title,
                            subtitle = "${h.author} · ${h.description}",
                            extra = "${h.downloads / 1_000_000}M 下载",
                            installed = false,
                            icon = {
                                com.miolauncher.app.ui.components.RemoteIcon(
                                    url = h.iconUrl,
                                    contentDescription = null,
                                    size = 48.dp,
                                )
                            },
                            onClick = {
                                onOpen(
                                    ModInfo(
                                        name = h.title, author = h.author, description = h.description,
                                        version = h.latestVersion, slug = h.slug,
                                        downloads = h.downloads, iconUrl = h.iconUrl,
                                    )
                                )
                            },
                        )
                    }

                    // 加载更多
                    if (hasMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (loadingMore) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = MioGreen,
                                    )
                                } else {
                                    androidx.compose.material3.OutlinedButton(onClick = { load(false) }) {
                                        Text(com.miolauncher.app.ui.theme.I18n.tr("dl.load_more"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShaderDownloadList(onOpen: (ShaderInfo) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<com.miolauncher.app.data.ModrinthApi.SearchHit>>(emptyList()) }
    LaunchedEffect(Unit) {
        loading = true
        val page = withContext(Dispatchers.IO) {
            com.miolauncher.app.data.ModrinthApi.search("", "shader", emptyList(), 0, 30)
        }
        items = page
        if (page.isEmpty()) error = "未找到光影资源"
        loading = false
    }
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = MioGreen)
        }
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedButton(onClick = {}) { Text("重试") }
            }
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.slug }) { h ->
                DownloadRow(
                    title = h.title,
                    subtitle = "${h.author} · ${h.description}",
                    extra = "${h.downloads / 1_000_000}M 下载",
                    installed = false,
                    icon = {
                        com.miolauncher.app.ui.components.RemoteIcon(
                            url = h.iconUrl, contentDescription = null, size = 48.dp,
                        )
                    },
                    onClick = {
                        onOpen(
                            ShaderInfo(
                                name = h.title, author = h.author, description = h.description,
                                version = h.latestVersion, slug = h.slug,
                                downloads = h.downloads, iconUrl = h.iconUrl,
                            )
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun WorldDownloadList(onOpen: (WorldInfo) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<com.miolauncher.app.data.ModrinthApi.SearchHit>>(emptyList()) }
    LaunchedEffect(Unit) {
        loading = true
        val page = withContext(Dispatchers.IO) {
            com.miolauncher.app.data.ModrinthApi.search("", "datapack", emptyList(), 0, 30)
        }
        items = page
        loading = false
    }
    Column {
        androidx.compose.material3.Surface(
            color = MioGreen.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Modrinth 无独立「世界存档」分类，此处展示地图类数据包（Data Pack）。" +
                        "完整世界存档请放入 存档目录 saves/。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        when {
            loading -> Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = MioGreen)
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("暂无地图资源", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.slug }) { h ->
                    DownloadRow(
                        title = h.title,
                        subtitle = "${h.author} · ${h.description}",
                        extra = "${h.downloads / 1_000_000}M 下载",
                        installed = false,
                        icon = {
                            com.miolauncher.app.ui.components.RemoteIcon(
                                url = h.iconUrl, contentDescription = null, size = 48.dp,
                            )
                        },
                        onClick = {
                            onOpen(
                                WorldInfo(
                                    name = h.title, author = h.author, description = h.description,
                                    version = h.latestVersion, slug = h.slug,
                                    downloads = h.downloads, iconUrl = h.iconUrl,
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModpackDownloadList(onOpen: (ModpackInfo) -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<com.miolauncher.app.data.ModrinthApi.SearchHit>>(emptyList()) }
    LaunchedEffect(Unit) {
        loading = true
        val page = withContext(Dispatchers.IO) {
            com.miolauncher.app.data.ModrinthApi.search("", "modpack", emptyList(), 0, 30)
        }
        items = page
        if (page.isEmpty()) error = "未找到整合包资源"
        loading = false
    }
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = MioGreen)
        }
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedButton(onClick = {}) { Text("重试") }
            }
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.slug }) { h ->
                DownloadRow(
                    title = h.title,
                    subtitle = "${h.author} · ${h.description}",
                    extra = "${h.downloads / 1_000_000}M 下载",
                    installed = false,
                    icon = {
                        com.miolauncher.app.ui.components.RemoteIcon(
                            url = h.iconUrl, contentDescription = null, size = 48.dp,
                        )
                    },
                    onClick = {
                        onOpen(
                            ModpackInfo(
                                name = h.title, author = h.author, description = h.description,
                                version = h.latestVersion, slug = h.slug,
                                downloads = h.downloads, iconUrl = h.iconUrl,
                            )
                        )
                    },
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000) "${(mb / 1000).toInt()}GB" else "${mb.toInt()}MB"
}

private val GameVersionType.label: String
    get() = when (this) {
        GameVersionType.RELEASE -> "正式版"
        GameVersionType.SNAPSHOT -> "快照"
        GameVersionType.BETA -> "测试版"
        GameVersionType.ALPHA -> "预览版"
    }

private val ModLoader.label: String
    get() = when (this) {
        ModLoader.FABRIC -> "Fabric"
        ModLoader.QUILT -> "Quilt"
        ModLoader.FORGE -> "Forge"
        ModLoader.NEO_FORGE -> "NeoForge"
        ModLoader.LITE_LOADER -> "LiteLoader"
        ModLoader.OPTIFINE -> "OptiFine"
        ModLoader.NONE -> "原版"
    }

private data class ResourceCompat(
    val gameVersion: String?,
    val loaders: List<String>,
)

/** 已安装版本 ID 里可能带加载器后缀（如 1.21.1-fabric） */
private enum class LoaderSuffix(val suffix: String, val modrinthName: String) {
    FABRIC("fabric", "fabric"),
    QUILT("quilt", "quilt"),
    FORGE("forge", "forge"),
    NEO_FORGE("neoforge", "neoforge"),
    OPTIFINE("optifine", "optifine"),
    LITE_LOADER("liteloader", "liteloader"),
}

private fun ModInfo.toDetail(c: ResourceCompat) = ResourceDetail(
    title = name, author = author, description = description, version = version,
    type = com.miolauncher.app.data.ResourceInstaller.Type.MOD,
    slug = slug, gameVersion = c.gameVersion, loaders = c.loaders,
)

private fun ShaderInfo.toDetail(c: ResourceCompat) = ResourceDetail(
    title = name, author = author, description = description, version = version,
    type = com.miolauncher.app.data.ResourceInstaller.Type.SHADER,
    slug = slug, gameVersion = c.gameVersion, loaders = emptyList(),
)

private fun WorldInfo.toDetail(c: ResourceCompat) = ResourceDetail(
    title = name, author = author, description = description, version = version,
    type = com.miolauncher.app.data.ResourceInstaller.Type.WORLD,
    slug = slug, gameVersion = null, loaders = emptyList(),
)

private fun ModpackInfo.toDetail(c: ResourceCompat) = ResourceDetail(
    title = name, author = author, description = description, version = version,
    type = com.miolauncher.app.data.ResourceInstaller.Type.MODPACK,
    slug = slug, gameVersion = gameVersion.takeIf { it.isNotBlank() }, loaders = listOf(loader.id),
)

private val ModLoader.id: String
    get() = when (this) {
        ModLoader.FABRIC -> "fabric"
        ModLoader.QUILT -> "quilt"
        ModLoader.FORGE -> "forge"
        ModLoader.NEO_FORGE -> "neoforge"
        ModLoader.LITE_LOADER -> "liteloader"
        ModLoader.OPTIFINE -> "optifine"
        ModLoader.NONE -> ""
    }
