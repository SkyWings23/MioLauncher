package com.miolauncher.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miolauncher.app.data.ModrinthApi
import com.miolauncher.app.data.ResourceInstaller
import com.miolauncher.app.ui.components.RemoteIcon
import com.miolauncher.app.ui.components.RemoteImage
import com.miolauncher.app.ui.theme.MioGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 资源详情（全屏页） */
data class ResourceDetail(
    val title: String,
    val author: String,
    val description: String,
    val version: String,
    val type: ResourceInstaller.Type,
    val slug: String,
    val gameVersion: String?,
    val loaders: List<String>,
)

/**
 * 全屏资源详情页：官方图标 / 画廊图 / 适配版本 / 版本选择 / 下载（含前置依赖补齐）。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ResourceDetailScreen(
    detail: ResourceDetail,
    versionId: String? = null,
    onBack: () -> Unit,
    onInstalled: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // 项目官方信息
    var project by remember { mutableStateOf<ModrinthApi.ModrinthProject?>(null) }
    var projectError by remember { mutableStateOf<String?>(null) }
    // 版本列表
    var versions by remember { mutableStateOf<List<ModrinthApi.ModrinthVersion>>(emptyList()) }
    var versionsLoading by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(0) }

    // 下载状态（统一走 DownloadManager，供悬浮窗同步展示）
    val taskId = remember(detail.slug) { "mod-${detail.slug}-${System.currentTimeMillis()}" }
    var installing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var depCount by remember { mutableStateOf(0) }
    // 待确认的前置组件（非空时弹窗询问是否一起下载）
    var pendingDeps by remember { mutableStateOf<List<String>>(emptyList()) }

    val allTasks by com.miolauncher.app.data.DownloadManager.tasks.collectAsState()
    val myTask = allTasks.firstOrNull { it.id == taskId }
    val progress = myTask?.percent ?: 0f
    val done = myTask?.isDone == true
    val success = done && myTask?.error == null

    LaunchedEffect(detail.slug) {
        if (detail.slug.isBlank()) return@LaunchedEffect
        try {
            val proj = withContext(Dispatchers.IO) { ModrinthApi.projectDetails(detail.slug) }
            project = proj
            if (proj == null) projectError = "无法获取项目信息，请检查网络"
            versionsLoading = true
            val loadedVersions = withContext(Dispatchers.IO) {
                // 整合包版本常达数百个，限制拉取数量避免大响应导致镜像截断/超时/渲染卡顿
                val limit = if (detail.type == com.miolauncher.app.data.ResourceInstaller.Type.MODPACK) 30 else 0
                runCatching { ModrinthApi.versions(detail.slug, detail.gameVersion, detail.loaders, limit) }
                    .getOrElse {
                        projectError = "版本信息解析失败，请稍后重试"
                        emptyList()
                    }
            }
            // 整合包：过滤出启动器支持（MC ≤ 1.21）的版本，并限制数量避免几百项一次性渲染卡死
            versions = if (detail.type == com.miolauncher.app.data.ResourceInstaller.Type.MODPACK) {
                val supported = loadedVersions.filter { v ->
                    v.gameVersions.any { gv -> !com.miolauncher.app.data.MioRepository.isMcAboveSupport(gv) }
                }
                if (supported.isNotEmpty()) supported.take(30) else loadedVersions.take(30)
            } else {
                loadedVersions
            }
            versionsLoading = false
            // 整合包：默认选第一个受支持版本
            if (detail.type == com.miolauncher.app.data.ResourceInstaller.Type.MODPACK && versions.isNotEmpty()) {
                val supportedIdx = versions.indexOfFirst { v ->
                    v.gameVersions.any { gv -> !com.miolauncher.app.data.MioRepository.isMcAboveSupport(gv) }
                }
                selectedIndex = if (supportedIdx >= 0) supportedIdx else 0
            } else if (selectedIndex >= versions.size) {
                selectedIndex = 0
            }
        } catch (e: Exception) {
            projectError = "获取详情失败：${e.message}"
            versionsLoading = false
        }
    }

    val selectedVersion = versions.getOrNull(selectedIndex)

    fun doInstall(includeDeps: Boolean) {
        if (installing) return
        installing = true
        depCount = 0
        status = ""
        scope.launch {
            try {
                if (detail.type == com.miolauncher.app.data.ResourceInstaller.Type.MODPACK) {
                    // 整合包：下载 mrpack → 解析 → 创建实例 → 装依赖模组 → 完成
                    val v = selectedVersion ?: throw Exception("未选择整合包版本")
                    val file = v.files.firstOrNull() ?: throw Exception("该版本无可用文件")
                    message = ""
                    withContext(Dispatchers.IO) {
                        val repo = com.miolauncher.app.data.MioRepository(context)
                        val instanceName = detail.slug.replace(Regex("[^a-zA-Z0-9_-]"), "-") + "-" + (v.versionNumber ?: "pack")
                        repo.installModpack(
                            modpackUrl = file.url,
                            instanceName = instanceName,
                            onStage = { s, p -> status = s },
                            onItem = { _, _, _ -> },
                            onTaskCount = {},
                            onTaskDone = {},
                        )
                    }
                    message = "整合包安装完成，可在主页选择 $detail.title 启动"
                } else {
                    val result = withContext(Dispatchers.IO) {
                        ResourceInstaller.install(
                            context, detail.type, detail.slug, selectedVersion,
                            detail.gameVersion, detail.loaders,
                            taskId = taskId,
                            includeDeps = includeDeps,
                            versionId = versionId,
                            onStatus = { s -> status = s },
                        )
                    }
                    depCount = result.dependencyNames.size
                    message = if (depCount > 0) "已自动补齐 $depCount 个前置" else ""
                }
            } catch (e: Exception) {
                message = e.message ?: "安装失败"
            } finally {
                installing = false
            }
        }
    }

    fun start() {
        if (installing) return
        val v = selectedVersion ?: return
        // 检查该版本声明的必需前置组件
        val required = v.dependencies
            .filter { it.dependencyType == "required" }
            .mapNotNull { it.projectId }
            .distinct()
        if (required.isEmpty()) {
            doInstall(includeDeps = true)
            return
        }
        installing = true
        scope.launch {
            val names = withContext(Dispatchers.IO) {
                required.map { ModrinthApi.projectTitle(it) ?: it }
            }
            pendingDeps = names
            installing = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = MioGreen)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (project != null && project!!.downloads > 0) {
                    Text(
                        text = "${project!!.downloads / 1_000_000}M 下载 · ${detail.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { if (!installing) onBack() }) { Text("关闭") }
        }

        if (detail.type == ResourceInstaller.Type.WORLD && detail.slug.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "该地图暂不支持在线下载（Modrinth 未收录），请手动放入游戏 saves 目录。",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 头部：官方图标 + 信息
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemoteIcon(
                        url = project?.iconUrl ?: "",
                        contentDescription = detail.title,
                        size = 88.dp,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = detail.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = detail.author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        project?.let { p ->
                            Text(
                                text = if (p.downloads > 0) "${p.downloads / 1_000_000}M 下载" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MioGreen,
                            )
                        }
                    }
                }
            }

            // 官方画廊图
            if (!project?.gallery.isNullOrEmpty()) {
                item {
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(project!!.gallery) { url ->
                            RemoteImage(
                                url = url,
                                contentDescription = "截图",
                                modifier = Modifier
                                    .height(140.dp)
                                    .width(240.dp),
                                cornerRadius = 12.dp,
                            )
                        }
                    }
                }
            }

            // 说明
            item {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "说明",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = project?.body?.let { markdownToText(it) } ?: detail.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 适配版本 / 加载器
            item {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "适配版本",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                val versionsList = project?.gameVersions?.take(14) ?: emptyList()
                if (versionsList.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        versionsList.forEach { v ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MioGreen.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    text = v,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MioGreen,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
                project?.loaders?.let { loaders ->
                    if (loaders.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "加载器：${loaders.joinToString(" / ")}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 版本选择（不同适配）
            item {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "选择版本（含不同适配）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                when {
                    versionsLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MioGreen,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("加载版本列表…", style = MaterialTheme.typography.bodySmall)
                    }
                    versions.isEmpty() -> Text(
                        if (projectError != null) projectError!! else "该资源没有兼容你当前版本的下载项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        versions.forEachIndexed { idx, v ->
                            val sel = idx == selectedIndex
                            Surface(
                                onClick = { selectedIndex = idx },
                                color = if (sel) MioGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = if (sel) MioGreen else Color.Transparent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            v.versionNumber,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        Text(
                                            "${v.gameVersions.joinToString("/").ifEmpty { "?" }} · ${v.loaders.joinToString("/")} · ${v.datePublished}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 下载状态
            if (installing || message.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(14.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (success) MioGreen else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            done && message.isNotEmpty() -> message
                            done -> "安装完成"
                            status.isNotEmpty() -> status
                            else -> "正在下载… ${(progress * 100).toInt()}%"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 底部下载按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (installing && !done) {
                Text("下载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                androidx.compose.material3.Button(
                    onClick = { if (success) onInstalled() else start() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MioGreen),
                ) {
                    Text(if (success) "完成" else "下载安装", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // 前置组件确认弹窗：是否一起下载
    if (pendingDeps.isNotEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDeps = emptyList() },
            title = {
                Text("需要前置组件", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("该模组需要以下前置组件才能正常加载：\n\n${pendingDeps.joinToString("\n")}\n\n是否一起下载？")
            },
            confirmButton = {
                TextButton(onClick = {
                    val deps = pendingDeps
                    pendingDeps = emptyList()
                    doInstall(includeDeps = true)
                }) {
                    Text("一起下载", fontWeight = FontWeight.Bold, color = MioGreen)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pendingDeps = emptyList()
                        doInstall(includeDeps = false)
                    }) {
                        Text("仅安装模组")
                    }
                    TextButton(onClick = { pendingDeps = emptyList() }) {
                        Text("取消")
                    }
                }
            },
        )
    }
}

/** 极简 markdown → 纯文本（去标题/加粗/链接标记） */
private fun markdownToText(md: String): String {
    var s = md
    s = s.replace(Regex("#+\\s*"), "")
    s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    s = s.replace(Regex("__(.+?)__"), "$1")
    s = s.replace(Regex("\\*(.+?)\\*"), "$1")
    s = s.replace(Regex("`(.+?)`"), "$1")
    s = s.replace(Regex("!\\[.*?\\]\\(.*?\\)"), "[图]")
    s = s.replace(Regex("\\[(.+?)\\]\\(.*?\\)"), "$1")
    s = s.replace(Regex("^\\s*>\\s*", RegexOption.MULTILINE), "")
    s = s.replace(Regex("^\\s*[-+]\\s*", RegexOption.MULTILINE), "· ")
    return s.trim()
}
