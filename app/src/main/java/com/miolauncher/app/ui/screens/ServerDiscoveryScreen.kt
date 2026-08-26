package com.miolauncher.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SORT_TABS = listOf(
    "rating" to "🌟 好评优先",
    "hot" to "🔥 人气最高",
    "new" to "🕒 最新入驻",
)
private val TAG_FILTERS = listOf(
    null, "纯净", "模组", "生存", "小游戏", "空岛", "休闲", "PVP",
)

@Composable
fun ServerDiscoveryScreen(
    initialServerId: String? = null,
    onConsumedInitial: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var servers by remember { mutableStateOf<List<DiscoverServer>>(emptyList()) }
    var banners by remember { mutableStateOf<List<DiscoverServer>>(emptyList()) }
    var reviewsMap by remember { mutableStateOf<Map<String, List<DiscoverReview>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var sort by remember { mutableStateOf("rating") }
    var tag by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<DiscoverServer?>(null) }
    var notInterested by remember { mutableStateOf<DiscoverServer?>(null) }
    var joinTarget by remember { mutableStateOf<DiscoverServer?>(null) }
    var pickerVersions by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var openedInitial by remember { mutableStateOf(false) }

    val dev = DiscoverStore.deviceId(context)

    LaunchedEffect(refreshKey) { banners = ServerDiscoveryApi.fetchBanners(context) }
    LaunchedEffect(sort, tag, query, refreshKey) {
        loading = true
        val list = ServerDiscoveryApi.fetchServers(context, sort, tag, query)
        servers = list
        reviewsMap = ServerDiscoveryApi.fetchReviews(context, list.map { it.id })
        loading = false
    }

    // 通知/入口跳转：列表加载后打开指定服详情（只触发一次）
    LaunchedEffect(initialServerId, servers) {
        if (initialServerId != null && !openedInitial && servers.isNotEmpty()) {
            openedInitial = true
            detail = servers.firstOrNull { it.id == initialServerId } ?: servers.firstOrNull()
            onConsumedInitial()
        }
    }

    detail?.let { srv ->
        ServerDetailScreen(server = srv, onBack = { detail = null }, onRefresh = { refreshKey++ })
        return
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "发现好服",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = { showSearch = true }) {
                Icon(Icons.Filled.Search, contentDescription = "搜索", tint = MioGreen)
            }
            IconButton(onClick = { showFilter = true }) {
                Icon(Icons.Filled.FilterList, contentDescription = "筛选", tint = MioGreen)
            }
        }

        if (banners.isNotEmpty()) {
            BannerCarousel(banners = banners, onOpen = { detail = it })
            Spacer(Modifier.height(4.dp))
        }

        // 排序 Tab
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SORT_TABS.forEach { (key, label) ->
                TextButton(onClick = { sort = key }) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (sort == key) FontWeight.Bold else FontWeight.Normal,
                        color = if (sort == key) MioGreen else Color.Gray,
                    )
                }
            }
        }

        when {
            loading && servers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MioGreen)
            }
            servers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无服务器", color = Color.Gray)
                    TextButton(onClick = { refreshKey++ }) { Text("重新加载") }
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(servers, key = { it.id }) { srv ->
                        if (!DiscoverStore.isHidden(context, srv.id)) {
                            ServerCard(
                                server = srv,
                                reviews = reviewsMap[srv.id] ?: emptyList(),
                                onOpen = {
                                    detail = srv
                                    DiscoverStore.addTag(context, srv.tags)
                                    scope.launch(Dispatchers.IO) {
                                        ServerDiscoveryApi.track(context, dev, srv.id, "click", srv.tags)
                                    }
                                },
                                onJoin = {
                                    DiscoverStore.addTag(context, srv.tags)
                                    scope.launch(Dispatchers.IO) {
                                        ServerDiscoveryApi.track(context, dev, srv.id, "join", srv.tags)
                                    }
                                    launchJoin(context, srv) { pickerVersions = it; joinTarget = srv }
                                },
                                onNotInterested = { notInterested = srv },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    // 搜索弹窗
    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("搜索服务器") },
            text = {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("搜服名 / 版本 / 模式 / 标签") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    query = searchText.trim().ifEmpty { null }
                    showSearch = false
                }) { Text("搜索") }
            },
            dismissButton = {
                TextButton(onClick = { showSearch = false }) { Text("取消") }
            },
        )
    }

    // 标签筛选弹窗
    if (showFilter) {
        AlertDialog(
            onDismissRequest = { showFilter = false },
            title = { Text("按标签筛选") },
            text = {
                Column {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(TAG_FILTERS) { t ->
                            FilterChip(
                                selected = tag == t,
                                onClick = {
                                    tag = t
                                    showFilter = false
                                },
                                label = { Text(t ?: "全部") },
                            )
                        }
                    }
                    TextButton(onClick = { query = null; tag = null; showFilter = false }) {
                        Text("清除全部筛选")
                    }
                }
            },
            confirmButton = {},
        )
    }

    // 不感兴趣确认
    notInterested?.let { srv ->
        AlertDialog(
            onDismissRequest = { notInterested = null },
            title = { Text("不感兴趣") },
            text = { Text("不再推荐「${srv.name}」，30 天内不会再出现。") },
            confirmButton = {
                TextButton(onClick = {
                    DiscoverStore.hideServer(context, srv.id)
                    scope.launch(Dispatchers.IO) { ServerDiscoveryApi.report(context, dev, srv.id) }
                    notInterested = null
                    refreshKey++
                    Toast.makeText(context, "已标记为不感兴趣", Toast.LENGTH_SHORT).show()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { notInterested = null }) { Text("取消") }
            },
        )
    }

    // 版本选择（多版本时）
    joinTarget?.let { srv ->
        if (pickerVersions.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { joinTarget = null },
                title = { Text("选择版本进入 ${srv.name}") },
                text = {
                    Column {
                        pickerVersions.forEach { v ->
                            TextButton(onClick = {
                                joinTarget = null
                                Toast.makeText(context, "正在启动 ${srv.name}...", Toast.LENGTH_SHORT).show()
                                scope.launch { GameLauncher.launch(context, v, srv.address) }
                            }) { Text(v) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { joinTarget = null }) { Text("取消") }
                },
            )
        }
    }
}

/** 点击"立即进入"：查已安装版本，单版本直接进、多版本弹选择 */
private fun launchJoin(
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

/** 置顶黄金展位横向轮播 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BannerCarousel(banners: List<DiscoverServer>, onOpen: (DiscoverServer) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { banners.size })
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 12.dp),
    ) { page ->
        val b = banners[page]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(bannerColors(b.id)))
                .combinedClickable(onClick = { onOpen(b) }),
        ) {
            Box(
                modifier = Modifier
                    .padding(10.dp)
                    .background(Color(0xFFE03131), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("🔥 赞助置顶", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalAlignment = Alignment.End) {
                Text(b.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${b.version} · ${b.mode} · ${b.online}人在线", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            }
            Text(
                text = "距置顶结束还剩 ${formatCountdown(b.pinRemaining)}",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

private val BANNER_PALETTES = listOf(
    listOf(Color(0xFF1A237E), Color(0xFF42A5F5)),
    listOf(Color(0xFF4A148C), Color(0xFFAB47BC)),
    listOf(Color(0xFF1B5E20), Color(0xFF66BB6A)),
    listOf(Color(0xFFB71C1C), Color(0xFFEF5350)),
    listOf(Color(0xFFE65100), Color(0xFFFFB74D)),
    listOf(Color(0xFF004D40), Color(0xFF4DB6AC)),
)

private fun bannerColors(id: String): List<Color> {
    val idx = Math.floorMod(id.hashCode(), BANNER_PALETTES.size)
    return BANNER_PALETTES[idx]
}

/** 服务器信息流卡片 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ServerCard(
    server: DiscoverServer,
    reviews: List<DiscoverReview>,
    onOpen: () -> Unit,
    onJoin: () -> Unit,
    onNotInterested: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .combinedClickable(onClick = onOpen, onLongClick = onNotInterested),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServerLogo(server)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(server.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        server.badge.takeIf { it.isNotBlank() }?.let { badge ->
                            Spacer(Modifier.width(5.dp))
                            Text(
                                badge,
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .background(
                                        if (badge == "满人") Color(0xFFE03131) else MioGreen,
                                        RoundedCornerShape(5.dp),
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text("● ${server.online} 人在线", fontSize = 12.sp, color = MioGreen)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${server.version} | ${server.mode} | ${server.tags.joinToString("、")}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            RatingRow(server)
            if (reviews.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                ReviewMarquee(reviews)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onJoin,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = Color.White,
                        containerColor = Color(0xFFE8590C),
                    ),
                ) {
                    Text("🚀 立即进入", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ServerLogo(server: DiscoverServer) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(bannerColors(server.id).first(), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            server.name.take(1).ifEmpty { "服" },
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RatingRow(server: DiscoverServer) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (server.hasRating) {
            val rate = server.rating ?: 0
            Box(Modifier.width(100.dp)) {
                LinearProgressIndicator(
                    progress = { rate / 100f },
                    modifier = Modifier
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MioGreen,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("$rate% 好评", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MioGreen)
            Spacer(Modifier.width(6.dp))
            Text("共 ${server.reviewCount} 条评价", fontSize = 11.sp, color = Color.Gray)
        } else {
            Text("新服暂无评分（少于 10 条评价）", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

/** 最新短评跑马灯（循环展示最近 3 条） */
@Composable
private fun ReviewMarquee(reviews: List<DiscoverReview>) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(reviews.size) {
        if (reviews.size > 1) {
            while (true) {
                delay(3200)
                index = (index + 1) % reviews.size
            }
        }
    }
    AnimatedContent(targetState = index) { i ->
        val r = reviews[i.coerceIn(0, reviews.size - 1)]
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(if (r.good) "👍" else "👎", fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                if (r.comment.isNotBlank()) "\"${r.comment}\"" else "（用户评价，无文字）",
                fontSize = 12.sp,
                color = Color(0xFFB0BEC5),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatCountdown(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return "%02d:%02d:%02d".format(h, m, sec)
}
