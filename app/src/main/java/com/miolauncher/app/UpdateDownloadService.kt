package com.miolauncher.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.miolauncher.app.data.AppUpdate
import com.miolauncher.app.data.PatchManager
import java.io.File
import kotlin.concurrent.thread

/**
 * APK 更新后台下载服务：退出下载界面后继续下载。
 * 下载进度通过静态对象 [state] 暴露，UI / 悬浮窗均可读取。
 */
class UpdateDownloadService : Service() {

    companion object {
        const val ACTION_START = "com.miolauncher.app.UPDATE_START"
        const val ACTION_CANCEL = "com.miolauncher.app.UPDATE_CANCEL"
        const val CHANNEL = "mio_update_download"
        const val NOTIF_ID = 0x5202

        @Volatile
        var state = DownloadState()
            private set

        data class DownloadState(
            val running: Boolean = false,
            val finished: Boolean = false,
            val success: Boolean = false,
            val cancelled: Boolean = false,
            val downloaded: Long = 0L,
            val total: Long = 0L,
            val speed: Long = 0L,
            val percent: Int = 0,
            val file: File? = null,
            val error: String = "",
            val versionName: String = "",
        )

        @Volatile
        private var cancelRequested = false

        @Volatile
        private var worker: Thread? = null

        /** 下载线程是否被请求取消。 */
        fun isCancelRequested(): Boolean = cancelRequested

        /** 重置状态（供界面重试时调用）。 */
        fun reset() {
            cancelRequested = false
            state = DownloadState()
        }

        /** 启动后台下载。 */
        fun start(context: Context, update: AppUpdate) {
            cancelRequested = false
            state = DownloadState(running = false, versionName = update.versionName)
            val intent = Intent(context, UpdateDownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra("versionName", update.versionName)
                .putExtra("versionCode", update.versionCode)
                .putExtra("file", update.file)
                .putExtra("sha256", update.sha256)
                .putExtra("size", update.size)
                .putExtra("desc", update.desc)
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 取消下载。 */
        fun cancel(context: Context) {
            cancelRequested = true
            try {
                context.stopService(Intent(context, UpdateDownloadService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private inner class LocalBinder : Binder()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelRequested = true
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val update = AppUpdate(
                    versionName = intent.getStringExtra("versionName") ?: "",
                    versionCode = intent.getIntExtra("versionCode", 0),
                    file = intent.getStringExtra("file") ?: "",
                    sha256 = intent.getStringExtra("sha256") ?: "",
                    size = intent.getLongExtra("size", 0L),
                    desc = intent.getStringExtra("desc") ?: "",
                )
                startForegroundInternal()
                state = state.copy(running = true, versionName = update.versionName)
                val old = worker
                if (old != null && old.isAlive) {
                    cancelRequested = true
                    old.interrupt()
                }
                worker = thread {
                    val file = PatchManager.downloadAppApk(this, update) { d, t, sp, pct ->
                        if (cancelRequested) return@downloadAppApk
                        state = state.copy(
                            downloaded = d, total = t, speed = sp, percent = pct,
                            running = true,
                        )
                        updateNotification(d, t, pct)
                    }
                    if (cancelRequested) {
                        state = state.copy(running = false, finished = true, cancelled = true)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@thread
                    }
                    if (file != null) {
                        state = state.copy(
                            running = false, finished = true, success = true,
                            downloaded = state.total, percent = 100, file = file,
                        )
                        showDoneNotification()
                    } else {
                        state = state.copy(
                            running = false, finished = true, success = false,
                            error = "下载失败，请检查网络后重试",
                        )
                        showFailNotification()
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelRequested = true
    }

    private fun startForegroundInternal() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Mio 更新下载", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = android.app.Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 MioLauncher 更新")
            .setContentText("准备下载…")
            .setOngoing(true)
            .build()
        try {
            startForeground(NOTIF_ID, notif)
        } catch (_: Exception) {
        }
    }

    private fun updateNotification(d: Long, t: Long, pct: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val text = if (t > 0) "${fmt(d)} / ${fmt(t)}" else fmt(d)
        val notif = android.app.Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 MioLauncher 更新")
            .setContentText("$text · $pct%")
            .setOngoing(true)
            .setProgress(100, pct, false)
            .build()
        try {
            nm.notify(NOTIF_ID, notif)
        } catch (_: Exception) {
        }
    }

    private fun showDoneNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = android.app.Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("MioLauncher 更新已下载")
            .setContentText("回到下载界面点击「立即安装」")
            .build()
        try {
            nm.notify(NOTIF_ID, notif)
        } catch (_: Exception) {
        }
    }

    private fun showFailNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = android.app.Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("MioLauncher 更新失败")
            .setContentText("请检查网络后重试")
            .build()
        try {
            nm.notify(NOTIF_ID, notif)
        } catch (_: Exception) {
        }
    }

    private fun fmt(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / 1073741824.0)
        bytes >= 1024L * 1024 -> String.format("%.2f MB", bytes / 1048576.0)
        bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
