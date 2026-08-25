package com.miolauncher.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miolauncher.app.data.PatchManager
import com.miolauncher.app.data.ThemeStore
import com.miolauncher.app.ui.theme.MioLauncherTheme
import org.json.JSONObject

/**
 * 全屏下载界面（启动器同款主题）：
 * - mode="patch"：补丁热更新（显示补丁编号/进度/速度，完成后可重启启动器）
 * - mode="apk"：完整 APK 更新（显示新版本信息，下载完成后调起系统安装器覆盖安装）
 */
class PatchDownloadActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "patch"
        setContent {
            MioLauncherTheme(darkTheme = ThemeStore.isDark(this)) {
                PatchDownloadScreen(activity = this, mode = mode)
            }
        }
    }

    companion object {
        /** 重启当前 App：结束后台任务并重新打开 MainActivity。 */
        fun restartApp(context: Context) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        context.startActivity(launchIntent)
                    } catch (_: Exception) {
                    }
                }, 500)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        /** 调起系统安装器安装下载好的 APK（FileProvider 暴露，授予读取权限）。 */
        fun installApk(context: Context, apkFile: java.io.File): Boolean {
            return try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    apkFile,
                )
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                android.util.Log.e("PatchDL", "installApk failed: ${e.message}")
                false
            }
        }
    }
}

private data class PatchItem(
    val target: String,
    val desc: String,
    val version: String,
)

