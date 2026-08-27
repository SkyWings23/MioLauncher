package com.miolauncher.app.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miolauncher.app.data.DeviceInfo
import com.miolauncher.app.data.LaunchSettings
import com.miolauncher.app.data.PerfProfile
import com.miolauncher.backend.Renderer
import com.miolauncher.app.ui.theme.MioGreen

/**
 * 全屏启动设置（对齐 FCL）：
 * 性能档位 / 内存 / 渲染器 / 分辨率 / 可见距离 / 模拟距离 / 帧率 / FOV / 界面缩放 / 粒子 / 语言 /
 * 垂直同步 / 显示日志 / 虚拟鼠标 / 附加 JVM 参数。
 */
@Composable
fun LaunchSettingsScreen(
    settings: LaunchSettings,
    memoryRange: IntRange,
    onSave: (LaunchSettings) -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var renderer by remember { mutableStateOf(settings.renderer) }
    var memory by remember { mutableStateOf(settings.javaMemory.coerceIn(memoryRange.first, memoryRange.last)) }
    var showLog by remember { mutableStateOf(settings.showLog) }
    var virtualMouse by remember { mutableStateOf(settings.virtualMouseEnabled) }
    var profile by remember { mutableStateOf(settings.perfProfile) }
    var resScale by remember { mutableIntStateOf(settings.resolutionScale) }
    var renderDist by remember { mutableIntStateOf(settings.renderDistance) }
    var simDist by remember { mutableIntStateOf(settings.simulationDistance) }
    var maxFps by remember { mutableIntStateOf(settings.maxFps) }
    var fov by remember { mutableIntStateOf(settings.fov) }
    var guiScale by remember { mutableIntStateOf(settings.guiScale) }
    var lang by remember { mutableStateOf(settings.lang) }
    var vsync by remember { mutableStateOf(settings.vsync) }
    var particles by remember { mutableIntStateOf(settings.particles) }
    var jvmArgs by remember { mutableStateOf(settings.extraJvmArgs) }
    var extendedMemory by remember { mutableStateOf(settings.extendedMemory) }
    var rendererMenu by remember { mutableStateOf(false) }
    // 弹窗状态
    var showExtMemoryConfirm by remember { mutableStateOf(false) }
    var showResScaleWarn by remember { mutableStateOf(false) }
    var pendingResScale by remember { mutableIntStateOf(resScale) }

    // 内存上限：开启扩展后放宽到设备物理内存的 60%
    val memoryCap = if (extendedMemory)
        com.miolauncher.app.data.DeviceInfo.extendedMemoryLimit(context)
    else memoryRange.last

    fun applyPreset(p: PerfProfile) {
        if (p == PerfProfile.CUSTOM) {
            profile = PerfProfile.CUSTOM
            return
        }
        val preset = LaunchSettings.preset(p)
        profile = p
        resScale = preset.resolutionScale
        renderDist = preset.renderDistance
        simDist = preset.simulationDistance
        maxFps = preset.maxFps
        particles = preset.particles
    }

    fun markCustom() {
        profile = PerfProfile.CUSTOM
    }

    fun save() {
        onSave(
            LaunchSettings(
                renderer = renderer,
                javaMemory = memory,
                showLog = showLog,
                virtualMouseEnabled = virtualMouse,
                perfProfile = profile,
                resolutionScale = resScale,
                renderDistance = renderDist,
                simulationDistance = simDist,
                maxFps = maxFps,
                fov = fov,
                guiScale = guiScale,
                lang = lang,
                vsync = vsync,
                particles = particles,
                extraJvmArgs = jvmArgs.trim(),
                extendedMemory = extendedMemory,
            )
        )
    }

    // 内存扩展二次确认弹窗
    if (showExtMemoryConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExtMemoryConfirm = false },
            title = { Text("开启内存扩展？", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "内存扩展允许把游戏内存调到安全上限以上（最高 ${
                        com.miolauncher.app.data.DeviceInfo.extendedMemoryLimit(context)
                    } MB）。\n\n" +
                    "风险提示：超过安全上限会占用更多系统内存，" +
                    "可能导致后台应用被回收、系统变卡，极端情况游戏可能被系统杀死（闪退）。",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    extendedMemory = true
                    showExtMemoryConfirm = false
                    markCustom()
                }) { Text("我已了解，开启", color = androidx.compose.ui.graphics.Color(0xFFE53935)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showExtMemoryConfirm = false }) { Text("取消") }
            },
        )
    }

    // 分辨率超采样提示弹窗
    if (showResScaleWarn) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showResScaleWarn = false },
            title = { Text("分辨率超过 100%？", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "超过 100% 是超采样（渲染分辨率高于屏幕），画质更清晰但会大幅增加 GPU 负载，" +
                    "在移动设备上可能导致明显掉帧。\n\n建议：如果你追求流畅，保持在 80%~100% 之间。",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    resScale = pendingResScale
                    showResScaleWarn = false
                    markCustom()
                }) { Text("仍然使用") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showResScaleWarn = false }) { Text("取消") }
            },
        )
    }

    // 渲染器选择弹窗（用居中 AlertDialog，避免滚动容器内 DropdownMenu 锚点错位）
    if (rendererMenu) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { rendererMenu = false },
            title = { Text(com.miolauncher.app.ui.theme.I18n.tr("ls.renderer"), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Renderer.entries.forEach { r ->
                        val selected = r == renderer
                        Surface(
                            onClick = { renderer = r; rendererMenu = false; markCustom() },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MioGreen.copy(alpha = 0.12f)
                            else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.label, fontWeight = FontWeight.Bold, color = if (selected) MioGreen else MaterialTheme.colorScheme.onSurface)
                                    if (selected) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Filled.CheckCircle, contentDescription = "当前", tint = MioGreen, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(
                                    text = when (r) {
                                        Renderer.NGGL4ES -> "默认，gl4es 直通系统 EGL，兼容性最好"
                                        Renderer.GL4ES -> "与默认同库但强制 GLES2，适合旧设备"
                                        Renderer.MOBILEGLUES -> "MobileGlues，Mali/Adreno 兼容最佳（1.17+）"
                                        Renderer.ZINK -> "Mesa Zink 经 Vulkan 渲染桌面 GL（Adreno 推荐，实验性）"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { rendererMenu = false }) { Text("关闭") }
            },
        )
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
            Text(
                text = com.miolauncher.app.ui.theme.I18n.tr("ls.title"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { save() }) { Text(com.miolauncher.app.ui.theme.I18n.tr("ls.save"), color = MioGreen, fontWeight = FontWeight.Bold) }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // 性能档位
            SectionTitle(com.miolauncher.app.ui.theme.I18n.tr("ls.perf_profile"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PerfProfile.entries.forEach { p ->
                    Chip(
                        text = p.label,
                        selected = profile == p,
                        onClick = { applyPreset(p) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = com.miolauncher.app.ui.theme.I18n.tr("ls.perf_hint"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            // 渲染器
            SectionTitle(com.miolauncher.app.ui.theme.I18n.tr("ls.renderer"))
            OutlinedButton(
                onClick = { rendererMenu = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(renderer.label, color = MioGreen, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "NG GL4ES 为默认且最稳定；GL4ES 兼容档适合旧设备。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            // 内存
            SectionTitle(com.miolauncher.app.ui.theme.I18n.tr("ls.memory"))
            SliderSetting(
                label = "${memory} MB",
                value = memory,
                range = memoryRange.first..memoryCap,
                onValueChange = { memory = it; markCustom() },
            )
            Text(
                "${com.miolauncher.app.ui.theme.I18n.tr("ls.memory_cap")} $memoryCap MB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 内存扩展开关（开启需二次确认；开启后才能拉到安全值以上）
            SwitchSetting(
                "内存扩展（允许超过安全上限，可能被系统回收）",
                extendedMemory,
            ) { enable ->
                if (enable) {
                    showExtMemoryConfirm = true
                } else {
                    extendedMemory = false
                    if (memory > memoryRange.last) memory = memoryRange.last
                    markCustom()
                }
            }
            Spacer(Modifier.height(4.dp))

            // 分辨率
            SectionTitle(com.miolauncher.app.ui.theme.I18n.tr("ls.resolution"))
            SliderSetting(
                label = "${resScale}%",
                value = resScale,
                range = 50..150,
                onValueChange = { newScale ->
                    // 首次跨过 100%（超采样）时弹窗提醒
                    if (newScale > 100 && resScale <= 100) {
                        pendingResScale = newScale
                        showResScaleWarn = true
                    } else {
                        resScale = newScale
                        markCustom()
                    }
                },
            )
            Spacer(Modifier.height(4.dp))

            // 可见距离
            SectionTitle(com.miolauncher.app.ui.theme.I18n.tr("ls.render_dist"))
            SliderSetting(
                label = "$renderDist 区块",
                value = renderDist,
                range = 2..32,
                onValueChange = { renderDist = it; markCustom() },
            )
            Spacer(Modifier.height(4.dp))

            // 模拟距离
            SectionTitle(com.miolauncher.app.ui.theme.I18n.tr("ls.sim_dist"))
            SliderSetting(
                label = "$simDist 区块",
                value = simDist,
                range = 5..16,
                onValueChange = { simDist = it; markCustom() },
            )
            Spacer(Modifier.height(4.dp))

            // 帧率上限
            SectionTitle(com.miolauncher.app.ui.theme.I18n.tr("ls.max_fps"))
            SliderSetting(
                label = if (maxFps <= 0) com.miolauncher.app.ui.theme.I18n.tr("ls.unlimited") else "$maxFps FPS",
                value = maxFps,
                range = 30..240,
                onValueChange = { maxFps = it; markCustom() },
                snapAtZero = true,
            )
            Spacer(Modifier.height(4.dp))

            // FOV
            SectionTitle(com.miolauncher.app.ui.theme.I18n.tr("ls.fov"))
            SliderSetting(
                label = "$fov°",
                value = fov,
                range = 30..110,
                onValueChange = { fov = it; markCustom() },
            )
            Spacer(Modifier.height(4.dp))

            // 界面缩放
            SectionTitle(com.miolauncher.app.ui.theme.I18n.tr("ls.gui_scale"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to com.miolauncher.app.ui.theme.I18n.tr("ls.auto"), 1 to "1", 2 to "2", 3 to "3", 4 to "4").forEach { (v, l) ->
                    Chip(text = l, selected = guiScale == v, onClick = { guiScale = v; markCustom() })
                }
            }
            Spacer(Modifier.height(4.dp))

            // 粒子
            SectionTitle(com.miolauncher.app.ui.theme.I18n.tr("ls.particles"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "关闭", 1 to "减少", 2 to "少量", 3 to "全部").forEach { (v, l) ->
                    Chip(text = l, selected = particles == v, onClick = { particles = v; markCustom() })
                }
            }
            Spacer(Modifier.height(4.dp))

            // 语言
            SectionTitle(com.miolauncher.app.ui.theme.I18n.tr("ls.lang"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(text = "简体中文", selected = lang == "zh_cn", onClick = { lang = "zh_cn" })
                Chip(text = "English", selected = lang == "en_us", onClick = { lang = "en_us" })
            }
            Spacer(Modifier.height(4.dp))

            // 开关
            SwitchSetting(com.miolauncher.app.ui.theme.I18n.tr("ls.vsync"), vsync) { vsync = it; markCustom() }
            SwitchSetting(com.miolauncher.app.ui.theme.I18n.tr("ls.show_log"), showLog) { showLog = it }
            SwitchSetting(com.miolauncher.app.ui.theme.I18n.tr("ls.virtual_mouse"), virtualMouse) { virtualMouse = it }
            Spacer(Modifier.height(4.dp))

            // 附加 JVM 参数
            SectionTitle(com.miolauncher.app.ui.theme.I18n.tr("ls.jvm_args"))
            OutlinedTextField(
                value = jvmArgs,
                onValueChange = { jvmArgs = it },
                placeholder = { Text("例如：-Xms512m -Dexample=1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = {
                    val d = LaunchSettings.preset(PerfProfile.LOW)
                    renderer = Renderer.NGGL4ES
                    memory = (memoryRange.last / 2).coerceIn(512, 2048)
                    showLog = true
                    virtualMouse = true
                    applyPreset(PerfProfile.LOW)
                    fov = 70; guiScale = 0; lang = "zh_cn"; vsync = false; jvmArgs = ""
                }) { Text(com.miolauncher.app.ui.theme.I18n.tr("ls.restore"), color = MaterialTheme.colorScheme.error) }
                androidx.compose.material3.Button(
                    onClick = { save() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MioGreen),
                ) {
                    Text(com.miolauncher.app.ui.theme.I18n.tr("ls.save"), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MioGreen,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )
}

@Composable
private fun SliderSetting(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    snapAtZero: Boolean = false,
) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(
        value = value.toFloat(),
        onValueChange = {
            val v = if (snapAtZero && it <= range.first + (range.last - range.first) * 0.02f) range.first - 1 else it.toInt()
            onValueChange(v.coerceIn(range.first, range.last))
        },
        valueRange = range.first.toFloat()..range.last.toFloat(),
    )
}

@Composable
private fun SwitchSetting(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MioGreen else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
