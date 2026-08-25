package com.miolauncher.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miolauncher.app.data.GameVersion
import com.miolauncher.app.data.GameVersionType
import com.miolauncher.app.data.GameLauncher
import com.miolauncher.app.data.MioRepository
import com.miolauncher.app.ui.components.MioFeatureCard
import com.miolauncher.app.ui.components.OfflineLoginDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MioGreen = Color(0xFF4CAF50)

@Composable
fun HomeScreen(
    selectedVersionId: String?,
    onSelectVersion: (String) -> Unit,
    onLaunch: (GameVersion) -> Unit = {},
    onNavigateToTab: (Int) -> Unit = {},
    onOpenDownloadTab: (Int) -> Unit = {},
    onOpenLaunchSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    var installedVersions by remember { mutableStateOf<List<GameVersion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showSwitcher by remember { mutableStateOf(false) }
    var showOfflineDialog by remember { mutableStateOf(false) }
    var showVersionSettingsDialog by remember { mutableStateOf(false) }
    var patchStatus by remember { mutableStateOf<Pair<String, String>>(Pair("", "")) }  // (提示, 目标target)
    var appUpdate by remember { mutableStateOf<com.miolauncher.app.data.AppUpdate?>(null) }
    // 是否已完成首次加载（切页回来不重复拉取版本列表/补丁）
    var loadedOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 首次进入主页才加载；切到其他页再回来直接用缓存/已加载状态，避免重复联网卡顿
        if (loadedOnce) {
            appUpdate = com.miolauncher.app.data.PatchManager.fetchAppUpdateCached()
            loading = false
            return@LaunchedEffect
        }
        loadedOnce = true
        withContext(Dispatchers.IO) {
            try {
                val repo = MioRepository(context)
                installedVersions = repo.loadInstalledVersions()
            } catch (_: Throwable) {}  // 用 Throwable：HMCL 库可能抛 Error（VerifyError 等），Exception 捕获不到
            // 检查热更新补丁（静默，不打断界面）
            try {
                val manifest = com.miolauncher.app.data.PatchManager.fetchManifest(context)
                val pending = com.miolauncher.app.data.PatchManager.pendingPatches(context, manifest)
                if (pending.isNotEmpty()) {
                    val p = pending.first()
                    val target = p.optString("target")
                    withContext(Dispatchers.Main) {
                        patchStatus = Pair(p.optString("desc", "发现可用补丁"), target)
                    }
                }
            } catch (_: Throwable) {
            }
            // 检查完整 APK 更新：直接用启动时缓存的检查结果（不重复联网）
            val update = com.miolauncher.app.data.PatchManager.fetchAppUpdateCached()
            if (update != null) {
                withContext(Dispatchers.Main) {
                    appUpdate = update
                }
            }
        }
        loading = false
    }

    // 补丁安装：切到全屏下载界面（进度/速度/大小，完成后可重启）
    fun installPatch() {
        val intent = android.content.Intent(context, com.miolauncher.app.PatchDownloadActivity::class.java)
            .putExtra("mode", "patch")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // 完整 APK 更新：切到全屏下载界面（下载完成后调起系统安装器）
    fun updateApp() {
        val intent = android.content.Intent(context, com.miolauncher.app.PatchDownloadActivity::class.java)
            .putExtra("mode", "apk")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun dismissPatch() {
        val (_, target) = patchStatus
        if (target.isNotBlank()) com.miolauncher.app.data.PatchManager.skipPatch(context, target)
        patchStatus = Pair("", "")
    }

    val selectedVersion = installedVersions.firstOrNull { it.id == selectedVersionId }
        ?: installedVersions.firstOrNull()

    fun selectVersion(ver: GameVersion?) {
        if (ver != null) onSelectVersion(ver.id)
        showSwitcher = false
    }

    fun play(version: GameVersion) {
        val has = GameLauncher.hasAccount(context)
        android.util.Log.d("MioHome", "play ${version.id} hasAccount=$has")
        if (has) {
            onLaunch(version)
        } else {
            showOfflineDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        WelcomeBanner()
        Spacer(Modifier.height(16.dp))

        if (patchStatus.first.isNotBlank()) {
            androidx.compose.material3.Surface(
                color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        androidx.compose.material3.Text(
                            text = patchStatus.first,
                            fontSize = 13.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (patchStatus.second.isNotBlank()) {
                        androidx.compose.material3.TextButton(onClick = { installPatch() }) {
                            androidx.compose.material3.Text("安装")
                        }
                        androidx.compose.material3.TextButton(onClick = { dismissPatch() }) {
                            androidx.compose.material3.Text("忽略")
                        }
                    } else if (patchStatus.first.contains("完成") || patchStatus.first.contains("失败")) {
                        androidx.compose.material3.TextButton(onClick = { patchStatus = Pair("", "") }) {
                            androidx.compose.material3.Text("知道了")
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // 完整 APK 更新提示（不可热更新的内容 → 整包更新，样式与补丁区分）
        appUpdate?.let { up ->
            androidx.compose.material3.Surface(
                color = com.miolauncher.app.ui.theme.MioAccent.copy(alpha = 0.18f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        Column {
                            Text(
                                text = "发现新版本 v${up.versionName}",
                                fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = com.miolauncher.app.ui.theme.MioAccent,
                            )
                            Text(
                                text = up.desc.ifBlank { "包含全新功能与修复" },
                                fontSize = 12.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    androidx.compose.material3.TextButton(onClick = { updateApp() }) {
                        androidx.compose.material3.Text("更新", color = com.miolauncher.app.ui.theme.MioAccent)
                    }
                    androidx.compose.material3.TextButton(onClick = { appUpdate = null }) {
                        androidx.compose.material3.Text("忽略")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        VersionCard(
            version = selectedVersion,
            allVersions = installedVersions,
            showSwitcher = showSwitcher,
            onSwitcherToggle = { showSwitcher = !showSwitcher },
            onVersionSelected = { ver ->
                selectVersion(ver)
            },
            onPlayClick = { selectedVersion?.let(::play) },
            onSettingsClick = { showVersionSettingsDialog = true },
        )
        Spacer(Modifier.height(20.dp))

        if (installedVersions.size > 1) {
            Text(
                text = com.miolauncher.app.ui.theme.I18n.tr("home.installed_versions"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            installedVersions.forEach { ver ->
                InstalledVersionRow(
                    version = ver,
                    isCurrent = ver.id == selectedVersion?.id,
                    onPlayClick = { play(ver) },
                    onSelect = { selectVersion(ver) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = com.miolauncher.app.ui.theme.I18n.tr("home.quick_features"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        FeatureGrid(
            onNavigateToTab = onNavigateToTab,
            onOpenDownloadTab = onOpenDownloadTab,
            onOpenLaunchSettings = onOpenLaunchSettings,
            context = context,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showOfflineDialog) {
        val version = selectedVersion
        OfflineLoginDialog(
            initialUsername = "",
            onConfirm = { name ->
                GameLauncher.saveOfflineUsername(context, name)
                showOfflineDialog = false
                if (version != null) onLaunch(version)
            },
            onDismiss = { showOfflineDialog = false },
        )
    }

    // 版本设置对话框（组件隔离开关）
    if (showVersionSettingsDialog) {
        val version = selectedVersion
        if (version != null) {
            var cfg by remember { mutableStateOf(
                com.miolauncher.app.data.VersionConfigStore.load(context, version.id)
            ) }
            AlertDialog(
                onDismissRequest = { showVersionSettingsDialog = false },
                title = { Text("版本设置 — ${version.id}") },
                text = {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("组件隔离", fontWeight = FontWeight.Bold)
                                Text(
                                    "启用后 mods/config/saves 独立存储，不影响其他版本",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = cfg.isolated,
                                onCheckedChange = {
                                    cfg = cfg.copy(isolated = it)
                                    com.miolauncher.app.data.VersionConfigStore.save(context, version.id, cfg)
                                },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "隔离状态：${if (cfg.isolated) "已启用 — 实例目录独立" else "未启用 — 共享全局目录"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (cfg.isolated) MioGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showVersionSettingsDialog = false }) {
                        Text("确定")
                    }
                },
            )
        }
    }
}

@Composable
private fun WelcomeBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF66BB6A)),
                ),
                shape = RoundedCornerShape(20.dp),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "MioLauncher",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = com.miolauncher.app.ui.theme.I18n.tr("home.tagline"),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "JAVA EDITION",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

private fun GameVersionType.label(): String = when (this) {
    GameVersionType.RELEASE -> "正式版"
    GameVersionType.SNAPSHOT -> "快照版"
    GameVersionType.BETA -> "测试版"
    GameVersionType.ALPHA -> "远古版"
}

private fun GameVersionType.color(): Color = when (this) {
    GameVersionType.RELEASE -> Color(0xFF4CAF50)
    GameVersionType.SNAPSHOT -> Color(0xFF2196F3)
    GameVersionType.BETA -> Color(0xFFFF9800)
    GameVersionType.ALPHA -> Color(0xFFE91E63)
}

@Composable
private fun VersionCard(
    version: GameVersion?,
    allVersions: List<GameVersion>,
    showSwitcher: Boolean,
    onSwitcherToggle: () -> Unit,
    onVersionSelected: (GameVersion) -> Unit,
    onPlayClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = com.miolauncher.app.ui.theme.I18n.tr("home.current_version"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = version?.id ?: com.miolauncher.app.ui.theme.I18n.tr("home.no_version"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (allVersions.size > 1) {
                            IconButton(onClick = onSwitcherToggle, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = "切换版本",
                                    tint = MioGreen,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                    version?.let {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(it.type.color(), CircleShape),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${it.type.label()}${if (it.releaseTime.isNotEmpty()) " · ${it.releaseTime}" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (version != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 点击回弹：按下缩放 0.94，松手弹簧回弹到 1
                        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val isPressed by interaction.collectIsPressedAsState()
                        val scale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isPressed) 0.94f else 1f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.45f, stiffness = 400f,
                            ),
                            label = "playBounce",
                        )
                        Button(
                            onClick = onPlayClick,
                            colors = ButtonDefaults.buttonColors(containerColor = MioGreen),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 20.dp, vertical = 12.dp,
                            ),
                            interactionSource = interaction,
                            modifier = Modifier.scale(scale),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(com.miolauncher.app.ui.theme.I18n.tr("home.start_game"), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { onSettingsClick() },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                .size(44.dp),
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "版本设置",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = showSwitcher,
                onDismissRequest = onSwitcherToggle,
            ) {
                allVersions.forEachIndexed { idx, ver ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(ver.type.color(), CircleShape),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(ver.id, fontWeight = FontWeight.Medium)
                                    Text(
                                        ver.type.label(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        onClick = { onVersionSelected(ver) },
                        leadingIcon = if (idx == allVersions.indexOf(version)) {
                            { Icon(Icons.Filled.CheckCircle, null, tint = MioGreen, modifier = Modifier.size(20.dp)) }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledVersionRow(
    version: GameVersion,
    isCurrent: Boolean,
    onPlayClick: () -> Unit,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MioGreen.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (isCurrent) MioGreen else Color.Gray,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = version.id,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(version.type.color(), CircleShape),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = version.type.label(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (version.releaseTime.isNotEmpty()) {
                        Text(
                            text = " · ${version.releaseTime}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = MioGreen),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 14.dp, vertical = 8.dp,
                ),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(com.miolauncher.app.ui.theme.I18n.tr("home.launch"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FeatureGrid(
    onNavigateToTab: (Int) -> Unit,
    onOpenDownloadTab: (Int) -> Unit,
    onOpenLaunchSettings: () -> Unit,
    context: Context,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MioFeatureCard(
                title = com.miolauncher.app.ui.theme.I18n.tr("home.version_mgmt"),
                subtitle = com.miolauncher.app.ui.theme.I18n.tr("home.version_mgmt_sub"),
                onClick = { onOpenDownloadTab(0) },
                modifier = Modifier.weight(1f),
            )
            MioFeatureCard(
                title = com.miolauncher.app.ui.theme.I18n.tr("home.mods_center"),
                subtitle = com.miolauncher.app.ui.theme.I18n.tr("home.mods_center_sub"),
                onClick = { onOpenDownloadTab(1) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MioFeatureCard(
                title = com.miolauncher.app.ui.theme.I18n.tr("home.shaders"),
                subtitle = com.miolauncher.app.ui.theme.I18n.tr("home.shaders_sub"),
                onClick = { onOpenDownloadTab(2) },
                modifier = Modifier.weight(1f),
            )
            MioFeatureCard(
                title = com.miolauncher.app.ui.theme.I18n.tr("home.modpacks"),
                subtitle = com.miolauncher.app.ui.theme.I18n.tr("home.modpacks_sub"),
                onClick = { onOpenDownloadTab(5) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MioFeatureCard(
                title = com.miolauncher.app.ui.theme.I18n.tr("home.worlds"),
                subtitle = com.miolauncher.app.ui.theme.I18n.tr("home.worlds_sub"),
                onClick = { onOpenDownloadTab(4) },
                modifier = Modifier.weight(1f),
            )
            MioFeatureCard(
                title = com.miolauncher.app.ui.theme.I18n.tr("home.resources"),
                subtitle = com.miolauncher.app.ui.theme.I18n.tr("home.resources_sub"),
                onClick = { onNavigateToTab(2) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MioFeatureCard(
                title = "启动设置",
                subtitle = "渲染器 / 内存 / 性能",
                onClick = onOpenLaunchSettings,
                modifier = Modifier.weight(1f),
            )
            MioFeatureCard(
                title = "Java 运行时",
                subtitle = "版本 / 设备信息",
                onClick = { onNavigateToTab(3) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
