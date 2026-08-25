package com.miolauncher.app

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class MioApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = this

        // 启动时主动回收内存（上次可能因下载/游戏崩溃残留大量对象），
        // 降低"下载版本闪退后无法进入启动器"的内存压力。
        try {
            val rt = Runtime.getRuntime()
            rt.gc()
            Thread.sleep(100)
            rt.gc()
        } catch (_: Exception) {}

        // 全局崩溃捕获：任何未捕获异常（主线程/其他线程）都写入日志文件，
        // 即使启动器 app 闪退，下次启动也能看到崩溃原因。
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val dir = File(filesDir, "mio/logs").apply { mkdirs() }
                val log = File(dir, "app_crash.log")
                val text = buildString {
                    append("===== 启动器崩溃（app 闪退） =====\n")
                    append("时间：").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date())).append('\n')
                    append("线程：").append(thread.name).append('\n')
                    append('\n')
                    append(sw.toString())
                }
                log.writeText(text)
                // 写入崩溃标记，保证下次启动可检测
                try {
                    val m = File(File(filesDir, "mio/game"), ".mio_crash_marker")
                    m.parentFile?.mkdirs()
                    m.writeText("app_crash=true\n$text")
                } catch (_: Exception) {}
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        @Volatile
        var appContext: MioApplication? = null
            private set

        @JvmStatic
        fun getContext(): MioApplication {
            return appContext ?: error("MioApplication not initialized")
        }
    }
}
