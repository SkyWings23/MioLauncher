package com.miolauncher.app.data

import android.content.Context
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 本机开服 + 联机隧道：
 * - 下载对应版本的 server.jar
 * - 用本机 JRE 以子进程运行 Minecraft 服务器（局域网可加入）
 * - 记录 cpolar 公网隧道地址（跨网络联机）
 */
object ServerHost {

    class ServerException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var process: Process? = null

    private const val PREF = "mio_online"
    private const val KEY_TUNNEL = "tunnel_address"
    const val RCON_PORT = 25575
    const val RCON_PASSWORD = "mio_rcon"

    fun serverDir(context: Context): File =
        File(MioRepository(context).gameDir, "server").apply { mkdirs() }

    fun serverJar(context: Context): File = File(serverDir(context), "server.jar")

    fun serverJarReady(context: Context): Boolean =
        serverJar(context).isFile && serverJar(context).length() > 1_000_000

    fun isRunning(): Boolean = process?.isAlive == true

    // ---- 日志 ----
    fun clearLogs() { _logs.value = emptyList() }

    private fun appendLog(line: String) {
        val list = _logs.value.toMutableList()
        list.add(line)
        if (list.size > 200) list.removeAt(0)
        _logs.value = list
    }

    // ---- cpolar 隧道 ----
    fun tunnelAddress(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_TUNNEL, "") ?: ""

