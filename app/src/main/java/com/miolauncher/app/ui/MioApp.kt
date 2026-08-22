package com.miolauncher.app.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.miolauncher.app.data.GameLauncher
import com.miolauncher.app.data.GameVersion
import com.miolauncher.app.ui.navigation.MainTab
import com.miolauncher.app.ui.screens.DownloadScreen
import com.miolauncher.app.ui.screens.HomeScreen
import com.miolauncher.app.ui.screens.MultiplayerScreen
import com.miolauncher.app.ui.screens.ProfileScreen
import com.miolauncher.app.ui.screens.ResourceScreen
import com.miolauncher.app.ui.theme.MioGreen
import com.miolauncher.app.ui.theme.MioLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MioApp() {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var darkTheme by rememberSaveable { mutableStateOf(true) }
    var downloadTab by rememberSaveable { mutableIntStateOf(0) }
    var resourceTab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    // 当前选中的游戏版本：提升到最外层持久化，跨 tab 切换/应用重启不丢失
    var selectedVersionId by rememberSaveable {
        mutableStateOf(com.miolauncher.app.data.GameVersionStore.get(context))
    }
    var openLaunchSettings by remember { mutableStateOf(false) }
    val versionListViewModel: com.miolauncher.app.viewmodel.VersionListViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    val scope = rememberCoroutineScope()
    var launching by remember { mutableStateOf(false) }
    // 启动失败原因（Java 不兼容等）→ App 内自定义弹窗完整展示，避免系统 Toast 显示不全
    var launchErrorMsg by remember { mutableStateOf<String?>(null) }
    // 上次游戏崩溃日志（启动时检测，覆盖原生崩溃进程直接死亡的场景）+ 自动诊断
    var crashReport by remember { mutableStateOf<com.miolauncher.app.data.CrashLogManager.CrashReport?>(null) }
    var crashDiagnoses by remember { mutableStateOf<List<com.miolauncher.backend.GameLogAnalyzer.Diagnosis>>(emptyList()) }
    LaunchedEffect(Unit) {
        val hasCrash = withContext(Dispatchers.IO) {
            com.miolauncher.app.data.CrashLogManager.hasUnviewedCrash(context) ||
                com.miolauncher.app.data.CrashLogManager.hasStaleCrashMarker(context)
        }
        if (hasCrash) {
            val report = withContext(Dispatchers.IO) {
                com.miolauncher.app.data.CrashLogManager.collect(context)
                    ?: com.miolauncher.app.data.CrashLogManager.buildCrashReport(context)
            }
            if (report != null) {
                crashReport = report
                // 自动分析日志并给出处理建议
                crashDiagnoses = withContext(Dispatchers.IO) {
                    com.miolauncher.backend.GameLogAnalyzer.analyzeGameLogs(context)
                }
            }
        }
    }

    fun launchVersion(version: GameVersion) {
        if (launching) return
        launching = true
        scope.launch {
            // 联机菜单设置过待连接服务器 → 一键进服（GameActivity 进服后清空）
            val server = com.miolauncher.app.data.ServerManager.pendingServer(context)
            val ok = com.miolauncher.app.data.GameLauncher.launch(context, version.id, server)
            if (!ok) {
                // App 内自定义弹窗展示完整原因（Java 不兼容等），替代系统 Toast
                val msg = try {
                    val repo = com.miolauncher.app.data.MioRepository(context)
                    repo.javaCompatibilityMessage(version.id) ?: "启动失败，请检查版本完整性"
                } catch (_: Exception) {
                    "启动失败，请检查版本完整性"
                }
                launchErrorMsg = msg
            }
            launching = false
        }
    }

    // 打开软件的加载动画（炫丽粒子 + 点击进入，4 秒兜底自动进入；可在「外观与语言」关闭）
    var showSplash by remember {
        mutableStateOf(com.miolauncher.app.data.UiSettingsStore.showSplash(context))
    }
    LaunchedEffect(Unit) {
        if (showSplash) {
            kotlinx.coroutines.delay(4000)
            showSplash = false
        }
    }

    // 首次运行：准备 Java 运行时（进度页）
    var jreState by remember { mutableStateOf<Pair<Float, String>>(0f to "准备中…") }
    var jreFailed by remember { mutableStateOf<String?>(null) }
    var jreDone by remember { mutableStateOf(false) }
    LaunchedEffect(showSplash) {
        if (showSplash) return@LaunchedEffect
        if (withContext(Dispatchers.IO) { com.miolauncher.backend.JRE.isInstalled(context) }) {
            jreDone = true
            return@LaunchedEffect
        }
        try {
            withContext(Dispatchers.IO) {
                com.miolauncher.backend.JRE.install(context) { p ->
                    jreState = p.toFloat() to "正在准备 Java 运行时…"
                }
            }
            jreState = 1f to "Java 运行时就绪"
            jreDone = true
        } catch (e: Exception) {
            jreFailed = e.message ?: "解压失败"
        }
    }

    if (showSplash) {
        SplashScreen(onEnter = { showSplash = false })
        return
    }

    if (jreFailed != null || !jreDone) {
        if (jreFailed != null) {
            JreSetupScreen(
                progress = 0f,
                message = "Java 运行时准备失败",
                error = jreFailed,
                onRetry = {
                    jreFailed = null
                    jreState = 0f to "准备中…"
                },
            )
        } else {
            JreSetupScreen(progress = jreState.first, message = jreState.second)
        }
        return
    }

    // 横屏：导航栏移到左侧（NavigationRail）；竖屏：保持底部（NavigationBar）
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    MioLauncherTheme(darkTheme = darkTheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!isLandscape) {
                    BottomNavBar(
                        currentTab = currentTab,
                        onTabSelected = { currentTab = it },
                    )
                }
            },
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (isLandscape) {
                    SideNavRail(
                        currentTab = currentTab,
                        onTabSelected = { currentTab = it },
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentTab) {
                        MainTab.HOME -> HomeScreen(
                            selectedVersionId = selectedVersionId,
                            onSelectVersion = { id ->
                                selectedVersionId = id
                                com.miolauncher.app.data.GameVersionStore.set(context, id)
                            },
                            onLaunch = ::launchVersion,
                            onNavigateToTab = { idx ->
                                currentTab = MainTab.entries[idx]
                            },
                            onOpenDownloadTab = { tab ->
                                downloadTab = tab
                                currentTab = MainTab.DOWNLOAD
                            },
                            onOpenLaunchSettings = {
                                openLaunchSettings = true
                                currentTab = MainTab.PROFILE
                            },
                        )
                        MainTab.DOWNLOAD -> DownloadScreen(
                            selectedTab = downloadTab,
                            onTabSelected = { downloadTab = it },
                            versionListViewModel = versionListViewModel,
                            selectedVersionId = selectedVersionId,
                        )
                        MainTab.RESOURCE -> ResourceScreen(
                            selectedVersionId = selectedVersionId,
                            onSelectVersion = { id ->
                                selectedVersionId = id
                                com.miolauncher.app.data.GameVersionStore.set(context, id)
                            },
                            selectedTab = resourceTab,
                            onTabSelected = { resourceTab = it },
                        )
                        MainTab.PROFILE -> ProfileScreen(
                            darkTheme = darkTheme,
                            onThemeChange = { darkTheme = it },
                            openLaunchSettings = openLaunchSettings,
                            onConsumeOpenLaunchSettings = { openLaunchSettings = false },
                        )
                        MainTab.MULTIPLAYER -> MultiplayerScreen(onBack = { currentTab = MainTab.HOME })
                    }

                    // 全局统一下载悬浮窗（所有页签可见）
                    com.miolauncher.app.ui.components.DownloadFloatingOverlay(
                        onCancelAll = {
                            versionListViewModel.cancelInstall()
                            com.miolauncher.app.data.DownloadManager.removeAll()
                        },
                         modifier = Modifier.align(Alignment.BottomEnd),
                     )
                }
            }
        }

        crashReport?.let { report ->
            CrashLogDialog(
                report = report,
                diagnoses = crashDiagnoses,
                onDismiss = {
                    crashReport = null
                    crashDiagnoses = emptyList()
                    com.miolauncher.app.data.CrashLogManager.consume(context)
                },
            )
        }

        // 启动失败/Java 不兼容 → App 内自定义弹窗（完整展示原因与处理方案）
        launchErrorMsg?.let { msg ->
            AppNoticeDialog(
                title = "无法启动该版本",
                message = msg,
                onDismiss = { launchErrorMsg = null },
            )
        }
    }
}