@Composable
private fun PatchDownloadScreen(activity: PatchDownloadActivity, mode: String = "patch") {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isApk = mode == "apk"
    // 0=更新日志确认 1=下载中 2=完成 3=失败（APK 模式）
    // 补丁模式：0=加载清单 1=下载中 2=完成 3=失败
    var phase by remember { mutableIntStateOf(0) }
    var patches by remember { mutableStateOf<List<PatchItem>>(emptyList()) }
    var appUpdate by remember { mutableStateOf<com.miolauncher.app.data.AppUpdate?>(null) }
    var downloadedApk by remember { mutableStateOf<java.io.File?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var downloaded by remember { mutableLongStateOf(0L) }
    var total by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableLongStateOf(0L) }
    var percent by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    var showExitDialog by remember { mutableStateOf(false) }

    val handler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(Unit) {
        if (isApk) {
            // ---- APK 更新模式：优先用启动时缓存的更新信息，没有再联网拉取 ----
            val update = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    com.miolauncher.app.data.PatchManager.fetchAppUpdateCached()
                        ?: com.miolauncher.app.data.PatchManager.fetchAppUpdate(context)
                } catch (_: Throwable) {
                    null
                }
            }
            if (update == null) {
                phase = 3
                error = "未发现可用的新版本"
                return@LaunchedEffect
            }
            appUpdate = update
            UpdateDownloadService.reset()
            // phase 保持 0：展示更新日志，等待用户点「开始更新」
        } else {
            // ---- 补丁热更新模式 ----
            val manifest = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    PatchManager.fetchManifest(context)
                } catch (_: Throwable) {
                    emptyList()
                }
            }
            val pending = PatchManager.pendingPatches(context, manifest)
            if (pending.isEmpty()) {
                phase = 2
                return@LaunchedEffect
            }
            patches = pending.map {
                PatchItem(
                    target = it.optString("target"),
                    desc = it.optString("desc", "补丁更新"),
                    version = it.optString("version", ""),
                )
            }
            phase = 1
            val jsonPatches = pending
            val okCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                PatchManager.applyPatches(
                    context,
                    jsonPatches,
                    onPatchStarted = { idx, count, _ ->
                        handler.post {
                            currentIndex = idx
                            downloaded = 0
                            total = 0
                            speed = 0
                            percent = 0
                        }
                    },
                    onProgress = { d, t, sp, pct ->
                        handler.post {
                            downloaded = d
                            total = t
                            speed = sp
                            percent = pct
                        }
                    },
                )
            }
            handler.post {
                phase = if (okCount == jsonPatches.size && jsonPatches.isNotEmpty()) 2 else if (okCount == 0) 3 else 2
                error = if (okCount < jsonPatches.size) "部分补丁下载失败" else ""
            }
        }
    }

    // APK 模式：轮询后台下载 Service 的进度
    LaunchedEffect(isApk, phase) {
        if (isApk && phase == 1) {
            while (true) {
                val s = UpdateDownloadService.state
                downloaded = s.downloaded
                total = s.total
                speed = s.speed
                percent = s.percent
                if (s.finished) {
                    if (s.cancelled) {
                        UpdateDownloadService.reset()
                        phase = 0
                    } else if (s.success) {
                        downloadedApk = s.file
                        phase = 2
                    } else {
                        error = s.error.ifBlank { "下载失败，请检查网络后重试" }
                        phase = 3
                    }
                    break
                }
                kotlinx.coroutines.delay(300)
            }
        }
    }

    // 若从悬浮窗恢复（resume=true）且下载已完成，直接进入完成态
    LaunchedEffect(Unit) {
        val resume = activity.intent.getBooleanExtra("resume", false)
        if (isApk && resume && UpdateDownloadService.state.finished) {
            val s = UpdateDownloadService.state
            if (s.success && s.file != null) {
                downloadedApk = s.file
                downloaded = s.total
                total = s.total
                percent = 100
                phase = 2
            }
        }
    }

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isApk) "启动器更新" else "补丁更新中心",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                // 下载中显示退出按钮
                if (isApk && phase == 1) {
                    Button(
                        onClick = { showExitDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text("退出", fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    isApk && phase == 0 -> "新版本更新说明"
                    isApk && phase == 1 -> "正在下载安装包"
                    isApk && phase == 2 -> "下载完成"
                    isApk -> "检测到新版本"
                    else -> "正在接收来自服务器的修复补丁"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isApk) {
                    // APK 更新卡片
                    val up = appUpdate
                    if (up != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("v", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "MioLauncher ${up.versionName}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "新版本 · 完整更新",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                // 更新说明（日志）：下载前展示修复内容
                                if (up.desc.isNotBlank()) {
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        "本次更新内容",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    ChangelogText(up.desc)
                                }
                                if (phase == 1) {
                                    Spacer(Modifier.height(14.dp))
                                    LinearProgressIndicator(
                                        progress = { percent / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        val sizeText = if (total > 0)
                                            "${fmtSize(downloaded)} / ${fmtSize(total)}"
                                        else
                                            fmtSize(downloaded)
                                        val speedText = if (speed > 0) fmtSpeed(speed) else "…"
                                        Text(sizeText, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(speedText, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    patches.forEachIndexed { idx, p ->
                        val isCurrent = idx == currentIndex && phase == 1
                        val isDone = (phase == 1 && idx < currentIndex) || phase == 2
                        PatchCard(
                            index = idx + 1,
                            total = patches.size,
                            item = p,
                            isCurrent = isCurrent,
                            isDone = isDone,
                            downloaded = if (isCurrent) downloaded else 0,
                            totalBytes = if (isCurrent) total else 0,
                            speed = if (isCurrent) speed else 0,
                            percent = if (isCurrent) percent else if (isDone) 100 else 0,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            when {
                isApk && phase == 0 -> {
                    // 更新日志确认：点「开始更新」才下载
                    Button(
                        onClick = {
                            val up = appUpdate ?: return@Button
                            phase = 1
                            UpdateDownloadService.start(context, up)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text("开始更新", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                phase == 1 -> {
                    Text(
                        if (isApk) "正在下载新版本安装包…" else "正在下载第 $currentIndex/${patches.size} 个补丁",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                phase == 2 -> {
                    if (isApk) {
                        // APK 更新：调起系统安装器
                        Button(
                            onClick = {
                                val f = downloadedApk
                                if (f != null) PatchDownloadActivity.installApk(context, f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Text("立即安装", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "下载完成，点击「立即安装」将打开系统安装器覆盖更新",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Button(
                            onClick = { PatchDownloadActivity.restartApp(context) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Text("重启启动器", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                phase == 3 -> {
                    Text(
                        error.ifBlank { "下载失败，请检查网络后重试" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            phase = 0
                            patches = emptyList()
                            appUpdate = null
                            downloadedApk = null
                            UpdateDownloadService.reset()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("重试")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 退出确认弹窗：退出 / 最小化（悬浮窗继续下载）
    if (showExitDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("更新下载中") },
            text = {
                Text(
                    "当前正在下载更新（$percent%）。\n\n" +
                        "「最小化」：下载在后台继续，可通过悬浮窗回到本界面。\n" +
                        "「退出」：停止下载并返回。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        // 最小化：后台下载 + 悬浮窗
                        val ctx = context.applicationContext
                        if (UpdateFloatService.canShow(ctx)) {
                            UpdateFloatService.show(ctx)
                        }
                        activity.finish()
                    },
                ) {
                    Text("最小化")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        // 退出：取消下载并返回
                        UpdateDownloadService.cancel(context)
                        UpdateDownloadService.reset()
                        activity.finish()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("退出")
                }
            },
        )
    }
}

/** 更新说明文本：把 desc 里的逗号/分号分隔的条目转成带符号的列表。 */
@Composable
private fun ChangelogText(desc: String) {
    val items = desc.split("\n", "+", "，", ",", "；", ";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(Modifier.fillMaxWidth()) {
                Text("• ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    item,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PatchCard(
    index: Int,
    total: Int,
    item: PatchItem,
    isCurrent: Boolean,
    isDone: Boolean,
    downloaded: Long,
    totalBytes: Long,
    speed: Long,
    percent: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 补丁编号徽章
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isDone) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isDone) "✓" else "$index",
                        color = if (isDone) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "补丁 #$index · 版本 ${item.version.ifBlank { "?" }}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        item.desc,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (isDone) {
                    Text("已安装", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                } else if (isCurrent) {
                    Text("$percent%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            if (isCurrent) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val sizeText = if (totalBytes > 0)
                        "${fmtSize(downloaded)} / ${fmtSize(totalBytes)}"
                    else
                        fmtSize(downloaded)
                    val speedText = if (speed > 0) fmtSpeed(speed) else "…"
                    Text(sizeText, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(speedText, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun fmtSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / 1073741824.0)
    bytes >= 1024L * 1024 -> String.format("%.2f MB", bytes / 1048576.0)
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun fmtSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1024L * 1024 -> String.format("%.1f MB/s", bytesPerSec / 1048576.0)
    bytesPerSec >= 1024L -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
    else -> "$bytesPerSec B/s"
}
