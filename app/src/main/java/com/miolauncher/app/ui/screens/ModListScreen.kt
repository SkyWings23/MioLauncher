package com.miolauncher.app.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miolauncher.app.data.ModJarReader
import com.miolauncher.app.data.ModLoader
import com.miolauncher.app.data.ModManager
import com.miolauncher.app.data.ModManager.ModEntry
import com.miolauncher.app.ui.theme.MioGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun ModLoader.label(): String = when (this) {
    ModLoader.FABRIC -> "Fabric"
    ModLoader.QUILT -> "Quilt"
    ModLoader.FORGE -> "Forge"
    ModLoader.NEO_FORGE -> "NeoForge"
    ModLoader.LITE_LOADER -> "LiteLoader"
    ModLoader.OPTIFINE -> "OptiFine"
    ModLoader.NONE -> "未知"
}

private data class CurrentVersion(val id: String?, val loader: ModLoader?)

/**
 * 本地模组列表：可用/全部 筛选 + 图标 + 勾选启停 + 点开详情。
 */
@Composable
fun ModListScreen(selectedVersionId: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ModEntry>>(emptyList()) }
    var currentVersion by remember { mutableStateOf<CurrentVersion?>(null) }
    var filterAll by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ModEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<ModEntry?>(null) }
    var loading by remember { mutableStateOf(true) }

    fun reload() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val repo = com.miolauncher.app.data.MioRepository(context)
                val all = repo.loadInstalledVersions()
                val active = all.firstOrNull { it.id == selectedVersionId } ?: all.firstOrNull()
                val json = active?.let { File(repo.gameDir, "versions/${it.id}/${it.id}.json") }
                val loader = json?.let { ModJarReader.detectVersionLoader(it) } ?: ModLoader.NONE
                currentVersion = CurrentVersion(active?.id, loader)
                entries = ModManager.list(context, active?.id, loader, active?.id)
                loading = false
            }
        }
    }

    LaunchedEffect(selectedVersionId) { reload() }

    // 自定义导入：系统文件选择器选 jar
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val err = withContext(Dispatchers.IO) {
                    runCatching {
                        val tmp = File(context.cacheDir, "import_mod_${System.currentTimeMillis()}.jar")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { output -> input.copyTo(output) }
                        }
                        val r = com.miolauncher.app.data.ResourceImporter.import(
                            context, com.miolauncher.app.data.ResourceInstaller.Type.MOD, tmp, currentVersion?.id,
                        )
                        tmp.delete()
                        r
                    }.getOrNull()
                }
                if (err != null) {
                    Toast.makeText(context, "导入失败：$err", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "模组导入成功", Toast.LENGTH_SHORT).show()
                }
                reload()
            }
        }
    }

    val shown = entries.filter { if (filterAll) true else it.compatible }

    Box(Modifier.fillMaxSize()) {
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
                onClick = { importLauncher.launch(arrayOf("application/java-archive", "application/zip", "*/*")) },
                label = { Text("导入") },
            )
            Text(
                text = "${entries.size} 个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("还没有安装模组", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("去「下载」页搜索模组安装", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        Icons.Filled.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (currentVersion?.loader == ModLoader.NONE)
                            "当前版本为原版，无法加载模组"
                        else "没有适配当前版本的模组",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = currentVersion?.id?.let { "当前版本：$it" } ?: "未选择版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 游戏内模组加载状态（从最新一次启动日志解析）
                item {
                    val loadResult = remember(context) {
                        with(com.miolauncher.app.data.GameModLoader) {
                            com.miolauncher.app.data.GameModLoader.loadResult(context)
                        }
                    }
                    if (loadResult.logExists) {
                        androidx.compose.material3.Card(
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (loadResult.failedCount > 0) Icons.Filled.Warning
                                        else Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = if (loadResult.failedCount > 0) MaterialTheme.colorScheme.error else MioGreen,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "上次启动加载",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = loadResult.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (loadResult.failedCount > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "失败模组：${loadResult.failedMods.joinToString("、")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
                items(shown, key = { it.fileName }) { entry ->
                    ModRow(
                        entry = entry,
                        onClick = { detail = entry },
                        onToggle = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    ModManager.setEnabled(context, currentVersion?.id, entry.fileName, !entry.enabled)
                                }
                                reload()
                            }
                        },
                    )
                }
            }
        }
    }

    detail?.let { entry ->
        ModDetailScreen(
            entry = entry,
            gameVersion = currentVersion,
            onBack = { detail = null },
            onToggle = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        ModManager.setEnabled(context, currentVersion?.id, entry.fileName, !entry.enabled)
                    }
                    detail = null
                    reload()
                }
            },
            onDelete = { deleteTarget = entry; detail = null },
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除 ${entry.displayName}？", fontWeight = FontWeight.Bold) },
            text = { Text("将从 mods 目录移除该文件，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            val r = ModManager.delete(context, currentVersion?.id, entry.fileName)
                            ModManager.clearCache()
                            r
                        }
                        if (!ok) {
                            android.widget.Toast.makeText(context, "删除失败：文件不存在或无权限", android.widget.Toast.LENGTH_SHORT).show()
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
}

@Composable
private fun ModRow(
    entry: ModEntry,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val icon = remember(entry.iconBytes) {
        entry.iconBytes?.let { b ->
            runCatching { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull()
        }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .alpha(if (entry.enabled) 1f else 0.45f),
        ) {
            // 组件图（jar 内图标；缺失用组件占位图，不再是文件夹）
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = entry.displayName,
                        modifier = Modifier.size(38.dp),
                    )
                } else {
                    Icon(
                        Icons.Filled.Extension,
                        contentDescription = null,
                        tint = MioGreen,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(if (entry.source == ModManager.Source.ISOLATED) "隔离 · " else "全局 · ")
                        if (entry.loader != ModLoader.NONE) append(entry.loader.label()).append(" · ")
                        if (entry.modVersion.isNotBlank()) append(entry.modVersion).append(" · ")
                        append(entry.baseName)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        entry.compatible -> "适配当前版本"
                        entry.loader == ModLoader.NONE -> "未知加载器"
                        else -> "不兼容当前版本"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (entry.compatible) MioGreen else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.width(8.dp))
            // 勾选 = 启用；取消勾选 = 关闭组件
            Checkbox(
                checked = entry.enabled,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

/**
 * 模组详情：左侧组件图 + 详细配置信息 + 启用开关 + 删除。
 */
@Composable
private fun ModDetailScreen(
    entry: ModEntry,
    gameVersion: CurrentVersion?,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val icon = remember(entry.iconBytes) {
        entry.iconBytes?.let { b ->
            runCatching { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = MioGreen)
            }
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            // 组件图 + 基本信息
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = entry.displayName, modifier = Modifier.size(72.dp))
                    } else {
                        Icon(Icons.Filled.Extension, contentDescription = null, tint = MioGreen, modifier = Modifier.size(48.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(entry.baseName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            entry.compatible -> "✓ 适配当前版本"
                            entry.loader == ModLoader.NONE -> "未知加载器"
                            else -> "✗ 不兼容当前版本"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (entry.compatible) MioGreen else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 启用开关
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("启用组件", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (entry.enabled) "已启用，游戏会加载此模组" else "已关闭，游戏不会加载此模组",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = entry.enabled, onCheckedChange = { onToggle() })
                }
            }

            Spacer(Modifier.height(16.dp))

            // 配置信息
            Text("详细配置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    InfoRow("文件名", entry.baseName)
                    if (entry.modVersion.isNotBlank()) InfoRow("模组版本", entry.modVersion)
                    if (entry.loader != ModLoader.NONE) InfoRow("加载器", entry.loader.label())
                    if (entry.minecraftRange.isNotBlank()) InfoRow("支持 MC 版本", entry.minecraftRange)
                    InfoRow("当前游戏版本", gameVersion?.id ?: "未选择")
                    InfoRow("状态", if (entry.enabled) "已启用" else "已关闭")
                }
            }

            if (entry.description.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("描述", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButtonRed(onClick = onDelete, text = "删除模组")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OutlinedButtonRed(onClick: () -> Unit, text: String) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }
}
