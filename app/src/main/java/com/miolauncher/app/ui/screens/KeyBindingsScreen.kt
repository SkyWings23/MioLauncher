package com.miolauncher.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.miolauncher.app.data.MioRepository
import com.miolauncher.app.ui.theme.MioGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 可绑定的按键选项（InputConstants 值 + 显示名） */
private val AVAILABLE_KEYS = listOf(
    "key.keyboard.w" to "W", "key.keyboard.a" to "A", "key.keyboard.s" to "S", "key.keyboard.d" to "D",
    "key.keyboard.space" to "空格", "key.keyboard.left.shift" to "左Shift", "key.keyboard.left.control" to "左Ctrl",
    "key.keyboard.e" to "E", "key.keyboard.q" to "Q", "key.keyboard.t" to "T", "key.keyboard.f" to "F",
    "key.keyboard.r" to "R", "key.keyboard.c" to "C", "key.keyboard.x" to "X", "key.keyboard.z" to "Z",
    "key.keyboard.1" to "1", "key.keyboard.2" to "2", "key.keyboard.3" to "3", "key.keyboard.4" to "4",
    "key.keyboard.5" to "5", "key.keyboard.6" to "6", "key.keyboard.7" to "7", "key.keyboard.8" to "8",
    "key.keyboard.9" to "9", "key.keyboard.0" to "0",
    "key.keyboard.tab" to "Tab", "key.keyboard.escape" to "Esc", "key.keyboard.f2" to "F2",
    "key.keyboard.f3" to "F3", "key.keyboard.f5" to "F5",
    "key.mouse.left" to "鼠标左键", "key.mouse.right" to "鼠标右键", "key.mouse.middle" to "鼠标中键",
)

/** 键位定义（key = options.txt 的键名） */
private data class KeyBind(
    val key: String,
    val label: String,
    val default: String,
)

private val DEFAULT_BINDS = listOf(
    KeyBind("key.forward", "前进", "key.keyboard.w"),
    KeyBind("key.back", "后退", "key.keyboard.s"),
    KeyBind("key.left", "左移", "key.keyboard.a"),
    KeyBind("key.right", "右移", "key.keyboard.d"),
    KeyBind("key.jump", "跳跃", "key.keyboard.space"),
    KeyBind("key.sneak", "潜行", "key.keyboard.left.shift"),
    KeyBind("key.sprint", "疾跑", "key.keyboard.left.control"),
    KeyBind("key.inventory", "物品栏", "key.keyboard.e"),
    KeyBind("key.attack", "攻击", "key.mouse.left"),
    KeyBind("key.use", "使用物品", "key.mouse.right"),
    KeyBind("key.drop", "丢弃物品", "key.keyboard.q"),
    KeyBind("key.chat", "打开聊天", "key.keyboard.t"),
    KeyBind("key.command", "命令", "key.keyboard.slash"),
    KeyBind("key.togglePerspective", "切换视角", "key.keyboard.f5"),
    KeyBind("key.swapOffhand", "切换副手", "key.keyboard.f"),
    KeyBind("key.screenshot", "截图", "key.keyboard.f2"),
    KeyBind("key.advancements", "进度界面", "key.keyboard.l"),
    KeyBind("key.hotbar.1", "快捷栏 1", "key.keyboard.1"),
    KeyBind("key.hotbar.2", "快捷栏 2", "key.keyboard.2"),
    KeyBind("key.hotbar.3", "快捷栏 3", "key.keyboard.3"),
    KeyBind("key.hotbar.4", "快捷栏 4", "key.keyboard.4"),
    KeyBind("key.hotbar.5", "快捷栏 5", "key.keyboard.5"),
)

private fun keyShortLabel(value: String): String {
    val v = value.removePrefix("key.keyboard.").removePrefix("key.mouse.")
    return when (v) {
        "space" -> "空格"
        "left.shift" -> "左Shift"
        "left.control" -> "左Ctrl"
        "left" -> "鼠标左键"
        "right" -> "鼠标右键"
        "middle" -> "鼠标中键"
        "slash" -> "/"
        else -> v.replaceFirstChar { it.uppercase() }
    }
}

/**
 * 键位修改界面：编辑 options.txt 的 key.* 绑定（真实影响游戏键盘映射）。
 */
@Composable
fun KeyBindingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val optionsFile = remember { File(MioRepository(context).gameDir, "options.txt") }

    var binds by remember { mutableStateOf(DEFAULT_BINDS.associate { it.key to it.default }) }
    var pickingKey by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                val map = DEFAULT_BINDS.associate { it.key to it.default }.toMutableMap()
                try {
                    optionsFile.readLines().forEach { line ->
                        val idx = line.indexOf(':')
                        if (idx > 0) {
                            val k = line.substring(0, idx)
                            val v = line.substring(idx + 1)
                            if (map.containsKey(k) && v.isNotBlank() && v != "key.keyboard.unknown") {
                                map[k] = v
                            }
                        }
                    }
                } catch (_: Exception) { }
                map
            }
            binds = parsed
        }
    }

    fun save() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val lines = if (optionsFile.exists()) optionsFile.readLines().toMutableList() else mutableListOf()
                    // 去掉旧 key.* 行
                    val rest = lines.filter { !it.startsWith("key.") }
                    val out = rest.toMutableList()
                    binds.forEach { (k, v) -> out.add("$k:$v") }
                    optionsFile.parentFile?.mkdirs()
                    optionsFile.writeText(out.joinToString("\n") + "\n")
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "保存失败：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
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
                text = "键位修改",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                binds = DEFAULT_BINDS.associate { it.key to it.default }
            }) { Text("恢复默认", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { save() }) { Text("保存", color = MioGreen, fontWeight = FontWeight.Bold) }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "点击按键可重新绑定。修改后需重新启动游戏生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            items(DEFAULT_BINDS) { def ->
                val value = binds[def.key] ?: def.default
                Surface(
                    onClick = { pickingKey = def.key },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            def.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MioGreen.copy(alpha = 0.15f),
                        ) {
                            Text(
                                keyShortLabel(value),
                                color = MioGreen,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // 按键选择对话框
    pickingKey?.let { bindKey ->
        AlertDialog(
            onDismissRequest = { pickingKey = null },
            title = { Text("选择按键", fontWeight = FontWeight.Bold) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(360.dp),
                ) {
                    items(AVAILABLE_KEYS) { (value, label) ->
                        val selected = binds[bindKey] == value
                        Surface(
                            onClick = {
                                binds = binds + (bindKey to value)
                                pickingKey = null
                            },
                            color = if (selected) MioGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MioGreen else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingKey = null }) { Text("取消") }
            },
        )
    }
}