    fun setTunnelAddress(context: Context, address: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_TUNNEL, address.trim()).apply()
    }

    /** 本机局域网 IP（供局域网好友加入） */
    fun lanIp(): String? {
        return try {
            val enums = NetworkInterface.getNetworkInterfaces()
            while (enums.hasMoreElements()) {
                val intf = enums.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (!ip.startsWith("169.254")) return ip
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // ---- server.jar 下载 ----
    /**
     * 从 BMCLAPI 下载对应版本的 server.jar。
     */
    fun ensureServerJar(
        context: Context,
        versionId: String,
        onProgress: (Float) -> Unit = {},
    ) {
        val target = serverJar(context)
        if (target.isFile && target.length() > 1_000_000) return
        appendLog("下载 server.jar（$versionId）…")
        val url = "https://bmclapi2.bangbang93.com/version/$versionId/server"
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "MioLauncher/0.1.0")
            if (conn.responseCode !in 200..299) {
                throw ServerException("下载 server.jar 失败 HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            var done = 0L
            val tmp = File(target.parentFile, "server.jar.tmp")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(65536)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toDouble() / total).toFloat())
                    }
                }
            }
            if (tmp.length() < 1_000_000) {
                tmp.delete()
                throw ServerException("server.jar 文件不完整")
            }
            tmp.renameTo(target)
            appendLog("server.jar 下载完成（${DownloadManager.formatBytes(target.length())}）")
        } catch (e: ServerException) {
            throw e
        } catch (e: Exception) {
            throw ServerException("下载 server.jar 失败：${e.message}", e)
        } finally {
            conn.disconnect()
        }
    }

    // ---- 启动/停止服务器（独立 :server 进程，进程内 JVM 运行） ----
    fun start(context: Context, port: Int, memoryMb: Int, onlineMode: Boolean) {
        if (isRunning()) return
        val jar = serverJar(context)
        if (!jar.isFile || jar.length() < 1_000_000) {
            throw ServerException("请先下载 server.jar")
        }
        // 取一个已安装版本号（用于记录，服务端实际按 server.jar 运行）
        val versionId = runCatching {
            MioRepository(context).loadInstalledVersions().firstOrNull()?.id
        }.getOrNull() ?: "server"
        clearLogs()
        appendLog("启动服务器（端口 $port）…")
        val intent = com.miolauncher.app.ServerService.startIntent(context, versionId, port, memoryMb, onlineMode)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        appendLog("正在停止服务器…")
        context.startService(com.miolauncher.app.ServerService.stopIntent(context))
    }

    /** 服务器是否在运行（:server 进程标记文件 + 真实进程存活校验） */
    fun isRunning(context: Context): Boolean {
        val marker = com.miolauncher.app.ServerService.runningMarker(context)
        if (!marker.exists()) return false
        val pid = runCatching {
            marker.readText().substringAfter("pid=").substringBefore("\n").trim().toInt()
        }.getOrNull() ?: return false
        return File("/proc/$pid").exists()
    }

    /** 服务器最新日志文件（:server 进程内 JVM stdout 也进该文件） */
    fun logFile(context: Context): File = File(serverDir(context), "logs/latest.log")

    /** 读取日志尾部（供 UI 轮询显示） */
    fun tailLog(context: Context, maxLines: Int = 120): List<String> {
        return try {
            val f = logFile(context)
            if (!f.isFile) return emptyList()
            val lines = f.readLines()
            lines.takeLast(maxLines)
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---- 在线玩家管理（从日志解析 + RCON） ----
    private val _onlinePlayers = MutableStateFlow<List<String>>(emptyList())
    val onlinePlayers: StateFlow<List<String>> = _onlinePlayers

    /** 解析服务器日志提取在线玩家列表 */
    fun parseOnlinePlayers(logLines: List<String>): List<String> {
        val joined = mutableSetOf<String>()
        for (line in logLines) {
            // 匹配 "logged in with entity id" 或 "joined the game"
            if (line.contains("logged in") || line.contains("joined the game")) {
                // 提取玩家名：格式为 "PlayerName[/ip:port] logged in" 或 "PlayerName joined the game"
                val match = Regex("""(\w+)(?:\[/[\d.:]+\])?\s+(?:logged in|joined)""").find(line)
                if (match != null) joined.add(match.groupValues[1])
            }
            // 匹配 "left the game" 或 "disconnected"
            if (line.contains("left the game") || line.contains("disconnected")) {
                val match = Regex("""(\w+)\s+(?:left|disconnected)""").find(line)
                if (match != null) joined.remove(match.groupValues[1])
            }
        }
        return joined.toList()
    }

    /** 更新在线玩家列表（由日志轮询调用） */
    fun refreshOnlinePlayers(context: Context) {
        val lines = tailLog(context, 500)
        _onlinePlayers.value = parseOnlinePlayers(lines)
    }

    /** 通过 RCON 发送命令到本地服务器 */
    fun sendRconCommand(context: Context, command: String): RconClient.RconResult {
        if (!isRunning(context)) return RconClient.RconResult(false, "服务器未运行")
        val host = "127.0.0.1"
        return RconClient.execute(host, RCON_PORT, RCON_PASSWORD, command)
    }

    /** 获取在线玩家列表（通过 RCON list 命令） */
    fun getOnlinePlayersRcon(context: Context): String {
        val result = sendRconCommand(context, "list")
        return if (result.success) result.body else "获取失败"
    }

    /** 踢出玩家 */
    fun kickPlayer(context: Context, player: String, reason: String = "被管理员踢出"): RconClient.RconResult {
        return sendRconCommand(context, "kick $player $reason")
    }

    /** 设为管理员 */
    fun opPlayer(context: Context, player: String): RconClient.RconResult {
        return sendRconCommand(context, "op $player")
    }

    /** 取消管理员 */
    fun deopPlayer(context: Context, player: String): RconClient.RconResult {
        return sendRconCommand(context, "deop $player")
    }

    /** 服务器根目录可分享信息 */
    fun shareInfo(context: Context, port: Int): String {
        val lan = lanIp()
        val tunnel = tunnelAddress(context)
        val sb = StringBuilder()
        if (lan != null) {
            sb.append("局域网地址：$lan:$port\n")
        }
        if (tunnel.isNotBlank()) {
            sb.append("公网地址：$tunnel\n")
        }
        if (sb.isEmpty()) sb.append("未检测到局域网 IP")
        return sb.toString().trimEnd()
    }
}
