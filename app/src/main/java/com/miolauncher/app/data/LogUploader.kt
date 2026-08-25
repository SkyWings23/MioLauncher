package com.miolauncher.app.data

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 日志上传（cpolar 内网穿透，与开服同一方案）。
 *
 * 本地 mio_log_server 跑 8787 端口，由多条 cpolar HTTP 隧道暴露公网地址。
 * 启动器内置候选域名列表，逐个尝试；服务器每次响应会返回当前全部活着的隧道域名
 * （[ENDPOINTS_URL]），客户端合并进本地列表并持久化——任一隧道挂了、域名变了，
 * 只要还有一条活着，就能自愈到新域名。
 */
object LogUploader {

    /** 服务端"域名注册表"接口：返回 {ok, endpoints:[...], ttl} */
    const val ENDPOINTS_URL = "/api/endpoints"

    /** 上传密钥（与服务器 UPLOAD_TOKEN 一致） */
    const val UPLOAD_TOKEN = "mio_upload_2024"

    /** cpolar 公网隧道 HTTP Basic 认证（局域网直连不受影响） */
    const val BASIC_USER = "mio"
    const val BASIC_PASS = "mio_admin_2024"

    /** 内置候选域名（首个是"当前主域名"，其余为备用）。
     *  含手机隧道（eff15fa，手机常开作稳定锚点）+ 平板隧道。
     *  首次连接用这些兜底，连上后从 /api/endpoints 拉最新列表并持久化。 */
    const val BUILTIN_ENDPOINTS =
        "https://eff15fa.r7.cpolar.cn,https://7fff14ba.r15.cpolar.top," +
        "https://2c93de49.r15.cpolar.top,https://1673320a.r15.cpolar.top,https://ba71887.r15.cpolar.top"

    private const val PREF = "mio_log_uploader"
    private const val KEY_ENDPOINTS = "endpoints"
    private const val KEY_LAST_HASH = "last_uploaded_hash"
    private const val MAX_ENDPOINTS = 24
    private const val MAX_PENDING = 50

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun pendingDir(context: Context): java.io.File =
        java.io.File(context.filesDir, "mio/logs/pending").apply { mkdirs() }

    /** 读取本地持久化的候选域名（内置域名保证永远兜底） */
    fun endpoints(context: Context?): List<String> {
        val saved = context?.let { prefs(it).getString(KEY_ENDPOINTS, "") }?.split("\n")
            ?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val merged = LinkedHashSet<String>()
        merged.addAll(saved)
        BUILTIN_ENDPOINTS.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { merged.add(it) }
        return merged.toList()
    }

    /** 合并服务器返回的域名列表（新域名放前面），持久化 */
    fun mergeEndpoints(context: Context?, serverList: List<String>) {
        val cleaned = serverList.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return
        val normalized = cleaned.map { e ->
            if (e.startsWith("http://") || e.startsWith("https://")) e else "https://$e"
        }
        val merged = LinkedHashSet<String>()
        normalized.forEach { merged.add(it) }
        endpoints(context).forEach { merged.add(it) }
        val list = merged.take(MAX_ENDPOINTS)
        context?.let { prefs(it).edit().putString(KEY_ENDPOINTS, list.joinToString("\n")).apply() }
    }

    /**
     * 上传日志文本到日志服务器。
     * 按本地域名列表逐个尝试，成功后把服务器返回的 endpoints 合并进本地。
     * 若全部域名都失败，则把日志存入本地 pending 缓存，待网络恢复后补发。
     * @return 查看链接；未缓存且失败返回 null，已缓存则返回特殊标记
     */
    fun upload(logText: String, device: String, version: String, context: Context? = null): String? {
        val candidates = endpoints(context)
        for (base in candidates) {
            try {
                val view = uploadOnce(base, logText, device, version, context)
                if (view != null) return view
            } catch (_: Exception) {
                // 尝试下一个域名
            }
        }
        // 全部失败 → 本地缓存，待下次补发
        if (context != null) {
            cachePending(context, logText, device, version)
            return PENDING_MARKER
        }
        return null
    }

    /** 上传失败时的本地缓存标记（外部据此提示"已缓存，联网后自动补传"） */
    const val PENDING_MARKER = "pending"

