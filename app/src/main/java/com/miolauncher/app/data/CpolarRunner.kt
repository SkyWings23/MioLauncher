package com.miolauncher.app.data

import android.content.Context
import java.io.File

/**
 * 内置 cpolar：从 nativeLibraryDir 直接运行官方 arm64 二进制（已验证 SELinux 放行 exec）。
 *
 * 写入 cpolar.yml（authtoken + tcp 隧道 → 127.0.0.1:port），以子进程运行。
 * cpolar 3.3.12 的本地 API 不是 ngrok 兼容格式（/api/tunnels 返回空 body），
 * 但 stdout 日志会输出连接 / 认证 / 隧道建立 / 错误信息。
 * 因此这里统一从日志解析状态，让普通用户（不看 adb）也能看到发生了什么。
 */
object CpolarRunner {

    const val WEB_PORT = 4040

    private const val PREF = "mio_cpolar"
    private const val KEY_TOKEN = "authtoken"

    /** cpolar 运行状态机（供 UI 直接显示） */
    enum class CpState {
        STOPPED,      // 未运行
        STARTING,     // 运行中，正在连接/认证
        TUNNEL_UP,    // 隧道已建立（有公网地址）
        AUTH_FAILED,  // authtoken 被服务器拒绝
        NETWORK_ERROR,// 连不上 cpolar 隧道服务器
    }

    data class CpStatus(
        val state: CpState,
        val message: String,
        val publicUrl: String?,
        val logTail: List<String>,
        val running: Boolean,
    )

    @Volatile
    private var process: Process? = null

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---- authtoken ----
    fun authToken(context: Context): String =
        prefs(context).getString(KEY_TOKEN, "") ?: ""

    fun setAuthToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    // ---- 路径 ----
    fun dir(context: Context): File =
        File(context.filesDir, "mio/cpolar").apply { mkdirs() }

    fun configFile(context: Context): File = File(dir(context), "cpolar.yml")

    fun logFile(context: Context): File = File(dir(context), "cpolar.log")

    fun binary(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, "libcpolar.so")

    // ---- 运行状态 ----
    fun isRunning(): Boolean = process?.isAlive == true

    /** 写 cpolar.yml（每次按当前端口重写，保持幂等） */
    private fun ensureConfig(context: Context, port: Int) {
        val yml = buildString {
            append("version: \"2\"\n")
            append("authtoken: ${authToken(context)}\n")
            append("web_addr: 127.0.0.1:$WEB_PORT\n")
            append("tunnels:\n")
            append("  mio-mc:\n")
            append("    proto: tcp\n")
            append("    addr: $port\n")
        }
        configFile(context).writeText(yml)
    }

    /** 启动内置 cpolar（阻塞式子进程，进程内保持运行） */
    fun start(context: Context, port: Int) {
        if (isRunning()) return
        val token = authToken(context)
        if (token.isBlank()) {
            throw IllegalStateException("请先填写 cpolar authtoken")
        }
        val bin = binary(context)
        if (!bin.exists()) {
            throw IllegalStateException("内置 cpolar 未找到（libcpolar.so）")
        }
        ensureConfig(context, port)
        val log = logFile(context)
        if (log.exists() && log.length() > 512 * 1024) {
            log.delete()
        }
        val cfg = configFile(context).absolutePath
        val pb = ProcessBuilder(bin.absolutePath, "start", "mio-mc", "-config=$cfg", "-log=stdout")
        pb.directory(dir(context))
        pb.redirectErrorStream(true)
        pb.redirectOutput(log)
        process = pb.start()
    }

    /** 停止内置 cpolar */
    fun stop() {
        process?.let { p ->
            runCatching { p.destroy() }
            runCatching { p.destroyForcibly() }
        }
        process = null
    }

    /** 读取 cpolar.log 尾部（供 UI 显示给普通用户） */
    fun logTail(context: Context, maxLines: Int = 120): List<String> {
        val log = logFile(context)
        if (!log.isFile) return emptyList()
        return runCatching { log.readLines().takeLast(maxLines) }.getOrElse { emptyList() }
    }

    /**
     * 从日志解析公网地址（如 15.tcp.cpolar.top:12841）。
     * 日志里会输出 "Tunnel established at tcp://..." 和 "PublicUrl":"tcp://..."。
     */
    fun publicUrl(context: Context): String? {
        return status(context).publicUrl
    }

    /**
     * 综合解析当前状态（运行状态 + 日志尾部特征）→ 用户可读结果。
     * 这是「通用处理」核心：任何出错原因都从日志推导，直接呈现给用户。
     */
    fun status(context: Context): CpStatus {
        val tail = logTail(context, 160)
        val text = tail.joinToString("\n")
        val running = isRunning()

        // 最近一条公网地址
        val re = Regex("""(?:Tunnel established at|"PublicUrl":")(tcp://[^\s",]+)""")
        var url: String? = null
        for (line in tail) {
            for (m in re.findAll(line)) url = m.groupValues[1]
        }
        url = url?.removePrefix("tcp://")?.trim()?.takeIf { it.isNotBlank() }

        val authFailed = text.contains("authToken auth failed") || text.contains("Failed to authenticate")
        val netErr = text.contains("i/o timeout") || text.contains("read tcp") ||
            text.contains("dial tcp") || text.contains("connection refused") ||
            text.contains("Failed to start proxy")

        return when {
            url != null -> CpStatus(
                CpState.TUNNEL_UP, "公网隧道已建立", url, tail, running,
            )
            running && authFailed -> CpStatus(
                CpState.AUTH_FAILED, "认证失败：authtoken 无效或已被吊销（请检查后重试）", null, tail, true,
            )
            !running && authFailed -> CpStatus(
                CpState.AUTH_FAILED, "认证失败：authtoken 无效或已被吊销", null, tail, false,
            )
            running && netErr -> CpStatus(
                CpState.NETWORK_ERROR, "连接 cpolar 隧道服务器异常，正在自动重试…", null, tail, true,
            )
            running -> CpStatus(
                CpState.STARTING, "正在连接 cpolar 服务器…", null, tail, true,
            )
            netErr -> CpStatus(
                CpState.NETWORK_ERROR, "连接 cpolar 服务器失败：请检查网络后重试", null, tail, false,
            )
            tail.isEmpty() -> CpStatus(
                CpState.STOPPED, "未启动", null, tail, false,
            )
            else -> CpStatus(
                CpState.STOPPED, "已停止", null, tail, false,
            )
        }
    }
}
