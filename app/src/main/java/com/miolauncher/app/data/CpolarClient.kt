package com.miolauncher.app.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL

/**
 * cpolar 本地客户端 API 客户端（ngrok 兼容格式）。
 * cpolar 在设备/局域网机器上运行后，会暴露本地 API（默认 127.0.0.1:4040）。
 * 通过它创建 TCP 隧道并读取公网地址，实现"一键获取公网地址"。
 */
object CpolarClient {

    class CpolarException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** 本地 API 基础地址（默认 127.0.0.1:4040；cpolar 在别的机器上则填该机器 IP） */
    const val DEFAULT_BASE = "http://127.0.0.1:4040"

    private fun json(method: String, url: String, body: String?): JsonObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = method
            conn.setRequestProperty("User-Agent", "MioLauncher/0.1.0")
            if (body != null) {
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw CpolarException("cpolar API HTTP $code：$text")
            }
            return if (text.isBlank()) null else JsonParser.parseString(text).asJsonObject
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 列出当前在线隧道。
     */
    fun getTunnels(base: String = DEFAULT_BASE): List<JsonObject> {
        val obj = json("GET", "$base/api/tunnels", null)
            ?: throw CpolarException("无法访问 cpolar 本地 API（$base）。请确认 cpolar 已启动。")
        val arr = obj.getAsJsonArray("tunnels") ?: return emptyList()
        return arr.map { it.asJsonObject }
    }

    /**
     * 创建 TCP 隧道（指向本机 port），已存在同名隧道则跳过。
     */
    fun createTcpTunnel(base: String, port: Int, name: String = "mio-mc"): JsonObject {
        val existing = getTunnels(base).firstOrNull {
            it.get("name")?.asString == name ||
                it.get("config")?.asJsonObject?.get("addr")?.asString?.endsWith(":$port") == true
        }
        if (existing != null) return existing
        val body = JsonObject().apply {
            addProperty("name", name)
            addProperty("proto", "tcp")
            addProperty("addr", port.toString())
        }.toString()
        return json("POST", "$base/api/tunnels", body)
            ?: throw CpolarException("创建隧道失败（无响应）")
    }

    /**
     * 获取 TCP 隧道的公网地址（如 tcp://6.tcp.cpolar.top:10577）。
     */
    fun tcpPublicUrl(base: String, port: Int, name: String = "mio-mc"): String? {
        // 先尝试创建（幂等），再读地址
        val created = runCatching { createTcpTunnel(base, port, name) }.getOrNull()
        val candidates = mutableListOf<JsonObject>()
        if (created != null) candidates.add(created)
        candidates.addAll(getTunnels(base))

        for (t in candidates) {
            val url = t.get("public_url")?.asString
            if (url != null && (url.startsWith("tcp://") || url.startsWith("TCP"))) {
                // 归一化：去掉 tcp:// 前缀（MC 服务器地址不需要）
                return url.removePrefix("tcp://").removePrefix("TCP://").trim()
            }
        }
        // 某些版本字段在 config 里
        for (t in candidates) {
            val cfg = t.getAsJsonObject("config")
            val addr = cfg?.get("addr")?.asString
            if (addr != null && addr.endsWith(":$port")) {
                val url = t.get("public_url")?.asString
                if (!url.isNullOrBlank()) return url.removePrefix("tcp://").removePrefix("TCP://").trim()
            }
        }
        return null
    }

    /**
     * 轮询等待 TCP 隧道公网地址（cpolar 刚启动时 API 未就绪，需重试）。
     *
     * @param attempts 最多尝试次数
     * @param delayMs  每次重试间隔
     */
    fun waitForPublicUrl(
        base: String,
        port: Int,
        name: String = "mio-mc",
        attempts: Int = 12,
        delayMs: Long = 1500,
    ): String? {
        for (i in 0 until attempts) {
            try {
                val url = tcpPublicUrl(base, port, name)
                if (url != null) return url
            } catch (e: Exception) {
                // API 未就绪，重试
            }
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                return null
            }
        }
        return null
    }
}
