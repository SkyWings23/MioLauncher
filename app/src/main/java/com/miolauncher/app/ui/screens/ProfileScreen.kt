package com.miolauncher.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.miolauncher.app.MioApplication
import com.miolauncher.app.ui.components.OfflineLoginDialog
import com.miolauncher.app.ui.theme.MioGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREF_NAME = "mio_account"
private const val KEY_USERNAME = "offline_username"

@Composable
fun ProfileScreen(
    darkTheme: Boolean = true,
    onThemeChange: (Boolean) -> Unit = {},
    openLaunchSettings: Boolean = false,
    onConsumeOpenLaunchSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }
    var username by remember { mutableStateOf(prefs.getString(KEY_USERNAME, "") ?: "") }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showLaunchSettings by remember { mutableStateOf(false) }
    var showVirtualControls by remember { mutableStateOf(false) }
    var showJavaInfo by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showJoinUs by remember { mutableStateOf(false) }
    var showMsLogin by remember { mutableStateOf(false) }
    var showLangDialog by remember { mutableStateOf(false) }
    var splashEnabled by remember { mutableStateOf(com.miolauncher.app.data.UiSettingsStore.showSplash(context)) }
    var appLang by remember { mutableStateOf(com.miolauncher.app.data.UiSettingsStore.appLang(context)) }
    var pageAnimEnabled by remember { mutableStateOf(com.miolauncher.app.data.UiSettingsStore.pageAnimationEnabled(context)) }
    var pageAnimMs by remember { mutableStateOf(com.miolauncher.app.data.UiSettingsStore.pageAnimationMs(context)) }
    var showAnimDurationDialog by remember { mutableStateOf(false) }

    // 主页「启动设置」卡片 → 自动打开本页设置
    LaunchedEffect(openLaunchSettings) {
        if (openLaunchSettings) {
            showLaunchSettings = true
            onConsumeOpenLaunchSettings()
        }
    }

    // 全屏虚拟键位编辑（FCL 控制布局）
    if (showVirtualControls) {
        VirtualControlsScreen(onBack = { showVirtualControls = false })
        return
    }

    // 全屏启动设置（替换本页内容）
    if (showLaunchSettings) {
        LaunchSettingsScreen(
            settings = com.miolauncher.app.data.LaunchSettingsStore.load(context),
            memoryRange = com.miolauncher.app.data.DeviceInfo.recommendedMemoryRange(context),
            onSave = {
                com.miolauncher.app.data.LaunchSettingsStore.save(context, it)
                showLaunchSettings = false
            },
            onBack = { showLaunchSettings = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = com.miolauncher.app.ui.theme.I18n.tr("profile.title"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        AccountCard(
            username = username,
            onClick = { showLoginDialog = true },
        )
        Spacer(Modifier.height(16.dp))
        SettingsGroup(
            darkTheme = darkTheme,
            onThemeChange = onThemeChange,
            onOpenLaunchSettings = { showLaunchSettings = true },
            onOpenVirtualControls = { showVirtualControls = true },
            onOpenJavaInfo = { showJavaInfo = true },
            onOpenAbout = { showAbout = true },
            onOpenJoinUs = { showJoinUs = true },
            onOpenMsLogin = { showMsLogin = true },
            splashEnabled = splashEnabled,
            onSplashChange = {
                splashEnabled = it
                com.miolauncher.app.data.UiSettingsStore.setShowSplash(context, it)
            },
            appLang = appLang,
            onOpenLanguage = { showLangDialog = true },
            pageAnimEnabled = pageAnimEnabled,
            onPageAnimChange = {
                pageAnimEnabled = it
                com.miolauncher.app.data.UiSettingsStore.setPageAnimationEnabled(context, it)
            },
            pageAnimMs = pageAnimMs,
            onOpenAnimDuration = { showAnimDurationDialog = true },
        )
        Spacer(Modifier.height(16.dp))
        DebugJvmTest()
        Spacer(Modifier.height(24.dp))
    }

    if (showLoginDialog) {
        OfflineLoginDialog(
            title = "离线账户",
            description = "输入你的游戏用户名（3-16位字母数字）",
            confirmText = "保存",
            initialUsername = username,
            onConfirm = { name ->
                prefs.edit().putString(KEY_USERNAME, name).apply()
                username = name
                showLoginDialog = false
            },
            onDismiss = { showLoginDialog = false },
        )
    }


    if (showJavaInfo) {
        var jreStatus by remember { mutableStateOf("查询中…") }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            jreStatus = withContext(Dispatchers.IO) {
                val jre = try {
                    if (com.miolauncher.backend.JRE.isInstalled(context)) {
                        val home = com.miolauncher.backend.JRE.getJreHome(context)
                        "已安装\n路径：$home"
                    } else {
                        "未安装"
                    }
                } catch (e: Exception) {
                    "查询失败：${e.message}"
                }
                jre + "\n\n" +
                    "设备：${com.miolauncher.app.data.DeviceInfo.deviceModel()}\n" +
                    "CPU 架构：${com.miolauncher.app.data.DeviceInfo.primaryAbi()}\n" +
                    "系统：${com.miolauncher.app.data.DeviceInfo.androidVersion()}\n" +
                    "内存：${com.miolauncher.app.data.DeviceInfo.totalMemoryMb(context)} MB（建议游戏内存 ${com.miolauncher.app.data.DeviceInfo.safeGameMemoryMb(context)} MB）"
            }
        }
        AlertDialog(
            onDismissRequest = { showJavaInfo = false },
            title = { Text("Java 运行时与设备", fontWeight = FontWeight.Bold) },
            text = { Text(jreStatus) },
            confirmButton = {
                TextButton(onClick = { showJavaInfo = false }) { Text("关闭") }
            },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(com.miolauncher.app.ui.theme.I18n.tr("profile.about"), fontWeight = FontWeight.Bold) },
            text = {                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "MioLauncher v${com.miolauncher.app.BuildConfig.VERSION_NAME}\n\n自由 · 开源 · 属于你的 Minecraft 启动器\n\nGPL-3.0",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("👥 工作人员", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("· 大鸟Fish（项目发起 / 开发）")
                    Text("· opencode（技术支持）")
                    Spacer(Modifier.height(14.dp))
                    Text("📜 声明", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "本启动器与 Mojang AB / Microsoft 无任何隶属关系。" +
                        "Minecraft 及相关商标归 Mojang AB 所有。\n\n" +
                        "本启动器基于 GPL-3.0 开源许可发布，仅供学习交流使用，" +
                        "请支持正版 Minecraft。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("关闭") }
            },
        )
    }

    if (showJoinUs) {
        AlertDialog(
            onDismissRequest = { showJoinUs = false },
            title = { Text("加入我们", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("欢迎加入 MioLauncher 玩家交流群：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    JoinButton(
                        text = "交流群：1079023595",
                        color = MioGreen,
                        onClick = {
                            showJoinUs = false
                            openQQGroup(context, "1079023595")
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("遇到 Bug / 问题？请前往 Bug 提交群反馈：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    JoinButton(
                        text = "Bug 提交群：601765045",
                        color = androidx.compose.ui.graphics.Color(0xFFE07020),
                        onClick = {
                            showJoinUs = false
                            openQQGroup(context, "601765045")
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "点击上方按钮可直接跳转 QQ 加入群聊；未安装 QQ 时请复制群号搜索。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showJoinUs = false }) { Text("关闭") }
            },
        )
    }

    if (showMsLogin) {
        AlertDialog(
            onDismissRequest = { showMsLogin = false },
            title = { Text("微软登录（正版）", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "正版登录正在开发中。\n\n" +
                        "当前版本支持离线账户（离线模式 · $username）。\n\n" +
                        "正式支持正版登录需要配置 Azure 应用 Client ID，敬请期待。"
                )
            },
            confirmButton = {
                TextButton(onClick = { showMsLogin = false }) { Text("知道了") }
            },
        )
    }
    if (showLangDialog) {
        AlertDialog(
            onDismissRequest = { showLangDialog = false },
            title = { Text("界面语言", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("zh_cn" to "简体中文", "en_us" to "English").forEach { (code, label) ->
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text(label, fontWeight = if (appLang == code) FontWeight.Bold else FontWeight.Normal) },
                            trailingContent = if (appLang == code) {
                                { Icon(Icons.Filled.CheckCircle, null, tint = MioGreen) }
                            } else null,
                            modifier = Modifier.clickable {
                                appLang = code
                                com.miolauncher.app.data.UiSettingsStore.setAppLang(context, code)
                                com.miolauncher.app.ui.theme.I18n.setLocale(code)
                                showLangDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLangDialog = false }) { Text("关闭") }
            },
        )
    }

    if (showAnimDurationDialog) {
        AlertDialog(
            onDismissRequest = { showAnimDurationDialog = false },
            title = { Text("动画时长", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(150 to "快速（150ms）", 350 to "标准（350ms）", 600 to "缓慢（600ms）", 1000 to "很慢（1000ms）").forEach { (ms, label) ->
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text(label, fontWeight = if (pageAnimMs == ms) FontWeight.Bold else FontWeight.Normal) },
                            trailingContent = if (pageAnimMs == ms) {
                                { Icon(Icons.Filled.CheckCircle, null, tint = MioGreen) }
                            } else null,
                            modifier = Modifier.clickable {
                                pageAnimMs = ms
                                com.miolauncher.app.data.UiSettingsStore.setPageAnimationMs(context, ms)
                                showAnimDurationDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAnimDurationDialog = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun AccountCard(
    username: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MioGreen, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = username.ifEmpty { "离线玩家" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (username.isNotEmpty()) "离线模式 · $username" else "未登录 · 点击登录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onOpenLaunchSettings: () -> Unit,
    onOpenVirtualControls: () -> Unit,
    onOpenJavaInfo: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenJoinUs: () -> Unit,
    onOpenMsLogin: () -> Unit,
    splashEnabled: Boolean,
    onSplashChange: (Boolean) -> Unit,
    appLang: String,
    onOpenLanguage: () -> Unit,
    pageAnimEnabled: Boolean,
    onPageAnimChange: (Boolean) -> Unit,
    pageAnimMs: Int,
    onOpenAnimDuration: () -> Unit,
) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = com.miolauncher.app.ui.theme.I18n.tr("profile.launch_runtime"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
        )
        SettingRow(Icons.Filled.Settings, com.miolauncher.app.ui.theme.I18n.tr("profile.launch_settings"), com.miolauncher.app.ui.theme.I18n.tr("profile.launch_settings_sub"), onClick = onOpenLaunchSettings)
        SettingRow(Icons.Filled.VideogameAsset, com.miolauncher.app.ui.theme.I18n.tr("profile.virtual_keys"), com.miolauncher.app.ui.theme.I18n.tr("profile.virtual_keys_sub"), onClick = onOpenVirtualControls)
        SettingRow(Icons.Filled.Storage, com.miolauncher.app.ui.theme.I18n.tr("profile.java"), "21 · 点击查看", onClick = onOpenJavaInfo)
        SettingRow(Icons.Filled.Person, com.miolauncher.app.ui.theme.I18n.tr("profile.ms_login"), "开发中 · 当前使用离线模式", onClick = onOpenMsLogin)
        Spacer(Modifier.height(8.dp))
        Text(
            text = com.miolauncher.app.ui.theme.I18n.tr("profile.appearance"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
        )
        SettingRow(
            Icons.Filled.Palette,
            com.miolauncher.app.ui.theme.I18n.tr("profile.dark_mode"),
            if (darkTheme) com.miolauncher.app.ui.theme.I18n.tr("profile.dark_on") else com.miolauncher.app.ui.theme.I18n.tr("profile.dark_off"),
            trailing = {
                androidx.compose.material3.Switch(
                    checked = darkTheme,
                    onCheckedChange = onThemeChange,
                )
            },
        )
        SettingRow(
            Icons.Filled.PlayCircle,
            com.miolauncher.app.ui.theme.I18n.tr("profile.splash"),
            if (splashEnabled) com.miolauncher.app.ui.theme.I18n.tr("profile.splash_on") else com.miolauncher.app.ui.theme.I18n.tr("profile.splash_off"),
            onClick = null,
            trailing = {
                androidx.compose.material3.Switch(
                    checked = splashEnabled,
                    onCheckedChange = onSplashChange,
                )
            },
        )
        SettingRow(
            Icons.Filled.Language,
            com.miolauncher.app.ui.theme.I18n.tr("profile.language"),
            if (appLang == "en_us") "English" else "简体中文",
            onClick = onOpenLanguage,
        )
        SettingRow(
            Icons.Filled.PlayCircle,
            "页面切换动画",
            if (pageAnimEnabled) "已开启 · ${pageAnimMs}ms" else "已关闭",
            onClick = null,
            trailing = {
                androidx.compose.material3.Switch(
                    checked = pageAnimEnabled,
                    onCheckedChange = onPageAnimChange,
                )
            },
        )
        if (pageAnimEnabled) {
            SettingRow(
                Icons.Filled.Settings,
                "动画时长",
                "${pageAnimMs}ms",
                onClick = onOpenAnimDuration,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = com.miolauncher.app.ui.theme.I18n.tr("profile.info"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
        )
        SettingRow(
            Icons.Filled.Description,
            "崩溃日志",
            "查看崩溃报告 / 游戏日志（复制 · 分享 · 导出）",
            onClick = {
                val cr = com.miolauncher.app.data.CrashLogManager.collect(ctx)
                if (cr != null) {
                    com.miolauncher.app.LogViewerActivity.start(ctx, cr.title, cr.combined, "crash.txt")
                } else {
                    val f = java.io.File(com.miolauncher.app.data.MioRepository(ctx).gameDir, "logs/latest.log")
                    val content = if (f.isFile)
                        runCatching { f.readText().split('\n').takeLast(500).joinToString("\n") }.getOrDefault("（读取日志失败）")
                    else "（暂无日志）"
                    com.miolauncher.app.LogViewerActivity.start(ctx, "最新游戏日志", content, "latest.log.txt")
                }
            },
        )
        SettingRow(Icons.Filled.Person, com.miolauncher.app.ui.theme.I18n.tr("profile.about"), "v${com.miolauncher.app.BuildConfig.VERSION_NAME} · GPL-3.0", onClick = onOpenAbout)
        SettingRow(Icons.Filled.Group, "加入我们", "交流群 / Bug 提交群", onClick = onOpenJoinUs)
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MioGreen,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val MioGreen = com.miolauncher.app.ui.theme.MioGreen

@Composable
private fun DebugJvmTest() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("未测试") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "调试",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        androidx.compose.material3.OutlinedButton(
            onClick = {
                scope.launch {
                    status = "准备 JRE…"
                    withContext(Dispatchers.IO) {
                        try {
                            if (!com.miolauncher.backend.JRE.isInstalled(context)) {
                                com.miolauncher.backend.JRE.install(context) {}
                            }
                            val code = com.miolauncher.backend.JRE.launch(context, listOf("-Xms256m", "-Xmx512m", "-version"))
                            withContext(Dispatchers.Main) { status = "JVM 退出码: $code" }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { status = "失败: ${e.message}" }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("测试 JVM 启动 (-version)")
        }
        androidx.compose.material3.OutlinedButton(
            onClick = {
                scope.launch {
                    status = "构建启动命令…"
                    withContext(Dispatchers.IO) {
                        try {
                            val repo = com.miolauncher.app.data.MioRepository(context)
                            val installed = repo.loadInstalledVersions()
                            if (installed.isEmpty()) {
                                withContext(Dispatchers.Main) { status = "无已安装版本，请先下载" }
                            } else {
                                val ver = installed.first().id
                                val prefs = context.getSharedPreferences("mio_account", Context.MODE_PRIVATE)
                                val user = (prefs.getString("offline_username", "") ?: "")
                                    .ifEmpty { "Player" }
                                val cmd = com.miolauncher.backend.GameLaunch.buildCommand(
                                    context, repo.gameDir, ver, user)
                                android.util.Log.d("MioGame", "命令: $cmd")
                                withContext(Dispatchers.Main) { status = "命令构建成功(${cmd.size}项) ver=$ver" }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MioGame", "构建命令失败", e)
                            withContext(Dispatchers.Main) { status = "失败: ${e.message}" }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("构建游戏启动命令")
        }
        androidx.compose.material3.OutlinedButton(
            onClick = {
                scope.launch {
                    status = "补齐库文件…"
                    withContext(Dispatchers.IO) {
                        try {
                            val repo = com.miolauncher.app.data.MioRepository(context)
                            val installed = repo.loadInstalledVersions()
                            if (installed.isEmpty()) {
                                withContext(Dispatchers.Main) { status = "无已安装版本" }
                            } else {
                                repo.ensureLibraries(installed.first().id)
                                withContext(Dispatchers.Main) { status = "库文件补齐完成" }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MioGame", "补齐库失败", e)
                            withContext(Dispatchers.Main) { status = "失败: ${e.message}" }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("补齐库文件")
        }
        androidx.compose.material3.OutlinedButton(
            onClick = {
                scope.launch {
                    status = "准备启动…"
                    withContext(Dispatchers.IO) {
                        try {
                            val repo = com.miolauncher.app.data.MioRepository(context)
                            val installed = repo.loadInstalledVersions()
                            if (installed.isEmpty()) {
                                withContext(Dispatchers.Main) { status = "无已安装版本" }
                            } else {
                                val ver = installed.first().id
                                val prefs = context.getSharedPreferences("mio_account", Context.MODE_PRIVATE)
                                val user = (prefs.getString("offline_username", "") ?: "")
                                    .ifEmpty { "Player" }
                                repo.ensureLibraries(ver)
                                val intent = android.content.Intent(context, com.miolauncher.app.GameActivity::class.java)
                                    .putExtra("version_id", ver)
                                    .putExtra("username", user)
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MioGame", "启动游戏失败", e)
                            withContext(Dispatchers.Main) { status = "失败: ${e.message}" }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("启动游戏")
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 加入群聊按钮（可点击跳转 QQ） */
@Composable
private fun JoinButton(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
        )
    }
}

/** 通过 QQ 协议跳转到群聊（未装 QQ 时 fallback 复制群号） */
private fun openQQGroup(context: android.content.Context, groupId: String) {
    val uris = listOf(
        "mqqwpa://im/chat?chat_type=group&uin=$groupId&version=1",
        "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$groupId",
    )
    for (uri in uris) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }
    // 兜底：复制群号并提示
    try {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("群号", groupId))
        android.widget.Toast.makeText(context, "未找到 QQ，已复制群号：$groupId", android.widget.Toast.LENGTH_LONG).show()
    } catch (_: Exception) {}
}
