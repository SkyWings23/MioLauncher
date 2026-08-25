package com.miolauncher.app.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miolauncher.app.data.terracotta.TerracottaManager
import com.miolauncher.app.data.terracotta.TerracottaState
import com.miolauncher.app.data.terracotta.TerracottaVpnService
import com.miolauncher.app.ui.theme.MioGreen
import kotlinx.coroutines.launch

/**
 * 陶瓦联机（Terracotta）面板：创建房间 / 加入房间，基于 EasyTier 虚拟局域网。
 */
@Composable
fun TerracottaPanel(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var initialized by remember { mutableStateOf(false) }
    var initError by remember { mutableStateOf<String?>(null) }
    var roomInput by remember { mutableStateOf("") }
    var busying by remember { mutableStateOf(false) }

    val state by TerracottaManager.state.collectAsState()
    val vpnPrepared = remember { mutableStateOf(TerracottaVpnService.isPrepared(context)) }

    // VPN 授权（系统对话框）
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        vpnPrepared.value = TerracottaVpnService.isPrepared(context)
        if (vpnPrepared.value) {
            Toast.makeText(context, "VPN 已授权", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "未授权 VPN，陶瓦联机无法工作", Toast.LENGTH_SHORT).show()
        }
    }

    // 初始化 Terracotta（首次）
    LaunchedEffect(Unit) {
        if (!TerracottaManager.initialized.value) {
            try {
                TerracottaManager.initialize(context) {
                    // native 请求 VpnService：确保已授权，启动 VPN 服务
                    if (TerracottaVpnService.isPrepared(context)) {
                        context.startService(Intent(context, TerracottaVpnService::class.java))
                    }
                }
                initialized = true
            } catch (e: Exception) {
                initError = e.message ?: "初始化失败"
            }
        } else {
            initialized = true
        }
    }

    fun ensureVpn() {
        if (!TerracottaVpnService.isPrepared(context)) {
            val intent = TerracottaVpnService.prepareIntent(context)
            if (intent != null) {
                vpnLauncher.launch(intent)
            }
        }
    }

    fun createRoom() {
        ensureVpn()
        busying = true
        scope.launch {
            try {
                val player = playerName(context)
                TerracottaManager.setScanning(player)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "创建房间失败", Toast.LENGTH_SHORT).show()
            }
            busying = false
        }
    }

    fun joinRoom() {
        val room = roomInput.trim()
        if (room.isEmpty()) {
            Toast.makeText(context, "请输入房间号", Toast.LENGTH_SHORT).show()
            return
        }
        ensureVpn()
        busying = true
        scope.launch {
            try {
                val player = playerName(context)
                val ok = TerracottaManager.setGuesting(room, player)
                if (!ok) {
                    Toast.makeText(context, "房间号无效", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "加入失败", Toast.LENGTH_SHORT).show()
            }
            busying = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            "陶瓦联机",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "基于 EasyTier 的虚拟局域网联机：创建房间生成房间号，朋友输入房间号即可加入，无需公网 IP。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (initError != null) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "初始化失败：$initError\n设备可能不支持（需 Android 8+，arm64/armv7/x86_64）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                )
            }
            return@Column
        }

        if (!initialized) {
            Spacer(Modifier.height(12.dp))
            Text("正在初始化陶瓦联机…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        Spacer(Modifier.height(12.dp))

        // VPN 状态
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (vpnPrepared.value) Icons.Filled.CheckCircle else Icons.Filled.StopCircle,
                contentDescription = null,
                tint = if (vpnPrepared.value) MioGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.width(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (vpnPrepared.value) "本地 VPN 已授权" else "本地 VPN 未授权",
                style = MaterialTheme.typography.bodySmall,
                color = if (vpnPrepared.value) MioGreen else MaterialTheme.colorScheme.error,
            )
            if (!vpnPrepared.value) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { ensureVpn() }) { Text("去授权") }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 当前状态
        val currentState = state
        // 扫描超时提示（收集 scanTimeout 流，显示到用户下次操作）
        var scanTimedOut by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            TerracottaManager.scanTimeout.collect { timedOut ->
                if (timedOut) scanTimedOut = true
            }
        }
        LaunchedEffect(currentState) {
            if (currentState is TerracottaState.HostScanning) {
                scanTimedOut = false
            }
        }
        val statusText = when (currentState) {
            is TerracottaState.Waiting, is TerracottaState.Bootstrap -> "空闲，等待操作"
            is TerracottaState.HostScanning -> "正在创建房间…"
            is TerracottaState.HostStarting -> "房间启动中…"
            is TerracottaState.HostOK -> "房间已创建"
            is TerracottaState.GuestConnecting -> "正在加入房间…"
            is TerracottaState.GuestStarting -> "连接建立中…"
            is TerracottaState.GuestOK -> "已加入房间"
            is TerracottaState.Exception -> "连接异常"
            else -> "等待中…"
        }
        Surface(
            color = MioGreen.copy(alpha = 0.1f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(statusText, fontWeight = FontWeight.Bold, color = MioGreen)

                when (currentState) {
                    is TerracottaState.HostScanning -> {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "正在扫描你手机上的 Minecraft 服务器…\n" +
                                "请先启动游戏并进入世界（多人→对局域网开放），或在「本机开服」页开服，" +
                                "扫描到服务器后就会生成房间号。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { TerracottaManager.setWaiting() }) {
                            Text("取消扫描")
                        }
                    }

                    is TerracottaState.HostOK -> {
                        Spacer(Modifier.height(6.dp))
                        Text("房间号：", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                currentState.getCode(),
                                style = MaterialTheme.typography.titleLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MioGreen,
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                if (cm != null) {
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("terracotta", currentState.getCode()))
                                    Toast.makeText(context, "房间号已复制", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.width(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("复制")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "把房间号发给朋友，朋友输入该房间号即可加入你的房间。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is TerracottaState.GuestOK -> {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "已连入房间，服务器地址：${currentState.getUrl()}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "在游戏内「多人游戏 → 直接连接」输入上面的地址即可加入。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is TerracottaState.Exception -> {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "错误类型：${currentState.getType()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    else -> {}
                }
            }
        }

        if (scanTimedOut) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "未找到正在运行的 Minecraft 服务器",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "创建房间需要先有本机服务器在运行。请按顺序操作：\n" +
                            "1. 启动游戏并进入一个世界\n" +
                            "2. 按 Esc 打开暂停菜单\n" +
                            "3. 点「对局域网开放」\n" +
                            "4. 再回到这里点「创建房间」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 创建房间
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("创建房间（开黑，朋友来你世界）", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "点「创建房间」会扫描你设备上正在运行的 Minecraft 服务器（游戏内开好世界→多人→对局域网开放，端口默认 25565）。" +
                        "扫描到后自动生成房间号，把房间号发给朋友即可。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Button(
                    onClick = { createRoom() },
                    enabled = !busying,
                    colors = ButtonDefaults.buttonColors(containerColor = MioGreen),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.GroupAdd, null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (busying) "处理中…" else "创建房间")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 加入房间
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("加入房间（朋友发你的房间号）", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = roomInput,
                    onValueChange = { roomInput = it },
                    placeholder = { Text("输入房间号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Button(
                    onClick = { joinRoom() },
                    enabled = !busying && roomInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MioGreen),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("加入房间")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 操作指南
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("📖 操作指南（与 FCL 互通）", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "· 创建房间（开黑）：\n" +
                        "  1. 游戏内进入世界\n" +
                        "  2. 按 Esc 打开暂停菜单\n" +
                        "  3. 点「对局域网开放」\n" +
                        "  4. 回到这里点「创建房间」\n" +
                        "  5. 生成房间号后点「复制」发给朋友\n\n" +
                        "· 加入房间：\n" +
                        "  输入朋友发的房间号（U/XXXX-...）→ 点「加入房间」，无需自己开服务器\n\n" +
                        "· 加入成功后：\n" +
                        "  游戏内「多人游戏 → 直接连接」输入显示的地址即可进入\n\n" +
                        "· FCL 互通：\n" +
                        "  FCL 启动器的陶瓦联机房间号与本启动器通用，可互相加入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = { TerracottaManager.setWaiting() }) {
            Text("回到空闲状态")
        }
    }
}

private fun playerName(context: Context): String? {
    return try {
        val prefs = context.getSharedPreferences("mio_profile", Context.MODE_PRIVATE)
        prefs.getString("username", null)
    } catch (_: Exception) {
        null
    }
}
