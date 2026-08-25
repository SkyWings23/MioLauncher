package com.miolauncher.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miolauncher.app.data.MsAccount
import com.miolauncher.app.data.SkinUploader
import com.miolauncher.app.ui.theme.MioGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 自定义皮肤弹窗：选择 PNG → 选模型（Steve/Alex）→ 上传正版 / 保存本地。
 */
@Composable
fun SkinDialog(
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var isSlim by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            working = true
            status = "处理中…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        // 复制到临时文件
                        val tmp = java.io.File(context.cacheDir, "skin_${System.currentTimeMillis()}.png")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tmp.outputStream().use { output -> input.copyTo(output) }
                        }
                        // 保存本地皮肤（离线/外置可用）
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            SkinUploader.saveLocalSkin(context, input, isSlim)
                        }
                        // 正版用户：上传到 Mojang
                        val ms = MsAccount.load(context)
                        if (ms != null) {
                            val uploadResult = runCatching {
                                SkinUploader.uploadToMojang(ms.accessToken, isSlim, tmp)
                            }
                            if (uploadResult.isSuccess) {
                                "已上传到正版账号，游戏内即时生效"
                            } else {
                                "已保存本地皮肤；正版上传失败：${uploadResult.exceptionOrNull()?.message}"
                            }
                        } else {
                            "已保存本地皮肤（离线/外置登录可用）"
                        }
                    }
                }
                working = false
                status = result.getOrElse { it.message ?: "处理失败" }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("自定义皮肤", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = if (MsAccount.load(context) != null)
                        "选择 PNG 皮肤文件。正版登录后会上传到 Mojang 账号，游戏内即时生效。"
                    else
                        "选择 PNG 皮肤文件保存为本地皮肤。正版登录可上传到 Mojang 账号。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text("皮肤模型", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row {
                    FilterChip(
                        selected = !isSlim,
                        onClick = { isSlim = false },
                        label = { Text("Steve（经典）") },
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    FilterChip(
                        selected = isSlim,
                        onClick = { isSlim = true },
                        label = { Text("Alex（纤细）") },
                    )
                }
                if (status != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = status!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { picker.launch(arrayOf("image/png", "image/*")) },
                enabled = !working,
            ) {
                Text("选择皮肤文件", color = MioGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
