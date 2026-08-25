package com.miolauncher.app.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Modrinth API 客户端：搜索、版本列表、前置依赖解析、真实下载链接。
 */
object ModrinthApi {

    private val gson = Gson()

    // 内存缓存：切换页签 / 反复进入不重新请求
    private val cache = HashMap<String, List<SearchHit>>()
    private val versionsCache = HashMap<String, List<ModrinthVersion>>()

    /** Modrinth 模组分类（code 与中文名） */
    val MOD_CATEGORIES = listOf(
        "optimization" to "优化",
        "adventure" to "冒险",
        "technology" to "科技",
        "magic" to "魔法",
        "combat" to "战斗",
        "gameplay" to "玩法",
        "worldgen" to "世界生成",
        "storage" to "存储",
        "decoration" to "装饰",
        "food" to "食物",
        "library" to "库",
        "misc" to "杂项",
    )

    data class LatestFile(
        val url: String,
        val filename: String,
        val size: Long,
        val versionNumber: String,
    )

    data class ModrinthFile(val url: String, val filename: String, val size: Long)

    data class ModrinthDependency(
        val projectId: String?,
        val versionId: String?,
        val dependencyType: String,
    )

    data class ModrinthVersion(
        val id: String,
        val versionNumber: String,
        val gameVersions: List<String>,
        val loaders: List<String>,
        val files: List<ModrinthFile>,
        val dependencies: List<ModrinthDependency>,
        val datePublished: String,
    )

    data class SearchHit(
        val slug: String,
        val title: String,
        val author: String,
        val description: String,
        val downloads: Long,
        val latestVersion: String,
        val iconUrl: String = "",
    )

    data class ModrinthProject(
        val slug: String,
        val title: String,
        val author: String,
        val description: String,
        val body: String,
        val downloads: Long,
        val iconUrl: String,
        val gameVersions: List<String>,
        val loaders: List<String>,
        val projectType: String,
        val gallery: List<String>,
    )

    /** API 基地址候选：国内镜像优先（mcimirror.top），官方兜底 */
    private val API_BASES = listOf(
        "https://mod.mcimirror.top/modrinth",
        "https://api.modrinth.com",
    )

    /** 文件 CDN 基地址候选：镜像优先，官方兜底 */
    private val CDN_BASES = listOf(
        "https://mod.mcimirror.top",
        "https://cdn.modrinth.com",
    )

    /** 把 api.modrinth.com 的 URL 替换到指定 base */
    private fun withBase(url: String, base: String): String =
        url.replace("https://api.modrinth.com", base)

    /** 把 cdn.modrinth.com 的 URL 替换到指定 base */
    private fun cdnWithBase(url: String, base: String): String =
        url.replace("https://cdn.modrinth.com", base)

    /** 获取可用的文件下载 URL 候选（镜像优先，官方兜底）。HEAD 检查快速失败（2s）。 */
    fun fileDownloadUrlCandidates(original: String): List<String> {
        val result = mutableListOf<String>()
        for (base in CDN_BASES) {
            val u = cdnWithBase(original, base)
            if (u == original) continue
            result.add(u)
        }
        result.add(original)
        return result
    }

    /** 获取可用的文件下载 URL（CDN 镜像优先，HEAD 快速检查） */
    fun fileDownloadUrl(original: String): String {
        for (base in CDN_BASES) {
            val u = cdnWithBase(original, base)
            if (u == original) return original
            if (HttpUtils.checkReachable(u, 2500)) return u
        }
        return original
    }

