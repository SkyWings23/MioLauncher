package com.miolauncher.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miolauncher.app.ui.theme.MioGreen
import com.miolauncher.app.ui.theme.MioLauncherTheme

/**
 * 日志查看页：显示日志内容，支持 复制 / 分享 / 导出。
 * Intent 参数：title、content、exportName（可选）。
 */
class LogViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val title = intent.getStringExtra("title") ?: "日志"
        val content = intent.getStringExtra("content") ?: ""
        val exportName = intent.getStringExtra("export_name") ?: "mio-log.txt"
        setContent {
            MioLauncherTheme {
                LogViewerScreen(title = title, content = content, exportName = exportName, onBack = { finish() })
            }
        }
    }

    companion object {
        fun start(context: Context, title: String, content: String, exportName: String) {
            val i = Intent(context, LogViewerActivity::class.java)
                .putExtra("title", title)
                .putExtra("content", content)
                .putExtra("export_name", exportName)
            if (context !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }
}

@Composable
private fun LogViewerScreen(
    title: String,
    content: String,
    exportName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lines = remember(content) { content.split('\n') }

    fun copy() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(title, content))
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    fun share() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, "分享日志"))
        }.onFailure {
            Toast.makeText(context, "没有可用的分享应用", Toast.LENGTH_SHORT).show()
        }
    }

    fun export() {
        val f = com.miolauncher.app.data.CrashLogManager.exportText(context, exportName, content)
        if (f != null) {
            Toast.makeText(context, "已导出到：${f.absolutePath}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = MioGreen)
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { copy() }) {
                        Icon(Icons.Filled.CopyAll, contentDescription = "复制", tint = MioGreen)
                    }
                    IconButton(onClick = { share() }) {
                        Icon(Icons.Filled.Share, contentDescription = "分享", tint = MioGreen)
                    }
                    IconButton(onClick = { export() }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "导出", tint = MioGreen)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    ActionChip("复制全部") { copy() }
                    ActionChip("分享") { share() }
                    ActionChip("导出文件") { export() }
                }
            }
        },
    ) { padding ->
        if (content.isBlank()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("（暂无日志内容）", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp,
                ),
            ) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MioGreen),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}
