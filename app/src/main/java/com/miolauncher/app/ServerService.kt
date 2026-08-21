package com.miolauncher.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.miolauncher.app.data.ServerHost
import java.io.File
import kotlin.concurrent.thread

/**
 * 在独立 :server 进程内运行 Minecraft 服务器（进程内 JLI_Launch，规避 SELinux 禁止 exec）。
 * 通过广播 / 标记文件与主进程通信。
 */
class ServerService : Service() {

    companion object {
        const val ACTION_START = "com.miolauncher.app.SERVER_START"
        const val ACTION_STOP = "com.miolauncher.app.SERVER_STOP"
        const val EXT_VERSION = "versionId"
        const val EXT_PORT = "port"
        const val EXT_MEM = "memoryMb"
        const val EXT_ONLINE = "onlineMode"

        private const val CHANNEL = "mio_server"

        /** 服务器运行标记（主进程用它判断 isRunning） */
        fun runningMarker(context: Context): File = File(File(context.filesDir, "mio/game/server"), ".running")

        fun startIntent(context: Context, versionId: String, port: Int, memMb: Int, online: Boolean): Intent =
            Intent(context, ServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXT_VERSION, versionId)
                putExtra(EXT_PORT, port)
                putExtra(EXT_MEM, memMb)
                putExtra(EXT_ONLINE, online)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, ServerService::class.java).apply { action = ACTION_STOP }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // 停止：清标记并退出 :server 进程
                runningMarker(this).delete()
                stopSelf()
                thread { Thread.sleep(300); android.os.Process.killProcess(android.os.Process.myPid()) }
            }
            ACTION_START -> {
                val versionId = intent.getStringExtra(EXT_VERSION) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(EXT_PORT, 25565)
                val memMb = intent.getIntExtra(EXT_MEM, 1024)
                val online = intent.getBooleanExtra(EXT_ONLINE, false)
                startForegroundInternal()
                // CallbackBridge.<clinit> 需要 Choreographer（依赖 Looper）。
                // onStartCommand 在主线程（有 Looper），先在这里完成类初始化，
                // 否则后台线程加载 pojavexec 时 FindClass 失败导致 UnsatisfiedLinkError。
                runCatching { Class.forName("org.lwjgl.glfw.CallbackBridge") }
                    .onFailure { android.util.Log.e("MioServer", "CallbackBridge 初始化失败", it) }
                thread {
                    runServer(versionId, port, memMb, online)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundInternal() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Mio 服务器", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = android.app.Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("MioLauncher 服务器")
            .setContentText("Minecraft 服务器运行中…")
            .setOngoing(true)
            .build()
        try {
            startForeground(0x5101, notif)
        } catch (_: Exception) { }
    }

    private fun runServer(versionId: String, port: Int, memMb: Int, online: Boolean) {
        try {
            val serverDir = File(filesDir, "mio/game/server")
            serverDir.mkdirs()
            val jar = File(serverDir, "server.jar")
            if (!jar.isFile || jar.length() < 1_000_000) {
                runningMarker(this).delete()
                stopSelf()
                return
            }
            File(serverDir, "eula.txt").writeText("eula=true\n")
            val props = File(serverDir, "server.properties")
            if (!props.exists()) {
                props.writeText(buildString {
                    append("server-port=$port\n")
                    append("online-mode=$online\n")
                    append("motd=MioLauncher 服务器\n")
                    append("spawn-protection=0\n")
                    append("view-distance=8\n")
                    append("max-players=8\n")
                    append("enable-rcon=true\n")
                    append("rcon.port=${ServerHost.RCON_PORT}\n")
                    append("rcon.password=${ServerHost.RCON_PASSWORD}\n")
                })
            }
            // 标记运行中
            runningMarker(this).writeText("pid=${android.os.Process.myPid()}")

            val args = mutableListOf(
                "-Xmx${memMb}M",
                "-Xms256m",
                "-Djava.net.preferIPv4Stack=true",
                "-Djava.io.tmpdir=" + File(cacheDir, "mioserver").apply { mkdirs() }.absolutePath,
                "-Duser.home=" + serverDir.absolutePath,
                "-jar", jar.absolutePath,
                "nogui",
            )
            com.miolauncher.backend.JRE.launchServer(this, args, serverDir)
        } catch (e: Exception) {
            android.util.Log.e("MioServer", "服务器异常", e)
        } finally {
            runningMarker(this).delete()
            stopSelf()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
