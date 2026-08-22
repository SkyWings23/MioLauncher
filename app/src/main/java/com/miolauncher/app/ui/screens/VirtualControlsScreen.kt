package com.miolauncher.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.miolauncher.app.data.FclExpr
import com.miolauncher.app.ui.components.toastOnMain
import com.miolauncher.app.ui.theme.MioGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val VIRTUAL_KEYS = listOf(
    32 to "跳跃", 70 to "副手", 340 to "潜行", 341 to "疾跑", 16 to "Shift",
    69 to "物品栏(E)", 87 to "前进(W)", 65 to "左移(A)", 83 to "后退(S)", 68 to "右移(D)",
    81 to "丢弃(Q)", 84 to "聊天(T)", 27 to "Esc", 258 to "Tab",
    292 to "F3", 293 to "F2", 294 to "F5",
    0 to "鼠标左键", 1 to "鼠标右键", 2 to "鼠标中键", -1 to "无",
    -3 to "左键", -4 to "右键", -5 to "鼠标开关", -6 to "中键",
)
private fun keyLabel(code: Int): String = VIRTUAL_KEYS.firstOrNull { it.first == code }?.second ?: "$code"

/** 预设背景色（ARGB） */
private val STYLE_COLORS = listOf(
    0xFF2E7D32.toInt() to "绿",
    0xFF1B5E20.toInt() to "深绿",
    0xFF1565C0.toInt() to "蓝",
    0xFFC62828.toInt() to "红",
    0xFFEF6C00.toInt() to "橙",
    0xFF546E7A.toInt() to "灰",
    0xFF6B4F2A.toInt() to "棕",
    0xFF222222.toInt() to "深灰",
    0xFF999999.toInt() to "浅灰",
    0x66000000.toInt() to "半透明",
)

private class WysEntry(
    val id: String,
    val listKey: String,
    val index: Int,
    var name: String,
    var visible: Boolean,
    var key: Int,
    var size: Float,
    var opacity: Float,
    var x: Float,
    var y: Float,
    var bgColor: Int,
    var cornerRadius: Float,
    var strokeColor: Int,
    var strokeWidth: Float,
    var isToggle: Boolean,
    val isDrawer: Boolean,
    val obj: JsonObject,
)

/**
 * 虚拟键位（对齐游戏内修改）：
 * 按键组切换 / 所见即所得拖动 / 样式编辑（颜色、圆角、描边、文字、键位、大小、透明度）。
 */
