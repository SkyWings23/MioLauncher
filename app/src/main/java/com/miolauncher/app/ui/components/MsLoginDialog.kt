package com.miolauncher.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import com.miolauncher.app.ui.theme.MioGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 正版（微软）登录弹窗。
 * 展示设备码，玩家打开浏览器输入码完成授权；成功保存会话。
 */
@Composable
fun MsLoginDialog(
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deviceCode by remember { mutableStateOf<String?>(null) }
    var verifyUrl by remember { mutableStateOf<String?>(null) }

    fun startLogin() {
        if (loading) return
        loading = true
        error = null
        deviceCode = null
        verifyUrl = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    MsAccount.login(
                        onDeviceCode = { code, url ->
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                deviceCode = code
                                verifyUrl = url
                            }
                        },
                        onOpenBrowser = { url ->
                            // 不自动打开浏览器：用户先记下设备码，再点按钮手动打开
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                verifyUrl = url
                            }
                        },
                    )
                }
            }
            loading = false
            result.onSuccess { s ->
                MsAccount.save(context, s)
                onSuccess()
            }.onFailure { e ->
                error = e.message ?: "登录失败"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("微软登录（正版）", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (deviceCode == null) {
                    Text(
                        text = "使用正版 Minecraft 账号登录。登录后支持皮肤、披风与多人服务器验证。\n\n" +
                            "需要你已购买正版 Minecraft（Java 版）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "请在浏览器中打开下面的链接，输入设备码完成授权：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = verifyUrl ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MioGreen,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "设备码：${deviceCode}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MioGreen,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            verifyUrl?.let { url ->
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(url),
                                        ),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("打开浏览器授权", color = MioGreen)
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (loading) {
                    Spacer(Modifier.height(12.dp))
                    Text("等待授权中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { startLogin() },
                enabled = !loading,
            ) {
                Text(if (loading) "登录中…" else "开始登录", color = MioGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
