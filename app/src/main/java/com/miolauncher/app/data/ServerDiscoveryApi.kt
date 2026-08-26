package com.miolauncher.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 「发现好服」服务器信息（对应 /api/discover/list 条目） */
data class DiscoverServer(
    val id: String,
    val name: String,
    val address: String,
    val version: String,
    val mode: String,
    val tags: List<String>,
    val logo: String,
    val banner: String,
    val desc: String,
    val slogan: String,
    val online: Int,
    val status: String,
    val badge: String,
    val rating: Int?,
    val reviewCount: Int,
    val good: Int,
    val bad: Int,
    val isPinned: Boolean,
    val pinRemaining: Long,
    val pinnedType: String,
    val clicks: Long,
    val joins: Long,
    val sunk: Boolean,
) {
    val hasRating: Boolean get() = rating != null
}

/** 用户评价（跑马灯/详情页用） */
data class DiscoverReview(val good: Boolean, val comment: String, val t: Long)

/** 评价提交结果（延迟生效） */
data class RateResult(val ok: Boolean, val error: String?, val effectiveAt: Long?)

/**
 * 服务器发现目录 API 客户端。
 * 基址复用引导文件/局域网/持久化域名候选；读接口公开、写接口带 X-Auth+Basic。
 */
object ServerDiscoveryApi {
    private const val LAN = "http://192.168.10.41:8787"
    private val basicHeader: String
        get() = "Basic " + android.util.Base64.encodeToString(
            "${LogUploader.BASIC_USER}:${LogUploader.BASIC_PASS}".toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )

    private fun candidates(context: Context): List<String> {
        val set = LinkedHashSet<String>()
        runCatching { PatchManager.fetchBootstrapEndpoints(context) }.getOrNull()?.forEach { set.add(it) }
        set.add(LAN)
        LogUploader.endpoints(context).forEach { set.add(it) }
        return set.toList()
    }

    private fun open(base: String, path: String, method: String, body: JSONObject?): HttpURLConnection {
        val conn = URL("$base$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 6000
        conn.readTimeout = 9000
        conn.setRequestProperty("Authorization", basicHeader)
        conn.setRequestProperty("X-Auth", LogUploader.UPLOAD_TOKEN)
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        return conn
    }

    private fun readJson(conn: HttpURLConnection): JSONObject? = runCatching {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }?.let { JSONObject(it) }
    }.getOrNull()

    private suspend fun <T> request(
        context: Context,
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        block: (JSONObject) -> T?,
    ): T? = withContext(Dispatchers.IO) {
        for (base in candidates(context)) {
            var conn: HttpURLConnection? = null
            try {
                conn = open(base, path, method, body)
                val code = conn.responseCode
                if (code in 200..299) {
                    val json = readJson(conn) ?: continue
                    val res = block(json)
                    if (res != null) return@withContext res
                } else if (code == 429) {
                    // 24h 防刷：把错误信息透传给调用方
                    val json = readJson(conn)
                    val res = json?.let { block(it) }
                    if (res != null) return@withContext res
                }
            } catch (_: Exception) {
            } finally {
                conn?.disconnect()
            }
        }
        null
    }

    fun parseServer(o: JSONObject): DiscoverServer {
        val pinned = o.optJSONObject("pinned") ?: JSONObject()
        return DiscoverServer(
            id = o.optString("id"),
            name = o.optString("name"),
            address = o.optString("address"),
            version = o.optString("version"),
            mode = o.optString("mode"),
            tags = o.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            } ?: emptyList(),
            logo = o.optString("logo"),
            banner = o.optString("banner"),
            desc = o.optString("desc"),
            slogan = o.optString("slogan"),
            online = o.optInt("online"),
            status = o.optString("status"),
            badge = o.optString("badge"),
            rating = if (o.isNull("rating")) null else o.optInt("rating", -1).takeIf { it >= 0 },
            reviewCount = o.optInt("reviewCount"),
            good = o.optInt("good"),
            bad = o.optInt("bad"),
            isPinned = o.optBoolean("isPinned"),
            pinRemaining = o.optLong("pinRemaining"),
            pinnedType = pinned.optString("type"),
            clicks = o.optLong("clicks"),
            joins = o.optLong("joins"),
            sunk = o.optBoolean("sunk"),
        )
    }

    private fun parseArray(o: JSONObject, key: String): List<DiscoverServer>? =
        o.optJSONArray(key)?.let { arr ->
            (0 until arr.length()).map { parseServer(arr.optJSONObject(it)) }
        }

    suspend fun fetchServers(
        context: Context,
        sort: String,
        tag: String? = null,
        query: String? = null,
    ): List<DiscoverServer> {
        val path = buildString {
            append("/api/discover/list?sort=$sort")
            if (!tag.isNullOrBlank()) append("&tag=${Uri.encode(tag)}")
            if (!query.isNullOrBlank()) append("&q=${Uri.encode(query)}")
        }
        return request(context, path) { parseArray(it, "servers") } ?: emptyList()
    }

    suspend fun fetchBanners(context: Context): List<DiscoverServer> =
        request(context, "/api/discover/banners") { parseArray(it, "banners") } ?: emptyList()

    suspend fun fetchDaily(context: Context, dev: String, tags: List<String>): List<DiscoverServer> {
        val tagParam = tags.joinToString(",") { Uri.encode(it) }
        return request(context, "/api/discover/daily?dev=$dev&tags=$tagParam") {
            parseArray(it, "recommendations")
        } ?: emptyList()
    }

    suspend fun fetchReviews(
        context: Context,
        ids: List<String>,
        limit: Int = 3,
    ): Map<String, List<DiscoverReview>> =
        request(context, "/api/discover/reviews?servers=${ids.joinToString(",")}&limit=$limit") { json ->
            val map = LinkedHashMap<String, List<DiscoverReview>>()
            val reviews = json.optJSONObject("reviews") ?: return@request map
            reviews.keys().forEach { sid ->
                val arr = reviews.optJSONArray(sid) ?: return@forEach
                map[sid] = (0 until arr.length()).map { i ->
                    val r = arr.optJSONObject(i)
                    DiscoverReview(r.optBoolean("good"), r.optString("comment"), r.optLong("t"))
                }
            }
            map
        } ?: emptyMap()

    suspend fun track(context: Context, dev: String, srvId: String, type: String, tags: List<String>): Boolean {
        val body = JSONObject()
            .put("dev", dev).put("srvId", srvId).put("type", type).put("tags", JSONArray(tags))
        return request(context, "/api/discover/track", "POST", body) { true } ?: false
    }

    suspend fun rate(
        context: Context,
        dev: String,
        srvId: String,
        good: Boolean,
        comment: String,
    ): RateResult {
        val body = JSONObject()
            .put("dev", dev).put("srvId", srvId).put("good", good).put("comment", comment)
        return request(context, "/api/discover/rate", "POST", body) { json ->
            RateResult(
                ok = json.optBoolean("ok", !json.has("error")),
                error = json.optString("error").ifEmpty { null },
                effectiveAt = json.optLong("effectiveAt", 0).takeIf { it > 0 },
            )
        } ?: RateResult(false, "network_error", null)
    }

    suspend fun report(context: Context, dev: String, srvId: String): Boolean {
        val body = JSONObject().put("dev", dev).put("srvId", srvId)
        return request(context, "/api/discover/report", "POST", body) { true } ?: false
    }
}