@Composable
fun VirtualControlsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    // 进入强制横屏，退出恢复
    LaunchedEffect(Unit) {
        context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    DisposableEffect(Unit) {
        onDispose {
            context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val controlMapDir = remember { File(context.filesDir, "mio/controlmap") }
    val sourceAssetFile = remember { File(context.filesDir, "default.json") }

    var groups by remember { mutableStateOf(listOf("default")) }
    var currentGroup by remember { mutableStateOf("default") }
    var groupMenu by remember { mutableStateOf(false) }
    var showNewGroupDialog by remember { mutableStateOf(false) }

    var entries by remember { mutableStateOf<List<WysEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    var previewW by remember { mutableStateOf(2400f) }
    var previewH by remember { mutableStateOf(1080f) }

    fun layoutFile(group: String): File = File(controlMapDir, "$group.json")

    fun load() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // 读取当前组
                    val prefs = context.getSharedPreferences("mio_settings", Context.MODE_PRIVATE)
                    val grp = prefs.getString("current_group", "default") ?: "default"
                    val grpSafe = grp.trim().ifEmpty { "default" }

                    // 扫描已有按键组
                    controlMapDir.mkdirs()
                    val found = controlMapDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
                        ?.map { it.name.removeSuffix(".json") }?.sorted() ?: emptyList()
                    val allGroups = (listOf("default") + found).distinct().sorted()
                    groups = allGroups
                    currentGroup = grpSafe

                    val src = layoutFile(grpSafe)
                    if (!src.isFile) {
                        context.assets.open("default.json").use { input ->
                            src.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    val root = JsonParser.parseString(src.readText()).asJsonObject

                    val dw = 2400f
                    val dh = 1080f
                    val list = mutableListOf<WysEntry>()
                    fun addList(listKey: String, isDrawer: Boolean) {
                        val arr = root.getAsJsonArray(listKey) ?: return
                        arr.forEachIndexed { i, el ->
                            if (!el.isJsonObject) return@forEachIndexed
                            val o = el.asJsonObject
                            val dx = o.get("dynamicX")?.asString
                            val dy = o.get("dynamicY")?.asString
                            val px = if (dx != null) FclExpr.eval(dx, dw, dh, 1f, 0f) else dw / 2
                            val py = if (dy != null) FclExpr.eval(dy, dw, dh, 1f, 0f) else dh / 2
                            val keys = o.getAsJsonArray("keycodes")
                            list.add(
                                WysEntry(
                                    id = "$listKey-$i",
                                    listKey = listKey,
                                    index = i,
                                    name = o.get("name")?.asString?.ifBlank { "未命名" } ?: "未命名 $i",
                                    visible = o.get("displayInGame")?.asBoolean ?: true,
                                    key = if (keys != null && keys.size() > 0) keys[0].asInt else -1,
                                    size = (o.get("width")?.asFloat ?: 60f),
                                    opacity = (o.get("opacity")?.asFloat ?: 1f).coerceIn(0.1f, 1f),
                                    x = px,
                                    y = py,
                                    bgColor = o.get("bgColor")?.asInt ?: 0x992E7D32.toInt(),
                                    cornerRadius = (o.get("cornerRadius")?.asFloat ?: 100f).coerceIn(0f, 100f),
                                    strokeColor = o.get("strokeColor")?.asInt ?: -1,
                                    strokeWidth = (o.get("strokeWidth")?.asFloat ?: 0f).coerceIn(0f, 10f),
                                    isToggle = o.get("isToggle")?.asBoolean ?: false,
                                    isDrawer = isDrawer,
                                    obj = o,
                                )
                            )
                        }
                    }
                    addList("mControlDataList", false)
                    addList("mDrawerDataList", true)
                    addList("mJoystickDataList", false)
                    list
                } catch (e: Exception) {
                    error = e.message ?: "读取布局失败"
                    emptyList()
                }
            }
            entries = result
            loaded = true
        }
    }

    fun save() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    controlMapDir.mkdirs()
                    val f = layoutFile(currentGroup)
                    val root = JsonParser.parseString(f.readText()).asJsonObject
                    entries.forEach { e ->
                        val arr = root.getAsJsonArray(e.listKey) ?: return@forEach
                        if (e.index < arr.size() && arr[e.index].isJsonObject) {
                            val o = arr[e.index].asJsonObject
                            o.addProperty("displayInGame", e.visible)
                            val ka = JsonArray()
                            ka.add(e.key); ka.add(0); ka.add(0); ka.add(0)
                            o.add("keycodes", ka)
                            o.addProperty("width", e.size)
                            o.addProperty("height", e.size)
                            o.addProperty("opacity", e.opacity)
                            o.addProperty("bgColor", e.bgColor)
                            o.addProperty("cornerRadius", e.cornerRadius)
                            o.addProperty("strokeColor", e.strokeColor)
                            o.addProperty("strokeWidth", e.strokeWidth)
                            o.addProperty("isToggle", e.isToggle)
                            o.addProperty("name", e.name)
                            o.addProperty("dynamicX", fmt(e.x / previewW) + " * \${screen_width}")
                            o.addProperty("dynamicY", fmt(e.y / previewH) + " * \${screen_height}")
                        }
                    }
                    f.writeText(root.toString())
                    context.getSharedPreferences("mio_settings", Context.MODE_PRIVATE)
                        .edit().putString("current_group", currentGroup).apply()
                    toastOnMain(context, "已保存到「$currentGroup」，重启游戏生效")
                } catch (e: Exception) {
                    toastOnMain(context, "保存失败：${e.message}")
                }
            }
        }
    }

    fun switchGroup(name: String) {
        if (name == currentGroup) return
        currentGroup = name
        context.getSharedPreferences("mio_settings", Context.MODE_PRIVATE)
            .edit().putString("current_group", name).apply()
        loaded = false
        load()
    }

    fun createNewGroup(name: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    controlMapDir.mkdirs()
                    val src = layoutFile(currentGroup)
                    val target = layoutFile(name)
                    if (src.isFile) src.copyTo(target, overwrite = true)
                    else context.assets.open("default.json").use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    context.getSharedPreferences("mio_settings", Context.MODE_PRIVATE)
                        .edit().putString("current_group", name).apply()
                } catch (e: Exception) {
                    toastOnMain(context, "创建失败：${e.message}")
                }
            }
            loaded = false
            load()
        }
    }

    fun addButton() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    controlMapDir.mkdirs()
                    val f = layoutFile(currentGroup)
                    val root = JsonParser.parseString(f.readText()).asJsonObject
                    val list = root.getAsJsonArray("mControlDataList") ?: JsonArray().also { root.add("mControlDataList", it) }
                    val o = JsonObject()
                    o.addProperty("name", "新按键")
                    o.addProperty("displayInGame", true)
                    o.addProperty("displayInMenu", false)
                    o.addProperty("opacity", 1f)
                    o.addProperty("width", 60f)
                    o.addProperty("height", 60f)
                    o.addProperty("cornerRadius", 100f)
                    o.addProperty("bgColor", 0x992E7D32)
                    o.addProperty("strokeColor", -1)
                    o.addProperty("strokeWidth", 0f)
                    o.addProperty("isSwipeable", false)
                    o.addProperty("isToggle", false)
                    o.addProperty("passThruEnabled", false)
                    o.addProperty("dynamicX", "0.5 * \${screen_width}")
                    o.addProperty("dynamicY", "0.5 * \${screen_height}")
                    val ka = JsonArray(); ka.add(-1); ka.add(0); ka.add(0); ka.add(0)
                    o.add("keycodes", ka)
                    list.add(o)
                    f.writeText(root.toString())
                } catch (e: Exception) {
                    toastOnMain(context, "添加失败：${e.message}")
                }
            }
            loaded = false
            load()
        }
    }

    fun deleteEntry(e: WysEntry) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val f = layoutFile(currentGroup)
                    val root = JsonParser.parseString(f.readText()).asJsonObject
                    val arr = root.getAsJsonArray(e.listKey)
                    if (arr != null && e.index < arr.size()) {
                        arr.remove(e.index)
                        f.writeText(root.toString())
                    }
                } catch (ex: Exception) {
                    toastOnMain(context, "删除失败：${ex.message}")
                }
            }
            entries = entries.filter { it.id != e.id }
            selectedId = null
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：返回 + 按键组切换 + 添加 + 保存
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = MioGreen)
            }
            // 按键组选择
            Surface(
                onClick = { groupMenu = true },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("按键组：$currentGroup", fontWeight = FontWeight.Medium)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MioGreen)
                }
            }
            DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                groups.forEach { g ->
                    DropdownMenuItem(
                        text = { Text(if (g == currentGroup) "$g ✓" else g) },
                        onClick = { groupMenu = false; switchGroup(g) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("＋ 新建按键组", color = MioGreen) },
                    onClick = { groupMenu = false; showNewGroupDialog = true },
                )
            }

            Spacer(Modifier.width(12.dp))
            Text(
                text = "虚拟键位",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { addButton() }) {
                Icon(Icons.Filled.Add, contentDescription = "添加", tint = MioGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(2.dp))
                Text("添加", color = MioGreen)
            }
            TextButton(onClick = { save() }) { Text("保存", color = MioGreen, fontWeight = FontWeight.Bold) }
        }

        when {
            !loaded -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = MioGreen)
            }
            error != null -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            else -> {
                // 预览区
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .background(Color(0xFF16241A), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                ) {
                    Box(Modifier.fillMaxSize().onSizeChanged { s ->
                        previewW = s.width.toFloat()
                        previewH = s.height.toFloat()
                    }) {
                        entries.forEach { e ->
                            val halfPx = e.size * density / 2
                            val selected = e.id == selectedId
                            val alpha = if (e.visible) e.opacity else 0.25f
                            val shape = if (e.isDrawer) RoundedCornerShape(e.cornerRadius.dp) else CircleShape
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (e.x - halfPx).roundToInt(),
                                            (e.y - halfPx).roundToInt(),
                                        )
                                    }
                                    .size(e.size.dp)
                                    .pointerInput(e.id) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown()
                                            selectedId = e.id
                                            var dragging = false
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull() ?: break
                                                if (change.changedToUp()) break
                                                if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                                    dragging = true
                                                    change.consume()
                                                }
                                                if (dragging) {
                                                    change.consume()
                                                    val maxX = previewW - halfPx
                                                    val maxY = previewH - halfPx
                                                    // 预览区比控件还小时范围会反转，coerceIn 会抛异常，故先守卫
                                                    if (maxX >= halfPx)
                                                        e.x = (e.x + change.positionChange().x).coerceIn(halfPx, maxX)
                                                    if (maxY >= halfPx)
                                                        e.y = (e.y + change.positionChange().y).coerceIn(halfPx, maxY)
                                                }
                                            }
                                        }
                                    }
                                    .then(
                                        if (selected) Modifier.background(Color(0xFF4CAF50).copy(alpha = 0.4f), CircleShape)
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(e.bgColor.toArgbCompat(alpha), shape)
                                        .then(
                                            if (e.strokeWidth > 0)
                                                Modifier.border(e.strokeWidth.dp, e.strokeColor.toArgbCompat(alpha), shape)
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = e.name,
                                        color = Color.White,
                                        fontSize = if (e.size < 60) 10.sp else 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Text(
                            "拖动按键 · 点击选中编辑样式 · 按键组「$currentGroup」",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 6.dp),
                        )
                    }
                }

                // 底部样式编辑栏（选中项）
                val selected = entries.firstOrNull { it.id == selectedId }
                if (selected != null) {
                    var keyMenu by remember { mutableStateOf(false) }
                    var colorMenu by remember { mutableStateOf(false) }
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            // 第一行：名称 + 显示 + 键位 + 删除
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = selected.name,
                                    onValueChange = { selected.name = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.width(180.dp).height(48.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("显示", style = MaterialTheme.typography.labelMedium)
                                Switch(checked = selected.visible, onCheckedChange = { selected.visible = it })
                                Spacer(Modifier.width(6.dp))
                                Text("切换", style = MaterialTheme.typography.labelMedium)
                                Switch(checked = selected.isToggle, onCheckedChange = { selected.isToggle = it })
                                Spacer(Modifier.weight(1f))
                                Text("键位：${keyLabel(selected.key)}", style = MaterialTheme.typography.labelMedium)
                                TextButton(onClick = { keyMenu = true }) { Text("改键", color = MioGreen) }
                                IconButton(onClick = { deleteEntry(selected) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                }
                                DropdownMenu(expanded = keyMenu, onDismissRequest = { keyMenu = false }) {
                                    VIRTUAL_KEYS.forEach { (code, label) ->
                                        DropdownMenuItem(text = { Text(label) }, onClick = { selected.key = code; keyMenu = false })
                                    }
                                }
                            }
                            // 第二行：颜色 + 圆角
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("背景", style = MaterialTheme.typography.labelMedium)
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .size(28.dp)
                                        .background(selected.bgColor.toArgbCompat(1f), CircleShape)
                                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                                )
                                TextButton(onClick = { colorMenu = true }) { Text("换色", color = MioGreen) }
                                Spacer(Modifier.width(10.dp))
                                Text("圆角 ${selected.cornerRadius.toInt()}", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = selected.cornerRadius,
                                    onValueChange = { selected.cornerRadius = it },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                )
                                DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                                    STYLE_COLORS.forEach { (c, l) ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(Modifier.size(18.dp).background(c.toArgbCompat(1f), CircleShape))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(l)
                                                }
                                            },
                                            onClick = { selected.bgColor = c; colorMenu = false },
                                        )
                                    }
                                }
                            }
                            // 第三行：大小 + 不透明
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("大小 ${selected.size.toInt()}", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = selected.size,
                                    onValueChange = { selected.size = it },
                                    valueRange = 30f..200f,
                                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("不透明 ${(selected.opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = selected.opacity,
                                    onValueChange = { selected.opacity = it },
                                    valueRange = 0.1f..1f,
                                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 新建按键组对话框
    if (showNewGroupDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewGroupDialog = false },
            title = { Text("新建按键组", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("基于当前按键组创建一份新布局（可在游戏内切换）。", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("输入按键组名称") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val safe = name.trim()
                    if (safe.isNotEmpty()) {
                        showNewGroupDialog = false
                        createNewGroup(safe)
                    }
                }) { Text("创建", color = MioGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showNewGroupDialog = false }) { Text("取消") }
            },
        )
    }
}

private fun fmt(v: Float): String = String.format("%.4f", v)

/** Int ARGB → Compose Color（叠加 alpha） */
private fun Int.toArgbCompat(alpha: Float): Color {
    val a = ((this ushr 24) and 0xFF)
    val r = ((this shr 16) and 0xFF)
    val g = ((this shr 8) and 0xFF)
    val b = (this and 0xFF)
    val eff = (alpha * (a / 255f)).coerceIn(0f, 1f)
    return Color(r / 255f, g / 255f, b / 255f, eff)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