    private object HttpUtils {
        fun checkReachable(url: String, timeoutMs: Int = 5000): Boolean {
            return try {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = timeoutMs
                    conn.readTimeout = timeoutMs
                    conn.requestMethod = "HEAD"
                    conn.setRequestProperty("User-Agent", "MioLauncher/0.1.0")
                    conn.responseCode in 200..299
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun getJson(url: String): JsonObject? {
        // 并发请求镜像+官方，取先成功的（网络差时快速返回）
        return race(url) { u, c -> request(u, c) }
    }

    private fun getJsonArray(url: String): JsonArray? {
        return raceArray(url)
    }

    private fun race(url: String, req: (String, java.net.HttpURLConnection) -> JsonObject?): JsonObject? {
        val urls = API_BASES.map { withBase(url, it) }
        val exec = java.util.concurrent.Executors.newFixedThreadPool(urls.size)
        try {
            // 并发请求全部镜像/官方，谁先成功返回谁（不按顺序等待，避免镜像卡住拖慢整体）
            val completion = java.util.concurrent.ExecutorCompletionService<JsonObject?>(exec)
            val futures = urls.map { u ->
                completion.submit(
                    java.util.concurrent.Callable<JsonObject?> {
                        req(u, URL(u).openConnection() as java.net.HttpURLConnection)
                    }
                )
            }
            val deadline = System.currentTimeMillis() + 12000
            for (i in urls.indices) {
                val remain = deadline - System.currentTimeMillis()
                if (remain <= 0) break
                val r = completion.take().get(remain, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (r != null) return r
            }
            return null
        } catch (e: Exception) {
            return null
        } finally {
            exec.shutdownNow()
        }
    }

    private fun raceArray(url: String): JsonArray? {
        val urls = API_BASES.map { withBase(url, it) }
        val exec = java.util.concurrent.Executors.newFixedThreadPool(urls.size)
        try {
            // 并发请求全部镜像/官方，谁先成功返回谁（不按顺序等待，避免镜像卡住拖慢整体）
            val completion = java.util.concurrent.ExecutorCompletionService<JsonArray?>(exec)
            val futures = urls.map { u ->
                completion.submit(
                    java.util.concurrent.Callable<JsonArray?> {
                        requestArray(u, URL(u).openConnection() as java.net.HttpURLConnection)
                    }
                )
            }
            val deadline = System.currentTimeMillis() + 12000
            for (i in urls.indices) {
                val remain = deadline - System.currentTimeMillis()
                if (remain <= 0) break
                val r = completion.take().get(remain, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (r != null) return r
            }
            return null
        } catch (e: Exception) {
            return null
        } finally {
            exec.shutdownNow()
        }
    }

    private fun request(url: String, conn: java.net.HttpURLConnection): JsonObject? {
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "MioLauncher/0.1.3")
            if (conn.responseCode !in 200..299) return null
            return JsonParser.parseString(conn.inputStream.bufferedReader().use { it.readText() }).asJsonObject
        } catch (e: Exception) {
            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun requestArray(url: String, conn: java.net.HttpURLConnection): JsonArray? {
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "MioLauncher/0.1.3")
            if (conn.responseCode !in 200..299) return null
            return JsonParser.parseString(conn.inputStream.bufferedReader().use { it.readText() }).asJsonArray
        } catch (e: Exception) {
            return null
        } finally {
            conn.disconnect()
        }
    }


    /**
     * 获取项目最新且兼容指定游戏版本 / 加载器的文件。
     */
    fun latestVersion(slug: String, gameVersion: String?, loaders: List<String>): LatestFile? {
        return versions(slug, gameVersion, loaders).firstOrNull()?.let { v ->
            v.files.firstOrNull()?.let { f ->
                LatestFile(f.url, f.filename, f.size, v.versionNumber)
            }
        }
    }

    /**
     * 获取项目的版本列表（Modrinth 返回从新到旧）。
     * 可用 gameVersion + loaders 过滤兼容版本。带内存缓存。
     * @param limit 最多拉取版本数（大项目如整合包有数百版本，限制避免响应过大/镜像截断超时）
     */
    fun versions(
        slug: String,
        gameVersion: String?,
        loaders: List<String>,
        limit: Int = 0,
    ): List<ModrinthVersion> {
        val key = "$slug|$gameVersion|${loaders.sorted()}|$limit"
        synchronized(versionsCache) { versionsCache[key]?.let { return it } }
        val base = "https://api.modrinth.com/v2/project/${enc(slug)}/version"
        val params = mutableListOf<String>()
        if (!gameVersion.isNullOrBlank()) {
            params.add("game_versions=${enc("[\"$gameVersion\"]")}")
        }
        if (loaders.isNotEmpty()) {
            params.add("loaders=${enc("[\"" + loaders.joinToString("\",\"") + "\"]")}")
        }
        if (limit > 0) {
            params.add("limit=$limit")
        }
        val url = if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
        val arr = getJsonArray(url) ?: return emptyList()
        val result = arr.mapNotNull { if (it.isJsonObject) parseVersion(it.asJsonObject) else null }
        synchronized(versionsCache) { versionsCache[key] = result }
        return result
    }

    /** 按版本 ID 获取单个版本（用于解析前置依赖）。 */
    fun versionById(id: String): ModrinthVersion? {
        val obj = getJson("https://api.modrinth.com/v2/version/${enc(id)}") ?: return null
        return parseVersion(obj)
    }

    /**
     * 为前置依赖挑选一个可用的版本。
     * 先精确匹配当前游戏版本；失败则按加载器取全部版本，选与当前版本同属一个 minor 家族
     * （如 QSL 声明 "1.21"，当前版本 "1.21.11"，视为兼容）。
     */
    fun pickVersionFor(pid: String, gameVersion: String?, loaders: List<String>): ModrinthVersion? {
        if (!gameVersion.isNullOrBlank()) {
            versions(pid, gameVersion, loaders).firstOrNull()?.let { return it }
        }
        val all = versions(pid, null, loaders)
        if (gameVersion.isNullOrBlank()) return all.firstOrNull()
        return all.firstOrNull { v ->
            v.gameVersions.any { familyMatch(it, gameVersion) }
        }
    }

    private fun familyMatch(declared: String, current: String): Boolean {
        if (declared == current) return true
        if (declared.endsWith("x") || declared.endsWith("X")) {
            val prefix = declared.removeSuffix("x").removeSuffix("X").trimEnd('.')
            return current.startsWith("$prefix.")
        }
        return current.startsWith("$declared.")
    }

    /** 项目基本信息（用于前置依赖显示名称）。 */
    fun projectTitle(slugOrId: String): String? {
        val obj = getJson("https://api.modrinth.com/v2/project/${enc(slugOrId)}") ?: return null
        return if (obj.has("title") && !obj.get("title").isJsonNull) obj.get("title").asString else slugOrId
    }

    /** 项目完整详情（图标、正文、适配版本、官方画廊图）。 */
    fun projectDetails(slug: String): ModrinthProject? {
        val o = getJson("https://api.modrinth.com/v2/project/${enc(slug)}") ?: return null
        val gallery = mutableListOf<String>()
        if (o.has("gallery") && o.get("gallery").isJsonArray) {
            o.getAsJsonArray("gallery").forEach { g ->
                val go = if (g.isJsonObject) g.asJsonObject else return@forEach
                val u = str(go, "url")
                if (u.isNotBlank()) gallery.add(u)
            }
        }
        return ModrinthProject(
            slug = str(o, "slug").ifBlank { slug },
            title = str(o, "title").ifBlank { slug },
            author = str(o, "author"),
            description = str(o, "description"),
            body = str(o, "body"),
            downloads = num(o, "downloads"),
            iconUrl = str(o, "icon_url"),
            gameVersions = strArr(o, "game_versions"),
            loaders = strArr(o, "loaders"),
            projectType = str(o, "project_type"),
            gallery = gallery,
        )
    }

    /**
     * 热门资源列表（FCL 搜索源 = Modrinth，默认展示热门）。
     */
    fun popular(projectType: String, limit: Int = 30): List<SearchHit> {
        return search("", projectType, emptyList(), 0, limit)
    }

    /**
     * 搜索项目（支持分类过滤 + 分页 + 内存缓存）。
     * @param categories 分类（如 adventure / technology / optimization）
     * @param offset 分页偏移（配合 limit 实现"加载更多"）
     */
    fun search(
        query: String,
        projectType: String,
        categories: List<String> = emptyList(),
        offset: Int = 0,
        limit: Int = 30,
    ): List<SearchHit> {
        val key = "$query|$projectType|${categories.sorted()}|$offset|$limit"
        synchronized(cache) { cache[key]?.let { return it } }

        // facets = [[ "project_type:mod" ], [ "categories:xxx" ]]（嵌套数组）
        val facetList = mutableListOf<List<String>>()
        facetList.add(listOf("project_type:$projectType"))
        categories.forEach { c -> facetList.add(listOf("categories:$c")) }
        val facetStr = enc(com.google.gson.Gson().toJson(facetList))
        val q = if (query.isBlank()) "" else "&query=${enc(query)}"
        val url = "https://api.modrinth.com/v2/search?limit=$limit&offset=$offset&index=downloads$q&facets=$facetStr"
        val obj = getJson(url)
        val result = if (obj == null) emptyList()
        else obj.getAsJsonArray("hits").mapNotNull { h -> if (h.isJsonObject) hitOf(h.asJsonObject) else null }
        synchronized(cache) { cache[key] = result }
        return result
    }

    private fun hitOf(o: JsonObject): SearchHit? {
        val slug = str(o, "slug")
        if (slug.isBlank()) return null
        return SearchHit(
            slug = slug,
            title = str(o, "title"),
            author = str(o, "author"),
            description = str(o, "description"),
            downloads = num(o, "downloads"),
            latestVersion = str(o, "latest_version"),
            iconUrl = str(o, "icon_url"),
        )
    }

    private fun parseVersion(o: JsonObject): ModrinthVersion {
        val files = if (o.has("files") && o.get("files").isJsonArray)
            o.getAsJsonArray("files").mapNotNull { f ->
                val fo = if (f.isJsonObject) f.asJsonObject else return@mapNotNull null
                if (!fo.has("url") || fo.get("url").isJsonNull) null
                else ModrinthFile(
                    url = fo.get("url").asString,
                    filename = str(fo, "filename"),
                    size = num(fo, "size"),
                )
            }
        else emptyList()
        val deps = if (o.has("dependencies") && o.get("dependencies").isJsonArray)
            o.getAsJsonArray("dependencies").mapNotNull { d ->
                val doo = if (d.isJsonObject) d.asJsonObject else return@mapNotNull null
                ModrinthDependency(
                    projectId = if (doo.has("project_id") && !doo.get("project_id").isJsonNull) doo.get("project_id").asString else null,
                    versionId = if (doo.has("version_id") && !doo.get("version_id").isJsonNull) doo.get("version_id").asString else null,
                    dependencyType = str(doo, "dependency_type"),
                )
            }
        else emptyList()
        return ModrinthVersion(
            id = str(o, "id"),
            versionNumber = str(o, "version_number"),
            gameVersions = strArr(o, "game_versions"),
            loaders = strArr(o, "loaders"),
            files = files,
            dependencies = deps,
            datePublished = str(o, "date_published").take(10),
        )
    }

    /** 安全读取字符串字段（兼容 JSON null / 缺失）。 */
    private fun str(o: JsonObject, name: String): String =
        if (o.has(name) && !o.get(name).isJsonNull && o.get(name).isJsonPrimitive) o.get(name).asString else ""

    /** 安全读取数字字段（兼容 JSON null / 缺失 / 非数字）。 */
    private fun num(o: JsonObject, name: String): Long =
        if (o.has(name) && !o.get(name).isJsonNull && o.get(name).isJsonPrimitive)
            runCatching { o.get(name).asLong }.getOrDefault(0L) else 0L

    /** 安全读取字符串数组字段（兼容 JSON null / 缺失 / 非数组）。 */
    private fun strArr(o: JsonObject, name: String): List<String> =
        if (o.has(name) && o.get(name).isJsonArray)
            o.getAsJsonArray(name).mapNotNull { if (it.isJsonNull) null else it.asString }
        else emptyList()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
