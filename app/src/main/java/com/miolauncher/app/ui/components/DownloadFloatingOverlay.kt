package com.miolauncher.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.miolauncher.app.data.DownloadManager
import com.miolauncher.app.ui.theme.MioGreen
import kotlin.math.roundToInt

/**
 * 全局下载悬浮窗：可拖动的进度球 + 点击展开面板。
 * 面板展示每个任务的「已下载 / 总数 / 当前速度 / 文件进度」。
 */
@Composable
fun DownloadFloatingOverlay(
    onCancelAll: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tasks by DownloadManager.tasks.collectAsState()
    if (tasks.isEmpty()) return

    var showPanel by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(-20f) }
    var offsetY by remember { mutableStateOf(-80f) }
    var dragging by remember { mutableStateOf(false) }

    // 屏幕尺寸（用于限制悬浮球不超出屏幕）
    val configuration = LocalConfiguration.current
    val densityPx = LocalDensity.current.density
    val screenW = configuration.screenWidthDp * densityPx
    val screenH = configuration.screenHeightDp * densityPx
    val ball = 56 * densityPx
    val margin = 8 * densityPx
    val minX = ball - screenW + margin
    val minY = ball - screenH + margin

    val active = tasks.filter { !it.isDone }
    val overall = if (active.isNotEmpty()) {
        active.map { it.percent.coerceIn(0f, 1f) }.average().toFloat()
    } else 1f

    Box(modifier = modifier) {
        // 可拖动悬浮球
        Surface(
            onClick = { if (!dragging) showPanel = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(minX, -margin)
                            offsetY = (offsetY + dragAmount.y).coerceIn(minY, -margin)
                        },
                    )
                }
                .size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            border = BorderStroke(2.dp, MioGreen),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { overall.coerceIn(0f, 1f) },
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 3.dp,
                    color = MioGreen,
                )
                Icon(
                    if (active.isEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Download,
                    contentDescription = "下载进度",
                    modifier = Modifier.size(18.dp),
                    tint = if (active.isEmpty()) MioGreen else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // 面板
        if (showPanel) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showPanel = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "下载任务",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            if (active.isNotEmpty()) {
                                TextButton(onClick = { onCancelAll(); showPanel = false }) {
                                    Text("全部取消", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            IconButton(onClick = { showPanel = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "关闭")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        tasks.forEach { t ->
                            DownloadTaskRow(task = t, onRemove = { DownloadManager.remove(t.id) })
                            Spacer(Modifier.height(10.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskRow(
    task: DownloadManager.Task,
    onRemove: () -> Unit,
) {
    val done = task.isDone
    var expanded by remember(task.id) { mutableStateOf(false) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { expanded = !expanded },
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.currentFile.isNotEmpty()) {
                    Text(
                        text = task.currentFile,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (done) {
                Icon(
                    if (task.error == null) Icons.Filled.CheckCircle else Icons.Filled.Info,
                    contentDescription = null,
                    tint = if (task.error == null) MioGreen else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
                }
            } else {
                Text(
                    text = if (expanded) "收起 ▲" else "详情 ▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = MioGreen,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // 大进度条（展开时加高）
        LinearProgressIndicator(
            progress = { task.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(if (expanded) 10.dp else 5.dp),
            color = when {
                done && task.error == null -> MioGreen
                done -> MaterialTheme.colorScheme.error
                else -> MioGreen
            },
        )
        Spacer(Modifier.height(4.dp))
        Row {
            // 已下载 / 总数
            Text(
                text = if (task.total > 0) {
                    "${DownloadManager.formatBytes(task.downloaded)} / ${DownloadManager.formatBytes(task.total)}"
                } else {
                    "${(task.progress * 100).toInt()}%"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (!done) {
                Text(
                    text = if (task.total > 0) "${DownloadManager.formatBytes(task.speed.toLong())}/s"
                    else "${String.format("%.1f", task.speed)}%/s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MioGreen,
                )
            } else {
                Text(
                    text = task.error ?: "完成",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.error != null) MaterialTheme.colorScheme.error else MioGreen,
                )
            }
        }
        if (!done) {
            Row {
                // 剩余量 + 预计剩余时间
                Text(
                    text = if (task.total > 0) "剩余 ${DownloadManager.formatBytes(task.remaining)} · 预计 ${task.etaText}"
                    else "预计 ${task.etaText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "文件 ${task.filesDone}/${task.filesTotal}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                text = "文件 ${task.filesDone}/${task.filesTotal}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 展开详情：各文件进度（来自 InstallProgress.items 会单独展示，此处展示任务自身进度）
        if (expanded && task.total > 0 && !done) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "速度 ${DownloadManager.formatBytes(task.speed.toLong())}/s · 剩余 ${DownloadManager.formatBytes(task.remaining)}",
                style = MaterialTheme.typography.labelSmall,
                color = MioGreen,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
