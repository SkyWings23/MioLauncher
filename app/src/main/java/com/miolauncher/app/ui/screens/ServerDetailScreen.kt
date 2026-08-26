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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miolauncher.app.data.DiscoverReview
import com.miolauncher.app.data.DiscoverServer
import com.miolauncher.app.data.DiscoverStore
import com.miolauncher.app.data.GameLauncher
import com.miolauncher.app.data.MioRepository
import com.miolauncher.app.data.ServerDiscoveryApi
import com.miolauncher.app.ui.theme.MioGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ServerDetailScreen(
    server: DiscoverServer,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dev = DiscoverStore.deviceId(context)
    var reviews by remember { mutableStateOf<List<DiscoverReview>>(emptyList()) }
    var loadingReviews by remember { mutableStateOf(true) }
    var showRateDialog by remember { mutableStateOf(false) }
    var rateGood by remember { mutableStateOf(true) }
    var comment by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var pickerVersions by remember { mutableStateOf<List<String>>(emptyList()) }
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(server.id) {
        loadingReviews = true
        reviews = ServerDiscoveryApi.fetchReviews(context, listOf(server.id), limit = 20)[server.id] ?: emptyList()
        loadingReviews = false
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("服务器详情", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefresh) { Text("刷新") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // 头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServerLogoBig(server)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(server.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        server.badge.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                it, fontSize = 11.sp, color = Color.White,
                                modifier = Modifier
                                    .background(MioGreen, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text("● ${server.online} 人在线", fontSize = 13.sp, color = MioGreen)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "地址：${server.address}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            // 标签
            Row {
                server.tags.forEach { t ->
                    Text(
                        t, fontSize = 12.sp, color = Color(0xFF9ECBFF),
                        modifier = Modifier
                            .background(Color(0xFF263043), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            // 好评率
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("好评率", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(6.dp))
                    if (server.hasRating) {
                        val rate = server.rating ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                LinearProgressIndicator(
                                    progress = { rate / 100f },
                                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                                    color = MioGreen,
                                    trackColor = Color.Black.copy(alpha = 0.2f),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("$rate%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MioGreen)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${server.good} 好评 / ${server.bad} 差评 · 共 ${server.reviewCount} 条评价 · ${server.clicks} 次点击",
                            fontSize = 11.sp,
                            color = Color.Gray,
                        )
                    } else {
                        Text("新服暂无评分（少于 10 条评价）", fontSize = 13.sp, color = Color.Gray)
                        Spacer(Modifier.height(2.dp))
                        Text("登录进入后即可参与评价", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            // 服主寄语（置顶展示）
            if (server.slogan.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF3A2A10),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("👑 服主寄语", fontSize = 12.sp, color = Color(0xFFFFD07A))
                        Spacer(Modifier.height(4.dp))
                        Text(server.slogan, fontSize = 14.sp, color = Color(0xFFFFF3D6))
                    }
                }
            }

            if (server.desc.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(server.desc, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
            Text("玩家评价", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            when {
                loadingReviews -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MioGreen)
                }
                reviews.isEmpty() -> Text("暂无评价，快来抢沙发", color = Color.Gray, fontSize = 13.sp)
                else -> reviews.forEach { r ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (r.good) "👍" else "👎", fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (r.comment.isNotBlank()) r.comment else "（用户评价，无文字）",
                                    fontSize = 13.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(90.dp))
        }

        // 底部操作栏
        Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { rateGood = true; comment = ""; showRateDialog = true }) {
                    Text("👍 好评", color = MioGreen)
                }
                TextButton(onClick = { rateGood = false; comment = ""; showRateDialog = true }) {
                    Text("👎 差评", color = Color(0xFFF87171))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        DiscoverStore.addTag(context, server.tags)
                        scope.launch(Dispatchers.IO) {
                            ServerDiscoveryApi.track(context, dev, server.id, "join", server.tags)
                        }
                        launchDetailJoin(context, server) { pickerVersions = it; showPicker = true }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE8590C),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("🚀 立即进入")
                }
            }
        }
    }

    // 评价弹窗
    if (showRateDialog) {
        AlertDialog(
            onDismissRequest = { if (!submitting) showRateDialog = false },
            title = { Text(if (rateGood) "👍 好评" else "👎 差评") },
            text = {
                Column {
                    Text("给「${server.name}」评分（24 小时内一服一评，提交后 5 分钟生效）", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { if (it.length <= 100) comment = it },
                        placeholder = { Text("写点评价（100 字以内，可留空）") },
                        supportingText = { Text("${comment.length}/100") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        submitting = true
                        scope.launch(Dispatchers.IO) {
                            val res = ServerDiscoveryApi.rate(context, dev, server.id, rateGood, comment.trim())
                            withContext(Dispatchers.Main) {
                                submitting = false
                                showRateDialog = false
                                if (res.ok) {
                                    Toast.makeText(context, "评价已提交，5 分钟后生效", Toast.LENGTH_SHORT).show()
                                    onRefresh()
                                } else {
                                    val msg = when (res.error) {
                                        "rate_limited" -> "同一服务器 24 小时内只能评价一次"
                                        else -> "提交失败，请稍后重试"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                ) { Text(if (submitting) "提交中..." else "提交") }
            },
            dismissButton = {
                TextButton(enabled = !submitting, onClick = { showRateDialog = false }) { Text("取消") }
            },
        )
    }

    // 版本选择
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择版本进入 ${server.name}") },
            text = {
                Column {
                    pickerVersions.forEach { v ->
                        TextButton(onClick = {
                            showPicker = false
                            Toast.makeText(context, "正在启动 ${server.name}...", Toast.LENGTH_SHORT).show()
                            scope.launch { GameLauncher.launch(context, v, server.address) }
                        }) { Text(v) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ServerLogoBig(server: DiscoverServer) {
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
        initialValue = null, key1 = server.logo,
    ) {
        value = if (server.logo.isBlank()) null else kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val conn = java.net.URL(server.logo).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                android.graphics.BitmapFactory.decodeStream(conn.inputStream)
            } catch (_: Exception) {
                null
            }
        }
    }
    val shape = RoundedCornerShape(14.dp)
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = server.name,
            modifier = Modifier.size(64.dp).clip(shape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    Brush.horizontalGradient(
                        ServerDiscoveryPalettes[Math.floorMod(server.id.hashCode(), ServerDiscoveryPalettes.size)],
                    ),
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(server.name.take(1).ifEmpty { "服" }, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private val ServerDiscoveryPalettes = listOf(
    listOf(Color(0xFF1A237E), Color(0xFF42A5F5)),
    listOf(Color(0xFF4A148C), Color(0xFFAB47BC)),
    listOf(Color(0xFF1B5E20), Color(0xFF66BB6A)),
    listOf(Color(0xFFB71C1C), Color(0xFFEF5350)),
    listOf(Color(0xFFE65100), Color(0xFFFFB74D)),
    listOf(Color(0xFF004D40), Color(0xFF4DB6AC)),
)

private fun launchDetailJoin(
    context: android.content.Context,
    server: DiscoverServer,
    onPick: (List<String>) -> Unit,
) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        val installed = runCatching { MioRepository(context).loadInstalledVersions() }.getOrNull() ?: emptyList()
        withContext(Dispatchers.Main) {
            when {
                installed.isEmpty() -> Toast.makeText(context, "请先安装一个游戏版本", Toast.LENGTH_SHORT).show()
                installed.size == 1 -> {
                    Toast.makeText(context, "正在启动 ${server.name}...", Toast.LENGTH_SHORT).show()
                    GameLauncher.launch(context, installed[0].id, server.address)
                }
                else -> onPick(installed.map { it.id })
            }
        }
    }
}
