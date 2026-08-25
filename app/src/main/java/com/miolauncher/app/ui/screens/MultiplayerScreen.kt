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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.miolauncher.app.data.GameLauncher
import com.miolauncher.app.data.MioRepository
import com.miolauncher.app.data.ServerHost
import com.miolauncher.app.data.ServerManager
import com.miolauncher.app.ui.components.toastOnMain
import com.miolauncher.app.ui.theme.MioGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 联机界面：服务器列表一键进服 + 本机开服（局域网）+ cpolar 隧道（公网）。
 */
@Composable
fun MultiplayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }

    // 已安装版本（进服 / 开服用）
    var installedVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        installedVersion = withContext(Dispatchers.IO) {
            MioRepository(context).loadInstalledVersions().firstOrNull()?.id
        }
    }

    // 服务器列表
    var servers by remember { mutableStateOf(ServerManager.list(context)) }
    var newName by remember { mutableStateOf("") }
    var newAddr by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(ServerManager.pendingServer(context)) }

    // 开服配置
    var port by remember { mutableIntStateOf(25565) }
    var memMb by remember { mutableIntStateOf(1024) }
    var onlineMode by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var tunnel by remember { mutableStateOf(ServerHost.tunnelAddress(context)) }
    var cpolarBase by remember { mutableStateOf(com.miolauncher.app.data.CpolarClient.DEFAULT_BASE) }
    var cpolarToken by remember { mutableStateOf(com.miolauncher.app.data.CpolarRunner.authToken(context)) }
    var cpolar by remember { mutableStateOf(com.miolauncher.app.data.CpolarRunner.status(context)) }
    var cpolarBusy by remember { mutableStateOf(false) }
    var cpolarShowLog by remember { mutableStateOf(false) }
    // 轮询服务器日志文件 + 运行状态
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }
    var running by remember { mutableStateOf(ServerHost.isRunning(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            val newLogs = withContext(Dispatchers.IO) { ServerHost.tailLog(context) }
            if (newLogs != logs) logs = newLogs
            running = withContext(Dispatchers.IO) { ServerHost.isRunning(context) }
            val s = withContext(Dispatchers.IO) { com.miolauncher.app.data.CpolarRunner.status(context) }
            if (s != cpolar) cpolar = s
            // 隧道已建立：自动保存公网地址
            val url = s.publicUrl
            if (url != null && url != ServerHost.tunnelAddress(context)) {
                ServerHost.setTunnelAddress(context, url)
                tunnel = url
            }
            kotlinx.coroutines.delay(2000)
        }
    }

    fun refresh() {
        servers = ServerManager.list(context)
        pending = ServerManager.pendingServer(context)
    }

    fun join(address: String) {
        val ver = installedVersion
        if (ver == null) {
            Toast.makeText(context, "请先安装一个游戏版本", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val ok = GameLauncher.launch(context, ver, address)
            if (!ok) Toast.makeText(context, "启动失败", Toast.LENGTH_SHORT).show()
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
            Text(
                text = "联机",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "版本：${installedVersion ?: "未安装"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 页签
        LazyRow(modifier = Modifier.padding(horizontal = 16.dp)) {
            item {
                listOf("服务器列表", "本机开服", "陶瓦联机").forEachIndexed { i, label ->
                    Surface(
                        onClick = { tab = i },
                        shape = RoundedCornerShape(20.dp),
                        color = if (tab == i) MioGreen else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (tab == i) androidx.compose.ui.graphics.Color.White
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                            fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        when (tab) {
            0 -> {
                // ---------- 服务器列表 ----------
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (pending != null) {
                        item {
                            Surface(
                                color = MioGreen.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "待连接：$pending（点「开始游戏」将自动进服）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MioGreen,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }
                    }

                    item {
                        Text("服务器列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "点「进服」会重启游戏并直接加入该服务器；也可先到「本机开服」开服后让朋友输入本机地址。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    if (servers.isEmpty()) {
                        item {
                            Text("暂无服务器，添加一个吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    items(servers, key = { it.address }) { s ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        s.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { join(s.address) }) {
                                    Icon(Icons.Filled.PlayArrow, null, tint = MioGreen, modifier = Modifier.width(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("进服", color = MioGreen)
                                }
                                IconButton(onClick = {
                                    ServerManager.remove(context, s.address)
                                    refresh()
                                }) {
                                    Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    // 添加服务器
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("添加服务器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            placeholder = { Text("名称（如：我的服务器）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = newAddr,
                            onValueChange = { newAddr = it },
                            placeholder = { Text("地址（如：192.168.1.5:25565）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.Button(
                            onClick = {
                                if (ServerManager.add(context, newName, newAddr)) {
                                    newName = ""; newAddr = ""; refresh()
                                } else Toast.makeText(context, "名称/地址不能为空", Toast.LENGTH_SHORT).show()
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MioGreen),
                        ) {
                            Icon(Icons.Filled.Add, null, modifier = Modifier.width(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("添加")
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }

            1 -> {
                // ---------- 本机开服 + cpolar ----------
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // server.jar 状态 + 下载
                    item {
                        Text("服务器程序（server.jar）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        val ready = ServerHost.serverJarReady(context)
                        Text(
                            if (ready) "已就绪（${com.miolauncher.app.data.DownloadManager.formatBytes(ServerHost.serverJar(context).length())}）"
                            else "未下载",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ready) MioGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                val ver = installedVersion
                                if (ver == null) {
                                    Toast.makeText(context, "请先安装一个游戏版本", Toast.LENGTH_SHORT).show(); return@OutlinedButton
                                }
                                downloading = true
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            ServerHost.ensureServerJar(context, ver) {}
                                        } catch (e: Exception) {
                                            toastOnMain(context, e.message ?: "下载失败")
                                        }
                                    }
                                    downloading = false
                                }
                            },
                            enabled = !downloading,
                        ) {
                            Text(if (downloading) "下载中…" else if (ready) "重新下载" else "下载 server.jar")
                        }
                    }

                    // 开服配置
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("服务器配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("端口", modifier = Modifier.width(60.dp))
                            OutlinedTextField(
                                value = port.toString(),
                                onValueChange = { port = it.toIntOrNull() ?: port },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("内存", modifier = Modifier.width(60.dp))
                            OutlinedTextField(
                                value = memMb.toString(),
                                onValueChange = { memMb = it.toIntOrNull() ?: memMb },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("MB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("在线模式（正版验证）", modifier = Modifier.weight(1f))
                            Switch(checked = onlineMode, onCheckedChange = { onlineMode = it })
                        }
                    }

                    // 启动/停止
                    item {
                        androidx.compose.material3.Button(
                            onClick = {
                                if (running) {
                                    scope.launch(Dispatchers.IO) { ServerHost.stop(context) }
                                } else {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            ServerHost.start(context, port, memMb, onlineMode)
                                        } catch (e: Exception) {
                                            toastOnMain(context, e.message ?: "启动失败")
                                        }
                                    }
                                }
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (running) MaterialTheme.colorScheme.error else MioGreen,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (running) "停止服务器" else "启动服务器")
                        }
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MioGreen.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("加入地址", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    ServerHost.shareInfo(context, port),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "局域网：同一 WiFi 下输入上面的局域网地址即可加入。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // cpolar 隧道
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("cpolar 公网隧道", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "支持本机内置运行（填 authtoken 一键开启），也可连接 Termux/电脑上已启动的 cpolar。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("authtoken（cpolar.com 免费注册获取）", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = cpolarToken,
                            onValueChange = { cpolarToken = it },
                            placeholder = { Text("粘贴 authtoken") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                com.miolauncher.app.data.CpolarRunner.setAuthToken(context, cpolarToken)
                                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                            },
                        ) { Text("保存令牌") }
                        Spacer(Modifier.height(10.dp))

                        // ---- 运行状态卡片：自动诊断，普通用户直接可读 ----
                        val st = cpolar
                        val stateRunning = st.running
                        val statusColor = when (st.state) {
                            com.miolauncher.app.data.CpolarRunner.CpState.TUNNEL_UP -> MioGreen
                            com.miolauncher.app.data.CpolarRunner.CpState.AUTH_FAILED,
                            com.miolauncher.app.data.CpolarRunner.CpState.NETWORK_ERROR,
                            -> MaterialTheme.colorScheme.error
                            com.miolauncher.app.data.CpolarRunner.CpState.STARTING -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val statusIcon = when (st.state) {
                            com.miolauncher.app.data.CpolarRunner.CpState.TUNNEL_UP -> Icons.Filled.CheckCircle
                            com.miolauncher.app.data.CpolarRunner.CpState.STARTING -> Icons.Filled.Autorenew
                            com.miolauncher.app.data.CpolarRunner.CpState.AUTH_FAILED -> Icons.Filled.Error
                            com.miolauncher.app.data.CpolarRunner.CpState.NETWORK_ERROR -> Icons.Filled.Warning
                            else -> Icons.Filled.StopCircle
                        }
                        Surface(
                            color = statusColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(statusIcon, null, tint = statusColor, modifier = Modifier.width(22.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        st.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                if (st.state == com.miolauncher.app.data.CpolarRunner.CpState.TUNNEL_UP && st.publicUrl != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "公网地址：${st.publicUrl}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = statusColor,
                                    )
                                }
                                if (st.state == com.miolauncher.app.data.CpolarRunner.CpState.AUTH_FAILED) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "请到 cpolar.com「认证」页核对 authtoken，保存后重新启动。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor,
                                    )
                                }
                                if (st.state == com.miolauncher.app.data.CpolarRunner.CpState.NETWORK_ERROR) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "cpolar 会自动重试；若持续失败请检查 WiFi/数据网络。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor,
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                TextButton(
                                    onClick = { cpolarShowLog = !cpolarShowLog },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                ) {
                                    Text(
                                        if (cpolarShowLog) "收起 cpolar 日志 ▲" else "查看 cpolar 日志 ▼",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = statusColor,
                                    )
                                }
                            }
                        }

                        if (cpolarShowLog) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                LazyColumn(
                                    modifier = Modifier.height(200.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                                ) {
                                    items(cpolar.logTail.size) { i ->
                                        Text(
                                            cpolar.logTail[i],
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (cpolar.logTail.isEmpty()) {
                                        item { Text("（暂无日志）", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    if (stateRunning) {
                                        scope.launch(Dispatchers.IO) { com.miolauncher.app.data.CpolarRunner.stop() }
                                    } else {
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                com.miolauncher.app.data.CpolarRunner.start(context, port)
                                            } catch (e: Exception) {
                                                toastOnMain(context, e.message ?: "启动 cpolar 失败")
                                            }
                                        }
                                    }
                                },
                            ) { Text(if (stateRunning) "停止内置 cpolar" else "启动内置 cpolar") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("外部 cpolar 本地 API 地址（Termux/电脑，默认 127.0.0.1:4040）", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = cpolarBase,
                            onValueChange = { cpolarBase = it },
                            placeholder = { Text("127.0.0.1:4040") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        // 一键获取公网地址（内置/外部自动判断）
                        androidx.compose.material3.Button(
                            onClick = {
                                cpolarBusy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        try {
                                            val token = com.miolauncher.app.data.CpolarRunner.authToken(context)
                                            if (token.isNotBlank()) {
                                                // 内置模式：启动后等状态变为 TUNNEL_UP（最多约 48s）
                                                val existing = com.miolauncher.app.data.CpolarRunner.status(context).publicUrl
                                                if (existing != null) {
                                                    ServerHost.setTunnelAddress(context, existing)
                                                    "✅ 公网地址：$existing"
                                                } else {
                                                    if (!com.miolauncher.app.data.CpolarRunner.isRunning()) {
                                                        com.miolauncher.app.data.CpolarRunner.start(context, port)
                                                    }
                                                    var url: String? = null
                                                    for (i in 0 until 24) {
                                                        url = com.miolauncher.app.data.CpolarRunner.status(context).publicUrl
                                                        if (url != null) break
                                                        Thread.sleep(2000)
                                                    }
                                                    if (url != null) {
                                                        ServerHost.setTunnelAddress(context, url)
                                                        "✅ 公网地址：$url"
                                                    } else {
                                                        "未拿到公网地址。请查看上方状态与日志定位问题。"
                                                    }
                                                }
                                            } else {
                                                // 外部模式
                                                val url = com.miolauncher.app.data.CpolarClient.waitForPublicUrl(cpolarBase.trim(), port)
                                                if (url != null) {
                                                    ServerHost.setTunnelAddress(context, url)
                                                    "✅ 公网地址：$url"
                                                } else {
                                                    "未找到 TCP 隧道，请确认外部 cpolar 已启动。"
                                                }
                                            }
                                        } catch (e: Exception) {
                                            "获取失败：${e.message}"
                                        }
                                    }
                                    tunnel = ServerHost.tunnelAddress(context)
                                    Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                                    cpolarBusy = false
                                }
                            },
                            enabled = !cpolarBusy,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MioGreen),
                        ) {
                            Text(if (cpolarBusy) "获取中…" else "一键获取公网地址")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "手动填公网地址（cpolar 在线隧道列表里复制）：",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = tunnel,
                            onValueChange = { tunnel = it },
                            placeholder = { Text("公网地址（如 6.tcp.cpolar.top:10577）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { ServerHost.setTunnelAddress(context, tunnel) },
                            ) { Text("保存公网地址") }
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    val info = ServerHost.shareInfo(context, port)
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    if (cm != null) {
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("mio", info))
                                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            ) { Text("复制加入信息") }
                        }
                    }

                    // 在线玩家管理
                    item {
                        val onlinePlayers by ServerHost.onlinePlayers.collectAsState()
                        var rconLoading by remember { mutableStateOf(false) }
                        var rconResult by remember { mutableStateOf("") }
                        var commandInput by remember { mutableStateOf("") }
                        var showCommandInput by remember { mutableStateOf(false) }

                        // 当服务器运行时，定时刷新在线玩家列表
                        LaunchedEffect(running) {
                            if (running) {
                                while (true) {
                                    delay(3000)
                                    ServerHost.refreshOnlinePlayers(context)
                                }
                            }
                        }

                        if (running) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "在线玩家（${onlinePlayers.size}）",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Row {
                                            IconButton(onClick = {
                                                rconLoading = true
                                                scope.launch(Dispatchers.IO) {
                                                    ServerHost.refreshOnlinePlayers(context)
                                                    val result = ServerHost.getOnlinePlayersRcon(context)
                                                    rconResult = result
                                                    rconLoading = false
                                                }
                                            }, modifier = Modifier.size(28.dp)) {
                                                if (rconLoading) {
                                                    androidx.compose.material3.CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                    )
                                                } else {
                                                    Icon(Icons.Default.Autorenew, "刷新", modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            IconButton(onClick = { showCommandInput = !showCommandInput }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Send, "命令", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    // 命令输入框
                                    if (showCommandInput) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = commandInput,
                                                onValueChange = { commandInput = it },
                                                modifier = Modifier.weight(1f).height(48.dp),
                                                placeholder = { Text("输入命令（如 kick PlayerName）", style = MaterialTheme.typography.labelSmall) },
                                                textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                singleLine = true,
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            androidx.compose.material3.FilledIconButton(
                                                onClick = {
                                                    if (commandInput.isNotBlank()) {
                                                        rconLoading = true
                                                        scope.launch(Dispatchers.IO) {
                                                            val result = ServerHost.sendRconCommand(context, commandInput)
                                                            rconResult = if (result.success) "✓ ${result.body}" else "✗ ${result.body}"
                                                            commandInput = ""
                                                            rconLoading = false
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(Icons.Default.Send, "发送", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    // RCON 结果提示
                                    if (rconResult.isNotBlank()) {
                                        Text(
                                            rconResult,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (rconResult.startsWith("✓")) MioGreen else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(bottom = 4.dp),
                                        )
                                    }

                                    // 玩家列表
                                    if (onlinePlayers.isEmpty()) {
                                        Text(
                                            "暂无在线玩家",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        onlinePlayers.forEach { player ->
                                            Row(
                                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    player,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    // 踢出按钮
                                                    androidx.compose.material3.OutlinedButton(
                                                        onClick = {
                                                            rconLoading = true
                                                            scope.launch(Dispatchers.IO) {
                                                                val result = ServerHost.kickPlayer(context, player)
                                                                rconResult = if (result.success) "✓ 已踢出 $player" else "✗ 踢出失败：${result.body}"
                                                                rconLoading = false
                                                            }
                                                        },
                                                        modifier = Modifier.height(28.dp),
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                    ) { Text("踢出", style = MaterialTheme.typography.labelSmall) }
                                                    // OP 按钮
                                                    androidx.compose.material3.OutlinedButton(
                                                        onClick = {
                                                            rconLoading = true
                                                            scope.launch(Dispatchers.IO) {
                                                                val result = ServerHost.opPlayer(context, player)
                                                                rconResult = if (result.success) "✓ 已设为管理员：$player" else "✗ 操作失败：${result.body}"
                                                                rconLoading = false
                                                            }
                                                        },
                                                        modifier = Modifier.height(28.dp),
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                    ) { Text("OP", style = MaterialTheme.typography.labelSmall) }
                                                    // 取消 OP
                                                    androidx.compose.material3.OutlinedButton(
                                                        onClick = {
                                                            rconLoading = true
                                                            scope.launch(Dispatchers.IO) {
                                                                val result = ServerHost.deopPlayer(context, player)
                                                                rconResult = if (result.success) "✓ 已取消管理员：$player" else "✗ 操作失败：${result.body}"
                                                                rconLoading = false
                                                            }
                                                        },
                                                        modifier = Modifier.height(28.dp),
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                    ) { Text("Deop", style = MaterialTheme.typography.labelSmall) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 服务器日志
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("服务器日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            LazyColumn(
                                modifier = Modifier.height(260.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                            ) {
                                items(logs.size) { i ->
                                    Text(
                                        logs[i],
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (logs.isEmpty()) {
                                    item { Text("（暂无日志）", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }

            2 -> {
                TerracottaPanel(onBack = { tab = 0 })
            }
        }
    }
}