@Composable
private fun AppNoticeDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(22.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(18.dp))
            androidx.compose.material3.Button(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MioGreen),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("我知道了", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CrashLogDialog(
    report: com.miolauncher.app.data.CrashLogManager.CrashReport,
    diagnoses: List<com.miolauncher.backend.GameLogAnalyzer.Diagnosis> = emptyList(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Text("检测到上次游戏崩溃", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
            Spacer(Modifier.height(4.dp))
            Text(report.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            // 处理建议置顶展示（无需滚动即可看到）
            val suggestions = com.miolauncher.backend.GameLogAnalyzer.summarizeSuggestions(diagnoses)
            if (suggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MioGreen.copy(alpha = 0.10f),
                            androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        )
                        .padding(12.dp),
                ) {
                    Text("🛠 处理建议", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MioGreen)
                    Spacer(Modifier.height(6.dp))
                    suggestions.forEach { s ->
                        Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.Top) {
                            Text("• ", fontWeight = FontWeight.Bold, color = MioGreen)
                            Text(s, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // 详细诊断（滚动查看）
            if (diagnoses.isNotEmpty()) {
                Text("🔍 详细诊断", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MioGreen)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        )
                        .padding(10.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                ) {
                    Text(
                        com.miolauncher.backend.GameLogAnalyzer.formatReport(diagnoses),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    )
                    .padding(10.dp)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
                Text(
                    report.summary,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(
                    onClick = {
                        val advice = if (suggestions.isNotEmpty())
                            "===== MioLauncher 处理建议 =====\n\n" + suggestions.joinToString("\n") { "• $it" } + "\n\n"
                        else ""
                        com.miolauncher.app.LogViewerActivity.start(context, report.title, advice + report.combined, "crash.txt")
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("查看完整日志", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText(report.title, report.combined))
                        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("复制") }
                androidx.compose.material3.TextButton(
                    onClick = {
                        // 分享完整日志文件，避免大文本截断
                        val f = com.miolauncher.app.data.CrashLogManager.shareFile(
                            context, "crash-${System.currentTimeMillis()}.txt", report.combined,
                        )
                        if (f == null) {
                            Toast.makeText(context, "生成分享文件失败", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, context.packageName + ".fileprovider", f,
                        )
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, report.title)
                            putExtra(android.content.Intent.EXTRA_TEXT, report.title + "（完整日志见附件）")
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(android.content.Intent.createChooser(send, "分享崩溃日志")) }
                            .onFailure { Toast.makeText(context, "没有可用的分享应用", Toast.LENGTH_SHORT).show() }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("分享") }
                androidx.compose.material3.TextButton(
                    onClick = {
                        val f = com.miolauncher.app.data.CrashLogManager.exportReport(context, report)
                        if (f != null) Toast.makeText(context, "已导出到：${f.absolutePath}", Toast.LENGTH_LONG).show()
                        else Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("导出") }
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("忽略", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun SplashScreen(onEnter: () -> Unit) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "splash")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(14000, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "t",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val blink by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "blink",
    )

    // 粒子（上升的方块 / 光点）
    val particles = remember {
        List(26) { i ->
            SplashParticle(
                seed = i.toFloat(),
                startY = Random.nextFloat(),
                startX = Random.nextFloat(),
                speed = 0.06f + Random.nextFloat() * 0.1f,
                radius = 3f + Random.nextFloat() * 9f,
                isBlock = i % 2 == 0,
                hue = Random.nextFloat(),
            )
        }
    }

    // 提示文字延迟出现
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1200); showHint = true }

    val colorA = androidx.compose.ui.graphics.Color(0xFF0B3D0B)
    val colorB = androidx.compose.ui.graphics.Color(0xFF1B5E20)
    val glow = androidx.compose.ui.graphics.Color(0xFF66BB6A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { onEnter() }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // 动态渐变背景
            val shift = t * size.height * 0.25f
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to colorA,
                    1f to colorB,
                    startY = -shift,
                    endY = size.height * 0.8f - shift,
                ),
            )
    // 粒子
    particles.forEach { p ->
        val y = (p.startY * size.height - t * p.speed * size.height * 1.4f)
            .mod(size.height + 100f) - 50f
        val sway = kotlin.math.sin(t * 6.28f + p.seed * 1.7f) * 40f
        val x = p.startX * size.width + sway
        val alpha = (0.35f + 0.55f * ((kotlin.math.sin(t * 4f + p.seed * 3f) + 1f) / 2f))
        val col = if (p.isBlock) {
            androidx.compose.ui.graphics.Color(
                red = 0.42f, green = 0.72f, blue = 0.36f, alpha = alpha,
            )
        } else {
            glow.copy(alpha = alpha)
        }
        if (p.isBlock) {
            drawRect(
                color = col,
                topLeft = Offset(x, y),
                size = Size(p.radius, p.radius),
            )
        } else {
            drawCircle(
                color = col,
                radius = p.radius,
                center = Offset(x, y),
            )
        }
    }
        }

        // 中央 Logo
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                            rotationZ = kotlin.math.sin(t * 6.28f * 0.5f) * 4f
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // 光晕
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(
                                glow.copy(alpha = 0.18f + 0.1f * blink),
                                androidx.compose.foundation.shape.CircleShape,
                            ),
                    )
                    androidx.compose.material3.Text(
                        text = "M",
                        fontSize = 110.sp,
                        fontWeight = FontWeight.Bold,
                        color = MioGreen,
                        modifier = Modifier
                            .background(Color(0xFF163216), androidx.compose.foundation.shape.CircleShape)
                            .padding(horizontal = 30.dp, vertical = 18.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                androidx.compose.material3.Text(
                    text = "MioLauncher",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.Text(
                    text = "自由 · 开源 · 属于你的 Minecraft 启动器",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }

        // 点击进入提示
        if (showHint) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                androidx.compose.material3.Text(
                    text = "点击屏幕进入游戏 ▸▸",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = blink),
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.35f), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private data class SplashParticle(
    val seed: Float,
    val startY: Float,
    val startX: Float,
    val speed: Float,
    val radius: Float,
    val isBlock: Boolean,
    val hue: Float,
)

@Composable
private fun JreSetupScreen(
    progress: Float,
    message: String,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B3D0B)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                color = MioGreen,
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp,
            )
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Text(
                text = message,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MioGreen,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Text(
                text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            if (error != null) {
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Text(
                    text = error,
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                if (onRetry != null) {
                    androidx.compose.material3.Button(
                        onClick = onRetry,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MioGreen),
                    ) {
                        androidx.compose.material3.Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tabLabel(tab)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MioGreen,
                    selectedTextColor = MioGreen,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun SideNavRail(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        header = {
            Text(
                text = "Mio",
                color = MioGreen,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
        },
    ) {
        MainTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tabLabel(tab)) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MioGreen,
                    selectedTextColor = MioGreen,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun tabLabel(tab: MainTab): String = when (tab) {
    MainTab.HOME -> com.miolauncher.app.ui.theme.I18n.tr("nav.home")
    MainTab.DOWNLOAD -> com.miolauncher.app.ui.theme.I18n.tr("nav.download")
    MainTab.RESOURCE -> com.miolauncher.app.ui.theme.I18n.tr("nav.resource")
    MainTab.PROFILE -> com.miolauncher.app.ui.theme.I18n.tr("nav.profile")
    MainTab.MULTIPLAYER -> "联机"
}