    /** 本地缓存一条待补发日志（超过上限丢弃最旧） */
    private fun cachePending(context: Context, logText: String, device: String, version: String) {
        try {
            val dir = pendingDir(context)
            val f = java.io.File(dir, "p_${System.currentTimeMillis()}_${randomSuffix()}.json")
            val obj = org.json.JSONObject()
                .put("log", logText)
                .put("device", device)
                .put("version", version)
                .put("time", System.currentTimeMillis())
            f.writeText(obj.toString())
            // 控制数量：删最旧的
            val files = dir.listFiles { it.isFile && it.name.endsWith(".json") }
                ?.sortedBy { it.lastModified() }?.toMutableList() ?: return
            while (files.size > MAX_PENDING) {
                files.first().delete()
                files.removeAt(0)
            }
        } catch (_: Exception) {}
    }

    private fun randomSuffix(): String =
        java.util.UUID.randomUUID().toString().substring(0, 6)

    /**
     * 补发本地缓存的待上传日志（联网后调用）。成功一条删一条，失败保留等下次。
     * @return 补发成功条数
     */
    fun flushPending(context: Context): Int {
        val dir = pendingDir(context)
        val files = dir.listFiles { it.isFile && it.name.endsWith(".json") }?.sortedBy { it.lastModified() }
            ?: return 0
        var sent = 0
        for (f in files) {
            try {
                val obj = org.json.JSONObject(f.readText())
                val logText = obj.optString("log")
                val device = obj.optString("device", "unknown")
                val version = obj.optString("version", "unknown")
                val ok = upload(logText, device, version, null) // 不再递归缓存
                if (ok != null && ok != PENDING_MARKER) {
                    f.delete()
                    sent++
                }
            } catch (_: Exception) {
                // 单条损坏/失败，跳过
            }
        }
        return sent
    }

    /** 当前待补发缓存条数 */
    fun pendingCount(context: Context): Int {
        return pendingDir(context).listFiles { it.isFile && it.name.endsWith(".json") }?.size ?: 0
    }

    private fun uploadOnce(
        base: String,
        logText: String,
        device: String,
        version: String,
        context: Context?,
    ): String? {
        val body = JSONObject()
            .put("log", logText)
            .put("device", device)
            .put("version", version)
            .toString()

        val conn = URL("$base/api/upload").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Auth", UPLOAD_TOKEN)
        conn.setRequestProperty(
            "Authorization",
            "Basic " + android.util.Base64.encodeToString(
                "$BASIC_USER:$BASIC_PASS".toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP,
            ),
        )
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code !in 200..299) throw java.io.IOException("HTTP $code")
        val resp = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val obj = JSONObject(resp)

        // 合并服务器返回的全部存活域名，实现自愈
        val endpoints = obj.optJSONArray("endpoints")
        if (endpoints != null && context != null) {
            val list = (0 until endpoints.length()).map { endpoints.optString(it) }
            mergeEndpoints(context, list)
        }
        return obj.optString("viewUrl").takeIf { it.isNotBlank() }
    }

    /** 收集设备信息 + 版本信息 */
    fun deviceInfo(context: Context): String {
        return try {
            val model = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
            "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · $model"
        } catch (_: Exception) {
            "unknown"
        }
    }

    /**
     * 崩溃自动上传：内容哈希去重（同一次崩溃只传一次，防止重复轰炸服务器）。
     * 先补发本地缓存的旧日志，再上传本次崩溃。失败自动进本地缓存待补发。
     * @return 查看链接；未到新崩溃（已传过）返回 null；失败已缓存返回 PENDING_MARKER
     */
    fun autoUpload(logText: String, device: String, version: String, context: Context): String? {
        // 联网条件满足时先补发历史缓存
        runCatching { flushPending(context) }
        val hash = sha256(logText)
        val last = prefs(context).getString(KEY_LAST_HASH, "")
        if (hash == last) return null
        val url = upload(logText, device, version, context)
        if (url != null && url != PENDING_MARKER) {
            prefs(context).edit().putString(KEY_LAST_HASH, hash).apply()
        }
        return url
    }

    private fun sha256(s: String): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            s.length.toString() + "_" + s.hashCode().toString()
        }
    }
}
