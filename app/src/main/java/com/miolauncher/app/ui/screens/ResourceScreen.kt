package com.miolauncher.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miolauncher.app.data.GameVersion
import com.miolauncher.app.data.GameVersionType
import com.miolauncher.app.data.MioRepository
import com.miolauncher.app.ui.components.DownloadRow
import com.miolauncher.app.ui.theme.MioGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun GameVersionType.label(): String = when (this) {
    GameVersionType.RELEASE -> "正式版"
    GameVersionType.SNAPSHOT -> "快照"
    GameVersionType.BETA -> "测试版"
    GameVersionType.ALPHA -> "远古版"
}

private fun GameVersionType.color(): Color = when (this) {
    GameVersionType.RELEASE -> Color(0xFF4CAF50)
    GameVersionType.SNAPSHOT -> Color(0xFF2196F3)
    GameVersionType.BETA -> Color(0xFFFF9800)
    GameVersionType.ALPHA -> Color(0xFFE91E63)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceScreen(
    selectedVersionId: String? = null,
    onSelectVersion: (String) -> Unit = {},
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
) {
    val tabs = listOf(
        com.miolauncher.app.ui.theme.I18n.tr("dl.tab_versions"),
        com.miolauncher.app.ui.theme.I18n.tr("dl.tab_mods"),
        com.miolauncher.app.ui.theme.I18n.tr("dl.tab_shaders"),
        com.miolauncher.app.ui.theme.I18n.tr("dl.tab_resources"),
        com.miolauncher.app.ui.theme.I18n.tr("dl.tab_worlds"),
        com.miolauncher.app.ui.theme.I18n.tr("dl.tab_modpacks"),
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = com.miolauncher.app.ui.theme.I18n.tr("res.title"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TargetVersionSwitcher(
                selectedVersionId = selectedVersionId,
                onSelectVersion = onSelectVersion,
            )
        }
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
        when (selectedTab) {
            0 -> InstalledVersionList()
            1 -> ModListScreen(selectedVersionId = selectedVersionId)
            2 -> LocalResourceList(com.miolauncher.app.data.ResourceInstaller.Type.SHADER, selectedVersionId)
            3 -> LocalResourceList(com.miolauncher.app.data.ResourceInstaller.Type.RESOURCE_PACK, selectedVersionId)
            4 -> LocalResourceList(com.miolauncher.app.data.ResourceInstaller.Type.WORLD, selectedVersionId)
            5 -> LocalResourceList(com.miolauncher.app.data.ResourceInstaller.Type.MODPACK, selectedVersionId)
        }
    }
}

/**
 * 右上角目标版本切换器：显示当前安装/兼容匹配所用的版本，点击弹出下拉选择。
 * 选择结果同步到主页「当前游戏版本」，各处保持一致。
 */
@Composable
private fun TargetVersionSwitcher(
    selectedVersionId: String?,
    onSelectVersion: (String) -> Unit,
) {
    val context = LocalContext.current
    var versions by remember { mutableStateOf<List<GameVersion>>(emptyList()) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        versions = withContext(Dispatchers.IO) {
            runCatching { com.miolauncher.app.data.MioRepository(context).loadInstalledVersions() }
                .getOrDefault(emptyList())
        }
    }

    val current = versions.firstOrNull { it.id == selectedVersionId } ?: versions.firstOrNull()

    Box {
        Surface(
            onClick = { showMenu = true },
            shape = RoundedCornerShape(8.dp),
            color = MioGreen.copy(alpha = 0.12f),
            contentColor = MioGreen,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "目标：${current?.id ?: "未安装版本"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            versions.forEach { ver ->
                DropdownMenuItem(
                    text = { Text(ver.id, fontWeight = if (ver.id == current?.id) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        showMenu = false
                        onSelectVersion(ver.id)
                    },
                    leadingIcon = if (ver.id == current?.id) {
                        { Icon(Icons.Filled.CheckCircle, null, tint = MioGreen, modifier = Modifier.size(20.dp)) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun InstalledVersionList() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var versions by remember { mutableStateOf<List<GameVersion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var deleteTarget by remember { mutableStateOf<GameVersion?>(null) }

    fun reload() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val repo = MioRepository(context)
                    versions = repo.loadInstalledVersions()
                } catch (_: Throwable) {}
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (versions.isEmpty()) {
        EmptyResourceHint()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "已安装 ${versions.size} 个版本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(versions, key = { it.id }) { ver ->
                DownloadRow(
                    title = ver.id,
                    subtitle = "${ver.type.label()}${if (ver.releaseTime.isNotEmpty()) " · ${ver.releaseTime}" else ""}",
                    extra = "点击启动 · 长按删除",
                    installed = true,
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(ver.type.color(), CircleShape),
                        )
                    },
                    onClick = { },
                    trailing = {
                        IconButton(onClick = { deleteTarget = ver }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除版本",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                )
            }
        }
    }

    deleteTarget?.let { ver ->
        DeleteVersionDialog(
            versionId = ver.id,
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            val repo = MioRepository(context)
                            repo.deleteVersion(ver.id)
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "删除失败：${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    reload()
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun DeleteVersionDialog(
    versionId: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除版本 $versionId？", fontWeight = FontWeight.Bold) },
        text = { Text("删除后，该版本的游戏文件将被移除。此操作无法撤销。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun LocalResourceList(
    type: com.miolauncher.app.data.ResourceInstaller.Type,
    selectedVersionId: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Pair<com.miolauncher.app.data.ModManager.Source, String>>>(emptyList()) }
    var deleteTarget by remember { mutableStateOf<Pair<com.miolauncher.app.data.ModManager.Source, String>?>(null) }
    var filterAll by remember { mutableStateOf(false) }
    var isolated by remember { mutableStateOf(false) }

    val versionId = selectedVersionId

    fun reload() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val list = mutableListOf<Pair<com.miolauncher.app.data.ModManager.Source, String>>()
                // 列出目录（解压导入的整合包/光影/地图）与文件，整合包等以独立目录形式存在
                fun scan(dir: java.io.File, source: com.miolauncher.app.data.ModManager.Source) {
                    val entries = dir.listFiles() ?: return
                    entries.forEach { f ->
                        if (f.isDirectory || f.isFile) {
                            list.add(source to f.name + if (f.isDirectory) "/" else "")
                        }
                    }
                }
                if (versionId != null) {
                    val inst = com.miolauncher.app.data.VersionConfigStore.getInstanceDir(context, versionId)
                    if (inst != null) {
                        isolated = true
                        scan(File(inst, type.dirName), com.miolauncher.app.data.ModManager.Source.ISOLATED)
                    }
                }
                scan(File(com.miolauncher.app.data.MioRepository(context).gameDir, type.dirName), com.miolauncher.app.data.ModManager.Source.GLOBAL)
                items = list.sortedWith(compareBy({ it.first == com.miolauncher.app.data.ModManager.Source.ISOLATED }, { it.second.lowercase() }))
            }
        }
    }

    LaunchedEffect(type, versionId) { reload() }

    // 自定义导入：系统文件选择器
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val err = withContext(Dispatchers.IO) {
                    runCatching {
                        val ext = if (type == com.miolauncher.app.data.ResourceInstaller.Type.MOD) "jar" else "zip"
                        val tmp = File(context.cacheDir, "import_${System.currentTimeMillis()}.$ext")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { output -> input.copyTo(output) }
                        }
                        val r = com.miolauncher.app.data.ResourceImporter.import(context, type, tmp, versionId)
                        tmp.delete()
                        r
                    }.getOrNull()
                }
                if (err != null) {
                    Toast.makeText(context, "导入失败：$err", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                }
                reload()
            }
        }
    }

    // "可用/全部"：模组按隔离语义过滤；光影/地图/整合包无兼容性概念，"可用"即全部
    val shown = if (type == com.miolauncher.app.data.ResourceInstaller.Type.MOD) {
        items.filter { if (filterAll) true else it.first == com.miolauncher.app.data.ModManager.Source.GLOBAL || isolated }
    } else {
        items
    }

    Column(Modifier.fillMaxSize()) {
        // 筛选：可用 / 全部
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            FilterChip(
                selected = !filterAll,
                onClick = { filterAll = false },
                label = { Text("可用") },
            )
            FilterChip(
                selected = filterAll,
                onClick = { filterAll = true },
                label = { Text("全部") },
            )
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = false,
                onClick = {
                    val mime = if (type == com.miolauncher.app.data.ResourceInstaller.Type.MOD)
                        arrayOf("application/java-archive", "application/zip", "*/*")
                    else arrayOf("application/zip", "*/*")
                    importLauncher.launch(mime)
                },
                label = { Text("导入") },
            )
            Text(
                text = "${items.size} 个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(com.miolauncher.app.ui.theme.I18n.tr("res.empty"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(com.miolauncher.app.ui.theme.I18n.tr("res.go_download"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("当前版本未启用隔离，全部文件属于全局", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = "已安装 ${items.size} 个${typeLabel(type)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(shown, key = { "${it.first}-${it.second}" }) { (source, name) ->
                    DownloadRow(
                        title = name,
                        subtitle = if (source == com.miolauncher.app.data.ModManager.Source.ISOLATED) "隔离实例" else "全局目录",
                        extra = typeLabel(type),
                        installed = true,
                        icon = { Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp)) },
                        onClick = { },
                        trailing = {
                            IconButton(onClick = { deleteTarget = source to name }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    deleteTarget?.let { (source, name) ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("${com.miolauncher.app.ui.theme.I18n.tr("res.delete")} $name？", fontWeight = FontWeight.Bold) },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // 目录（整合包/光影解压）递归删除；文件直接删
                            val cleanName = name.removeSuffix("/")
                            val target = if (source == com.miolauncher.app.data.ModManager.Source.ISOLATED) {
                                val inst = com.miolauncher.app.data.VersionConfigStore.getInstanceDir(context, versionId!!)
                                File(inst, "${type.dirName}/$cleanName")
                            } else {
                                com.miolauncher.app.data.ResourceInstaller.resolve(context, type, cleanName)
                            }
                            if (target.isDirectory) target.deleteRecursively() else target.delete()
                        }
                        reload()
                    }
                }) {
                    Text("删除", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

private fun typeLabel(type: com.miolauncher.app.data.ResourceInstaller.Type): String = when (type) {
    com.miolauncher.app.data.ResourceInstaller.Type.MOD -> "模组"
    com.miolauncher.app.data.ResourceInstaller.Type.SHADER -> "光影"
    com.miolauncher.app.data.ResourceInstaller.Type.RESOURCE_PACK -> "材质包"
    com.miolauncher.app.data.ResourceInstaller.Type.WORLD -> "地图"
    com.miolauncher.app.data.ResourceInstaller.Type.MODPACK -> "整合包"
}

@Composable
private fun EmptyResourceHint() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(com.miolauncher.app.ui.theme.I18n.tr("res.empty"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(com.miolauncher.app.ui.theme.I18n.tr("res.go_download"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}
