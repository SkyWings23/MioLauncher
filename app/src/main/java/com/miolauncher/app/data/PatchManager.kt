package com.miolauncher.app.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 补丁热更新：从日志服务器拉取补丁清单，下载并应用到 runtime 目录。
 *
 * 可热更新的文件：assets/runtime 下每次解压的 jar（OshiPatch.jar、MioLibPatcher.jar、
 * MioExitAgent.jar、DumpAgent.jar、lwjgl.jar、lwjglx.jar、authlib-injector.jar 等）。
 * 这些文件在游戏启动前由 extractRuntime() 解压到 files/mio/runtime/，因此只要在
 * extractRuntime 之后、JVM 启动之前用补丁覆盖同名文件即可生效，无需重装 APK。
 */
/** 增量更新补丁项：从指定基准版本(code) 用 bsdiff 补丁直达最新版。 */
data class IncrementalPatch(
    val baseCode: Int,       // 基准版本 code（客户端需持有该版本 APK 副本）
    val file: String,        // 补丁文件名
    val size: Long,          // 补丁大小
)

/** 完整 APK 更新信息（不可热更新的内容通过整包更新下发）。 */
data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val file: String,
    val sha256: String,
    val size: Long,
    val desc: String,
    /** 增量更新（兼容单个补丁）：旧版→新版的 bsdiff 补丁文件名（为空则需完整下载） */
    val patchFile: String = "",
    /** 增量更新：补丁的基准版本 code（客户端本地需持有该版本 APK 副本才能用补丁） */
    val patchBaseCode: Int = 0,
    /** 增量更新：补丁大小 */
    val patchSize: Long = 0L,
    /** 增量更新（多版本直达补丁列表）：客户端按本地持有的 APK 副本版本匹配 */
    val patchList: List<IncrementalPatch> = emptyList(),
)

object PatchManager {

    private const val PREF = "mio_patches"
    private const val KEY_INSTALLED = "installed"  // JSON: {"<target>": "<version>"}
    private const val KEY_SKIPPED = "skipped"       // JSON: ["<target>..."] 用户点"忽略"的

    /**
     * App 进程内共享的更新检查缓存：启动时（开机动画期间）检查一次并缓存，
     * 所有页面/组件复用，避免切页后重新拉取版本导致重复加载。
     * null = 尚未检查；非 null = 已检查（无更新时为特殊"无更新"标记）。
     */
    @Volatile
    private var cachedAppUpdate: AppUpdate? = null
    private val updateCheckLock = Any()

    /**
     * 检查 App 更新（进程内只查一次，结果缓存）。
     * 启动时调用一次即可，后续 fetchAppUpdateCached() 直接返回缓存结果。
     */
    fun checkAppUpdateOnce(context: Context) {
        if (cachedAppUpdate != null) return
        synchronized(updateCheckLock) {
            if (cachedAppUpdate != null) return
            try {
                cachedAppUpdate = fetchAppUpdate(context)
            } catch (_: Throwable) {
                cachedAppUpdate = AppUpdate("", 0, "", "", 0L, "")  // 无更新标记
            }
        }
    }

    /** 返回已缓存的更新信息；null 表示无更新或未检查。 */
    fun fetchAppUpdateCached(): AppUpdate? =
        cachedAppUpdate?.takeIf { it.versionCode > 0 }

    /** 清除缓存（供调试/重试） */
    fun clearUpdateCache() {
        cachedAppUpdate = null
    }

    /**
     * endpoint 列表进程内缓存：App 生命周期内只完整探测一次，
     * 避免每次打开更新中心/检查更新都串行探测所有隧道导致卡顿。
     */
    @Volatile
    private var cachedEndpoints: List<String>? = null
    private val endpointLock = Any()

    /** 局域网候选（服务器主机，平板）；与 cpolar 域名并列兜底。 */
    private const val LAN_ENDPOINTS = "http://192.168.10.41:8787"

    /**
     * 引导文件（bootstrap）：永不失效的外部锚点。
     * 由平板/手机服务端定期把当前存活隧道列表写入 GitHub 仓库 mio/endpoints.json，
     * 客户端经 jsDelivr CDN（国内可达）读取。即使硬编码/持久化域名全部失效，
     * 也能从这里发现新的随机 cpolar 隧道，解决"自举死锁"。
     * ?v= 取 5 分钟粒度时间戳，绕过 jsDelivr 的 12h 缓存。
     */
    private val BOOTSTRAP_URLS = listOf(
        "https://cdn.jsdelivr.net/gh/SkyWings23/MioLauncher@main/mio/endpoints.json?v={t}",
        "https://raw.githubusercontent.com/SkyWings23/MioLauncher/main/mio/endpoints.json?v={t}",
    )

    /**
     * 从引导文件拉取当前存活隧道列表（jsDelivr/GitHub raw，永不失效的外部锚点）。
     * 返回规范化 URL 列表；失败返回空列表（不抛异常）。
     */
    fun fetchBootstrapEndpoints(context: Context?): List<String> {
        val found = java.util.Collections.synchronizedList(ArrayList<String>())
        try {
            val ts = System.currentTimeMillis() / 300000L  // 5 分钟粒度，绕过 CDN 缓存
            val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
            val futures = BOOTSTRAP_URLS.map { tpl ->
                pool.submit {
                    try {
                        val url = tpl.replace("{t}", ts.toString())
                        val conn = URL(url).openConnection() as HttpURLConnection
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        conn.setRequestProperty("User-Agent", "MioLauncher/bootstrap")
                        try {
                            val code = conn.responseCode
                            if (code in 200..299) {
                                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                                val arr = JSONObject(text).optJSONArray("endpoints")
                                if (arr != null) {
                                    val fetched = ArrayList<String>()
                                    for (i in 0 until arr.length()) {
                                        val e = arr.optString(i)
                                        if (e.isNotBlank()) {
                                            val norm = if (e.startsWith("http")) e else "https://$e"
                                            found.add(norm)
                                            fetched.add(norm)
                                        }
                                    }
                                    if (fetched.isNotEmpty() && context != null) {
                                        LogUploader.mergeEndpoints(context, fetched)
                                    }
                                    android.util.Log.i("PatchManager", "bootstrap: ${fetched.size} endpoints from $url")
                                }
                            }
                        } finally {
                            conn.disconnect()
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            futures.forEach { runCatching { it.get() } }
            pool.shutdown()
        } catch (_: Exception) {
        }
        return found
    }

    /** 补丁下载域名候选：局域网优先，其次 cpolar 多域名。 */
    private fun patchEndpoints(context: Context): List<String> {
        // 进程内缓存：启动后首次完整探测一次，后续直接复用，避免重复串行探测卡顿
        cachedEndpoints?.let { return it }
        synchronized(endpointLock) {
            cachedEndpoints?.let { return it }
            val result = buildEndpoints(context)
            cachedEndpoints = result
            return result
        }
    }

    /** 构建 endpoint 候选列表（引导文件 + 局域网 + 本地持久化 + 服务器注册表）。 */
    private fun buildEndpoints(context: Context): List<String> {
        val merged = LinkedHashSet<String>()
        // 引导文件优先：永不失效的外部锚点，硬编码/持久化域名全挂时也能发现新隧道。
        // 失败静默，不影响后续本地/内置域名流程。
        fetchBootstrapEndpoints(context).forEach { merged.add(it) }
        LAN_ENDPOINTS.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { merged.add(it) }
        LogUploader.endpoints(context).forEach { merged.add(it) }
        // 主动拉取服务器最新隧道列表（含手机/平板多隧道），合并进候选域名。
        // 并行探测所有已知域名，取最快成功的响应，避免串行等待拖慢启动。
        try {
            val probeList = merged.toList()
            val lock = java.util.concurrent.atomic.AtomicBoolean(false)
            val pool = java.util.concurrent.Executors.newFixedThreadPool(minOf(probeList.size, 8))
            val futures = probeList.map { ep ->
                pool.submit {
                    if (lock.get()) return@submit
                    var conn: HttpURLConnection? = null
                    try {
                        conn = URL("$ep/api/endpoints").openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        conn.setRequestProperty(
                            "Authorization",
                            "Basic " + Base64.encodeToString(
                                "${LogUploader.BASIC_USER}:${LogUploader.BASIC_PASS}".toByteArray(Charsets.UTF_8),
                                Base64.NO_WRAP,
                            ),
                        )
                        val code = conn.responseCode
                        if (code in 200..299) {
                            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                            val arr = JSONObject(text).optJSONArray("endpoints")
                            if (arr != null && lock.compareAndSet(false, true)) {
                                val fetched = ArrayList<String>()
                                for (i in 0 until arr.length()) {
                                    val e = arr.optString(i)
                                    if (e.isNotBlank()) {
                                        val norm = if (e.startsWith("http")) e else "https://$e"
                                        synchronized(merged) {
                                            merged.add(norm)
                                        }
                                        fetched.add(norm)
                                    }
                                }
                                // 持久化本次拉取到的全部隧道域名，供下次启动/平板重启后复用
                                if (fetched.isNotEmpty()) LogUploader.mergeEndpoints(context, fetched)
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        conn?.disconnect()
                    }
                }
            }
            futures.forEach { runCatching { it.get() } }
            pool.shutdown()
        } catch (_: Exception) {
        }
        // 过滤对客户端永远无效的地址：127.0.0.1 / localhost 指向客户端自身，公网玩家也连不上 192.168 局域网。
        return merged.filter { ep ->
            val host = ep.substringAfter("://", ep).substringBefore("/").substringBefore(":")
            !host.startsWith("127.") && !host.equals("localhost", true) && !host.startsWith("0.")
        }
    }

    fun patchDir(context: Context): File =
        File(context.filesDir, "mio/patch").apply { mkdirs() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    @Synchronized
    fun installed(context: Context): Map<String, String> {
        val raw = prefs(context).getString(KEY_INSTALLED, "")
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(raw)
            val m = LinkedHashMap<String, String>()
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next() as String
                m[k] = o.optString(k)
            }
            m
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveInstalled(context: Context, map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs(context).edit().putString(KEY_INSTALLED, o.toString()).apply()
    }

    /**
     * 拉取补丁清单。返回 null 表示网络失败（不打断启动流程）。
     * 用与 LogUploader 相同的多域名注册表，逐个尝试。
     */
    fun fetchManifest(context: Context): List<JSONObject> {
        for (base in patchEndpoints(context)) {
            try {
                val conn = URL("$base/api/patches").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 12000
                conn.readTimeout = 12000
                conn.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.encodeToString(
                        "${LogUploader.BASIC_USER}:${LogUploader.BASIC_PASS}".toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP,
                    ),
                )
                val code = conn.responseCode
                if (code in 200..299) {
                    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val arr = JSONObject(text).optJSONArray("patches") ?: JSONArray()
                    val list = ArrayList<JSONObject>()
                    for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                    if (list.isNotEmpty() || text.contains("\"patches\"")) return list
                }
            } catch (_: Exception) {
                // 试下一个域名
            }
        }
        return emptyList()
    }

    /**
     * 拉取完整 APK 更新信息。返回 null 表示服务器没有可用的新版本（或网络失败）。
     */
    fun fetchAppUpdate(context: Context): AppUpdate? {
        for (base in patchEndpoints(context)) {
            try {
                val conn = URL("$base/api/patches").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 12000
                conn.readTimeout = 12000
                conn.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.encodeToString(
                        "${LogUploader.BASIC_USER}:${LogUploader.BASIC_PASS}".toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP,
                    ),
                )
                val code = conn.responseCode
                if (code in 200..299) {
                    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val app = JSONObject(text).optJSONObject("app") ?: continue
                    val versionCode = app.optInt("versionCode", 0)
                    if (versionCode <= 0) continue
                    val currentCode = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                    } catch (_: Exception) {
                        return null
                    }
                    if (versionCode <= currentCode) return null  // 已是最新
                    val patchList = ArrayList<IncrementalPatch>()
                    val patchArr = app.optJSONArray("patchList")
                    if (patchArr != null) {
                        for (i in 0 until patchArr.length()) {
                            val po = patchArr.optJSONObject(i) ?: continue
                            val base = po.optInt("baseCode", 0)
                            val file = po.optString("file", "")
                            if (base > 0 && file.isNotBlank()) {
                                patchList.add(IncrementalPatch(base, file, po.optLong("size", 0L)))
                            }
                        }
                    }
                    return AppUpdate(
                        versionName = app.optString("versionName", ""),
                        versionCode = versionCode,
                        file = app.optString("file", ""),
                        sha256 = app.optString("sha256", ""),
                        size = app.optLong("size", 0L),
                        desc = app.optString("desc", ""),
                        patchFile = app.optString("patchFile", ""),
                        patchBaseCode = app.optInt("patchBaseCode", 0),
                        patchSize = app.optLong("patchSize", 0L),
                        patchList = patchList,
                    )
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** 计算需要下载/更新的补丁列表（未安装、或已装版本低于服务器版本）。 */
    fun pendingPatches(context: Context, manifest: List<JSONObject>): List<JSONObject> {
        val installed = installed(context)
        val skipped = try {
            val a = JSONArray(prefs(context).getString(KEY_SKIPPED, "[]"))
            (0 until a.length()).map { a.optString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
        // 当前 App 版本：补丁声明 minAppVersion 时，App 版本过低则不应用（避免旧版加载不兼容补丁）
        val currentCode = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (_: Exception) {
            Int.MAX_VALUE
        }
        return manifest.filter { p ->
            val target = p.optString("target")
            val serverVer = p.optString("version")
            val localVer = installed[target]
            val minVer = p.optLong("minAppVersion", 0L)
            // 需要更新：服务器版本存在且比本地新（本地缺失或版本号更小），
            // 且当前 App 版本满足补丁的最低版本要求
            !skipped.contains(target) &&
                serverVer.isNotBlank() &&
                currentCode >= minVer &&
                (localVer == null || versionCompare(serverVer, localVer) > 0)
        }
    }

    /**
     * 下载并安装补丁。成功返回 true。
     * 下载到 patch 目录并校验 SHA256，然后复制到 runtime 目录同名文件。
     * @param onProgress 进度回调：(已下载字节, 总字节, 下载速度字节/秒, 完成百分比0~100)
     */
    fun applyPatch(
        context: Context,
        patch: JSONObject,
        onProgress: (Long, Long, Long, Int) -> Unit = { _, _, _, _ -> },
    ): Boolean {
        val file = patch.optString("file")
        val target = patch.optString("target")
        val sha = patch.optString("sha256")
        val version = patch.optString("version")
        if (file.isBlank() || target.isBlank()) return false

        val dest = File(patchDir(context), file)
        var ok = false
        for (base in patchEndpoints(context)) {
            try {
                val conn = URL("$base/api/patches/$file").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 30000
                conn.readTimeout = 60000
                conn.setRequestProperty(
                    "Authorization",
                    "Basic " + Base64.encodeToString(
                        "${LogUploader.BASIC_USER}:${LogUploader.BASIC_PASS}".toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP,
                    ),
                )
                val code = conn.responseCode
                if (code !in 200..299) continue
                val total = conn.contentLengthLong
                val input = conn.inputStream
                val tmp = File(dest.parentFile, dest.name + ".tmp")
                tmp.outputStream().use { out ->
                    val buf = ByteArray(65536)
                    var read: Int
                    var written = 0L
                    val startMs = System.currentTimeMillis()
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        written += read
                        val elapsedSec = (System.currentTimeMillis() - startMs) / 1000.0
                        val speed = if (elapsedSec > 0) (written / elapsedSec).toLong() else 0L
                        val pct = if (total > 0) ((written * 100) / total).toInt().coerceIn(0, 100) else 0
                        onProgress(written, total, speed, pct)
                    }
                }
                input.close()
                // SHA256 校验
                if (sha.isNotBlank()) {
                    val actual = sha256File(tmp)
                    if (!actual.equals(sha, ignoreCase = true)) {
                        tmp.delete()
                        continue
                    }
                }
                if (tmp.renameTo(dest) || (dest.exists() && dest.delete() && tmp.renameTo(dest))) {
                    // 复制到 runtime 目录同名文件
                    val runtime = File(context.filesDir, "mio/runtime/$target")
                    runtime.parentFile?.mkdirs()
                    dest.copyTo(runtime, overwrite = true)
                    markInstalled(context, target, version)
                    ok = true
                }
                break
            } catch (e: Exception) {
                android.util.Log.w("PatchManager", "applyPatch $file via $base failed", e)
                // 下一个域名
            }
        }
        return ok
    }

    /**
     * 批量下载并安装补丁，逐个下载。
     * @param onPatchStarted 每个补丁开始时回调：(当前序号, 总数, 补丁描述)
     * @param onProgress 单个补丁下载进度：(已下载, 总大小, 速度, 百分比)
     * @return 成功安装的补丁数
     */
    fun applyPatches(
        context: Context,
        patches: List<JSONObject>,
        onPatchStarted: (Int, Int, String) -> Unit = { _, _, _ -> },
        onProgress: (Long, Long, Long, Int) -> Unit = { _, _, _, _ -> },
    ): Int {
        var okCount = 0
        for ((i, p) in patches.withIndex()) {
            onPatchStarted(i + 1, patches.size, p.optString("desc", ""))
            if (applyPatch(context, p, onProgress)) okCount++
        }
        return okCount
    }

    /**
     * 下载完整 APK 更新包到缓存目录，SHA256 校验后返回文件。
     * 使用多线程分块下载（Range 分片）加速，支持断点续传。
     * @param onProgress (已下载, 总大小, 速度, 百分比)
     * @return 下载完成的 APK 文件；失败返回 null
     */
    fun downloadAppApk(
        context: Context,
        update: AppUpdate,
        onProgress: (Long, Long, Long, Int) -> Unit = { _, _, _, _ -> },
    ): File? {
        // ---- 增量更新（bsdiff）：本地有匹配的旧 APK 副本且服务器提供补丁 → 只下载补丁（几 MB）----
        // 优先用多版本补丁列表匹配本地副本；否则回退到单个 patchFile
        val matchPatch = findMatchPatch(context, update)
        if (matchPatch != null) {
            val (oldApk, patch) = matchPatch
            android.util.Log.i("PatchManager", "增量更新: 本地有旧版(${patch.baseCode}) APK, 下载补丁 ${patch.file} (${patch.size / 1024}KB)")
            val result = applyIncrementalUpdate(context, update, oldApk, patch.file, patch.size, onProgress)
            if (result != null) return result
            android.util.Log.w("PatchManager", "增量更新失败, 回退完整下载")
        } else if (update.patchFile.isNotBlank() && update.patchBaseCode > 0) {
            val oldApk = savedApkFile(context, update.patchBaseCode)
            if (oldApk != null && oldApk.isFile) {
                android.util.Log.i("PatchManager", "增量更新: 本地有旧版(${update.patchBaseCode}) APK, 下载补丁 ${update.patchFile}")
                val result = applyIncrementalUpdate(context, update, oldApk, update.patchFile, update.patchSize, onProgress)
                if (result != null) return result
                android.util.Log.w("PatchManager", "增量更新失败, 回退完整下载")
            }
        }
        // 整体重试：慢隧道导致部分分片失败时，第二次重试（慢隧道已被淘汰）通常成功。
        // 最多尝试 2 次，避免无限重试拖时间。重试时保留 tmp（断点续传）。
        var lastResult: File? = null
        for (attempt in 1..2) {
            if (com.miolauncher.app.UpdateDownloadService.isCancelRequested()) return null
            lastResult = downloadAppApkOnce(context, update, onProgress)
            if (lastResult != null) return lastResult
            if (attempt == 1) {
                android.util.Log.w("PatchManager", "download attempt $attempt failed, retrying once (resume from tmp)")
            }
        }
        return lastResult
    }

    /** 已保存的指定版本 APK 副本路径（用于增量更新基准），无则返回 null。 */
    private fun savedApkFile(context: Context, versionCode: Int): File? {
        val dir = File(context.filesDir, "mio/old_apk")
        val f = File(dir, "mio_update_$versionCode.apk")
        return if (f.isFile && f.length() > 0) f else null
    }

    /** 安装完成后保存一份 APK 副本供下次增量更新使用。 */
    fun saveApkCopy(context: Context, src: File, versionCode: Int) {
        try {
            val dir = File(context.filesDir, "mio/old_apk")
            dir.mkdirs()
            val dst = File(dir, "mio_update_$versionCode.apk")
            src.copyTo(dst, overwrite = true)
            android.util.Log.i("PatchManager", "已保存 APK 副本 (code $versionCode) 供增量更新")
            // 清理更旧的副本，只保留最近 3 个（多版本直达补丁需要更多历史副本匹配）
            val files = dir.listFiles { f -> f.isFile && f.name.startsWith("mio_update_") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
            for (f in files.drop(3)) f.delete()
        } catch (e: Exception) {
            android.util.Log.w("PatchManager", "保存 APK 副本失败", e)
        }
    }

    /** 从补丁列表中找到与本地持有副本匹配的补丁（返回副本文件 + 补丁项）。 */
    private fun findMatchPatch(context: Context, update: AppUpdate): Pair<File, IncrementalPatch>? {
        // 先试多版本补丁列表（优先匹配，跨度大）
        for (p in update.patchList) {
            val oldApk = savedApkFile(context, p.baseCode)
            if (oldApk != null && oldApk.isFile) return oldApk to p
        }
        return null
    }

    /**
     * 增量更新：下载 bsdiff 补丁 → 本地用旧 APK 还原出新 APK → SHA 校验。
     * 成功后返回新 APK 文件；失败返回 null（调用方回退完整下载）。
     */
    private fun applyIncrementalUpdate(
        context: Context,
        update: AppUpdate,
        oldApk: File,
        patchFileName: String,
        patchFileSize: Long,
        onProgress: (Long, Long, Long, Int) -> Unit,
    ): File? {
        try {
            // 1. 下载补丁（多隧道候选，单次 Range 完整下载——补丁很小）
            val dest = File(context.cacheDir, "mio_update_${update.versionCode}.apk")
            val patchFile = File(context.cacheDir, "mio_patch_${update.versionCode}.diff")
            var downloaded = false
            for (base in patchEndpoints(context)) {
                try {
                    val conn = URL("$base/api/patches/$patchFileName").openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 10000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("Authorization", authHeader())
                    conn.setRequestProperty("User-Agent", "MioLauncher/updater")
                    val code = conn.responseCode
                    if (code !in 200..299) continue
                    val total = conn.contentLengthLong
                    val input = conn.inputStream
                    val tmp = File(patchFile.parentFile, patchFile.name + ".tmp")
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(65536)
                        var written = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            onProgress(written, if (total > 0) total else patchFileSize, 0L, 0)
                        }
                    }
                    input.close()
                    if (tmp.renameTo(patchFile)) {
                        downloaded = true
                        break
                    }
                } catch (e: Exception) {
                    android.util.Log.w("PatchManager", "补丁下载失败 $base", e)
                }
            }
            if (!downloaded) {
                android.util.Log.w("PatchManager", "补丁下载失败, 回退完整下载")
                return null
            }

            // 2. bspatch 还原
            onProgress(0, update.size, 0L, 1)
            val tmpNew = File(dest.parentFile, dest.name + ".tmp")
            if (tmpNew.exists()) tmpNew.delete()
            Bspatch.apply(oldApk, tmpNew, patchFile)
            android.util.Log.i("PatchManager", "bspatch 还原完成: ${tmpNew.length()} bytes")

            // 3. SHA256 校验
            if (update.sha256.isNotBlank()) {
                val actual = sha256File(tmpNew)
                if (!actual.equals(update.sha256, ignoreCase = true)) {
                    android.util.Log.w("PatchManager", "增量更新 SHA 不符: $actual vs ${update.sha256}")
                    tmpNew.delete()
                    return null
                }
            }
            // 4. 完成
            onProgress(update.size, update.size, 0L, 100)
            if (tmpNew.renameTo(dest) || (dest.exists() && dest.delete() && tmpNew.renameTo(dest))) {
                return dest
            }
            return null
        } catch (e: Exception) {
            android.util.Log.w("PatchManager", "增量更新异常, 回退完整下载", e)
            return null
        }
    }

    private fun downloadAppApkOnce(
        context: Context,
        update: AppUpdate,
        onProgress: (Long, Long, Long, Int) -> Unit = { _, _, _, _ -> },
    ): File? {
        val dest = File(context.cacheDir, "mio_update_${update.versionCode}.apk")
        // 多隧道并行：局域网 + 全部 cpolar 公网隧道，分片按 index 轮询分配，
        // 突破单隧道限速（cpolar 每条隧道独立限速 ~130KB/s，N 条并行叠加）。
        val endpoints = patchEndpoints(context).filter { it.isNotBlank() }
        if (endpoints.isEmpty()) return null
        val base = endpoints[0]
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        try {
            // 探测服务器 Range 支持与文件大小
            val probe = URL("$base/api/app/latest.apk").openConnection() as HttpURLConnection
            probe.requestMethod = "GET"
            probe.connectTimeout = 15000
            probe.readTimeout = 15000
            probe.setRequestProperty("Authorization", authHeader())
            probe.setRequestProperty("Range", "bytes=0-0")
            probe.setRequestProperty("User-Agent", "MioLauncher/updater")
            val probeCode = probe.responseCode
            val total = if (probeCode == 206) {
                val cr = probe.getHeaderField("Content-Range") // bytes 0-0/306329819
                cr?.substringAfter("/")?.trim()?.toLongOrNull() ?: update.size
            } else {
                probe.disconnect()
                update.size
            }
            probe.disconnect()
            if (total <= 0) return null

            // 测活所有 endpoint：只保留能返回 206 的（公网玩家连不上局域网会自动排除）。
            // 并行测活，避免串行等待拖慢下载启动。
            val aliveResult = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
            val probePool = java.util.concurrent.Executors.newFixedThreadPool(
                minOf(endpoints.size, 8)
            )
            val probeFutures = endpoints.map { ep ->
                probePool.submit {
                    try {
                        val c = URL("$ep/api/app/latest.apk").openConnection() as HttpURLConnection
                        c.requestMethod = "GET"
                        c.connectTimeout = 5000
                        c.readTimeout = 5000
                        c.setRequestProperty("Authorization", authHeader())
                        c.setRequestProperty("Range", "bytes=0-0")
                        c.setRequestProperty("User-Agent", "MioLauncher/updater")
                        val ok = c.responseCode == 206
                        c.disconnect()
                        aliveResult[ep] = ok
                    } catch (_: Exception) {
                        aliveResult[ep] = false
                    }
                }
            }
            probeFutures.forEach { it.get() }
            probePool.shutdown()
            val alive = endpoints.filter { aliveResult[it] == true }
            val useEndpoints = alive.ifEmpty { listOf(base) }
            android.util.Log.i("PatchManager", "download: alive endpoints=${useEndpoints.size}/${endpoints.size}")

            // 线程数 = 隧道数，上限 16。分片大小 ≈ total/隧道数。
            // 注意：每分片下载时间 = 分片大小 / 隧道速率(~130KB/s)，必须给足分片超时。
            val threads = useEndpoints.size.coerceIn(4, 16)
            val chunk = (total / threads) + 1
            // 断点续传：不清空临时文件，跳过 tmp 中已写满的字节（退出/中断后下次从断点继续）。
            // tmp 大小即已下载的连续前缀长度（分片写入是稀疏的，但安全起见只信任文件实际大小）。
            val resumeFrom = if (tmp.isFile && tmp.length() > 0) tmp.length() else 0L
            if (!tmp.exists()) tmp.createNewFile()
            if (resumeFrom > 0) {
                android.util.Log.i("PatchManager", "resume from $resumeFrom/${total} (${resumeFrom * 100 / total}%)")
            }
            val startMs = System.currentTimeMillis()
            var downloadedAtomic = java.util.concurrent.atomic.AtomicLong(resumeFrom)
            val completed = java.util.concurrent.atomic.AtomicInteger(0)
            val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
            val tasks = ArrayList<java.util.concurrent.Future<*>>()

            // 下载中的失败分片范围（并发安全），全部完成后用稳定通道补齐
            val failedChunks = java.util.concurrent.ConcurrentLinkedQueue<LongArray>()
            for (t in 0 until threads) {
                val start = maxOf(t * chunk, resumeFrom)  // 跳过已下载前缀
                val end = minOf((t + 1) * chunk - 1, total - 1)
                if (start >= total) continue
                // 分片轮询分配隧道：t=0→ep0, t=1→ep1, ... 突破单隧道限速
                val ep = useEndpoints[t % useEndpoints.size]
                tasks.add(pool.submit {
                    // 每个线程独立的 RandomAccessFile，避免共享文件指针竞争
                    var raf: java.io.RandomAccessFile? = null
                    var chunkOk = false
                    try {
                        raf = java.io.RandomAccessFile(tmp, "rw")
                        var pos = start
                        // 分片级硬超时：按分片大小估算（最低 50KB/s，上限 20 分钟）。
                        // 腾讯云出口仅 ~350KB/s，290MB 全量包多线程共享下每个分片可能很慢，
                        // 固定 180 秒会把慢连接判失败 → 全部走顺序补齐（更慢）。
                        val perChunkSec = ((end - start) / 51200L).coerceIn(180L, 1200L)
                        val chunkDeadline = System.currentTimeMillis() + perChunkSec * 1000L
                        // 分片内部用小块请求：cpolar 免费隧道大 Range 长连接会返回损坏数据，
                        // 每 256KB 重新发 Range 请求（小块数据可靠），保证数据完整性。
                        val BLOCK = 256 * 1024
                        while (pos <= end && System.currentTimeMillis() < chunkDeadline) {
                            if (com.miolauncher.app.UpdateDownloadService.isCancelRequested()) break
                            val blockEnd = minOf(pos + BLOCK - 1, end.toLong())
                            // 每块最多重试 3 次（换隧道）
                            var blockOk = false
                            for (attempt in 0..3) {
                                if (com.miolauncher.app.UpdateDownloadService.isCancelRequested()) break
                                var conn: HttpURLConnection? = null
                                try {
                                    val ep2 = useEndpoints[(t + attempt) % useEndpoints.size]
                                    conn = openRange(ep2, pos, blockEnd)
                                    if (conn.responseCode != 206) {
                                        conn.disconnect(); conn = null
                                        continue
                                    }
                                    val input = conn.inputStream
                                    val buf = ByteArray(128 * 1024)
                                    var localPos = pos
                                    // 块级硬超时：防止 read() 半开连接无限阻塞导致"卡住"。
                                    // 慢连接（<100KB/s）256KB 块可能超过 15 秒，放宽到 40 秒。
                                    val blockDeadline = System.currentTimeMillis() + 40000L
                                    while (localPos <= blockEnd && System.currentTimeMillis() < blockDeadline) {
                                        if (com.miolauncher.app.UpdateDownloadService.isCancelRequested()) break
                                        val remaining = blockEnd - localPos + 1
                                        val want = minOf(buf.size, remaining.toInt())
                                        val read = input.read(buf, 0, want)
                                        if (read == -1) break
                                        raf.seek(localPos)
                                        raf.write(buf, 0, read)
                                        localPos += read
                                        downloadedAtomic.addAndGet(read.toLong())
                                    }
                                    input.close()
                                    if (localPos > blockEnd) {
                                        blockOk = true
                                        pos = localPos
                                        break
                                    }
                                    // 块未满（超时或断流）：重试（pos 从 localPos 继续）
                                    pos = localPos
                                } catch (_: Exception) {
                                } finally {
                                    conn?.disconnect()
                                }
                            }
                            if (!blockOk) {
                                if (pos > start) {
                                    // 已下部分数据，记录剩余范围补齐
                                    failedChunks.add(longArrayOf(pos, end))
                                } else {
                                    failedChunks.add(longArrayOf(start, end))
                                }
                                chunkOk = pos > end
                                break
                            }
                        }
                        if (pos > end) chunkOk = true
                        if (!chunkOk && !com.miolauncher.app.UpdateDownloadService.isCancelRequested()) {
                            android.util.Log.w("PatchManager", "chunk [$start-$end] partial, repairing from $pos")
                        }
                    } catch (_: Exception) {
                    } finally {
                        try {
                            raf?.close()
                        } catch (_: Exception) {
                        }
                        completed.incrementAndGet()
                    }
                })
            }
            // 进度上报（主线程 Handler 回调由调用方转）
            val lastReport = java.util.concurrent.atomic.AtomicLong(0L)
            // 总超时：按文件大小估算（最低 50KB/s），上限 30 分钟。
            // 腾讯云出口仅 ~350KB/s，290MB 全量包需 8~14 分钟，固定 4 分钟会提前判失败
            // （保留 tmp 断点，重试可续传，但首次体验就是"下载失败"）。
            val waitTimeoutSec = (total / 51200L).coerceIn(240L, 1800L)
            val waitDeadline = System.currentTimeMillis() + waitTimeoutSec * 1000L
            while (completed.get() < tasks.size) {
                if (com.miolauncher.app.UpdateDownloadService.isCancelRequested()) {
                    pool.shutdownNow()
                    // 保留 tmp 供下次断点续传
                    android.util.Log.w("PatchManager", "download cancelled, keeping tmp ${tmp.length()} bytes for resume")
                    return null
                }
                if (System.currentTimeMillis() > waitDeadline) {
                    android.util.Log.w("PatchManager", "download timed out, ${completed.get()}/${tasks.size} chunks done, keeping tmp ${tmp.length()} bytes")
                    pool.shutdownNow()
                    // 保留 tmp 供下次断点续传
                    return null
                }
                val done = downloadedAtomic.get()
                val now = System.currentTimeMillis()
                val speed = if (now > startMs) (done * 1000 / (now - startMs)).toLong() else 0L
                val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
                if (done - lastReport.get() > 65536) {
                    lastReport.set(done)
                    onProgress(done, total, speed, pct)
                }
                Thread.sleep(150)
            }
            pool.shutdown()

            // 补齐失败分片：用存活隧道逐条重下（此时大部分已完成，稳定性高）
            if (failedChunks.isNotEmpty()) {
                android.util.Log.w("PatchManager", "repairing ${failedChunks.size} failed chunks")
                val repairRaf = java.io.RandomAccessFile(tmp, "rw")
                for (range in failedChunks) {
                    if (com.miolauncher.app.UpdateDownloadService.isCancelRequested()) {
                        repairRaf.close()
                        return null
                    }
                    var pos = range[0]
                    val end = range[1]
                    var repaired = false
                    // 补齐也用小块请求（cpolar 大 Range 数据损坏）
                    val RBLOCK = 256 * 1024
                    for (ep in useEndpoints) {
                        if (com.miolauncher.app.UpdateDownloadService.isCancelRequested()) break
                        try {
                            while (pos <= end) {
                                val blockEnd = minOf(pos + RBLOCK - 1, end.toLong())
                                val conn = openRange(ep, pos, blockEnd)
                                if (conn.responseCode != 206) {
                                    conn.disconnect()
                                    break
                                }
                                val input = conn.inputStream
                                val buf = ByteArray(128 * 1024)
                                var localPos = pos
                                // repair 也加块级硬超时防卡住
                                val repairDeadline = System.currentTimeMillis() + 40000L
                                while (localPos <= blockEnd && System.currentTimeMillis() < repairDeadline) {
                                    val remaining = blockEnd - localPos + 1
                                    val want = minOf(buf.size, remaining.toInt())
                                    val read = input.read(buf, 0, want)
                                    if (read == -1) break
                                    repairRaf.seek(localPos)
                                    repairRaf.write(buf, 0, read)
                                    localPos += read
                                }
                                input.close()
                                conn.disconnect()
                                pos = localPos
                                if (pos > end) {
                                    repaired = true
                                    break
                                }
                            }
                        } catch (_: Exception) {
                        }
                        if (repaired) break
                    }
                    if (!repaired) {
                        android.util.Log.w("PatchManager", "repair chunk [$pos-$end] failed")
                    }
                }
                repairRaf.close()
            }
            onProgress(total, total, 0L, 100)

            if (update.sha256.isNotBlank()) {
                val actual = sha256File(tmp)
                if (!actual.equals(update.sha256, ignoreCase = true)) {
                    android.util.Log.w("PatchManager", "APK SHA256 mismatch: $actual vs ${update.sha256}")
                    tmp.delete()
                    return null
                }
            }
            if (tmp.renameTo(dest) || (dest.exists() && dest.delete() && tmp.renameTo(dest))) {
                return dest
            }
            return null
        } catch (e: Exception) {
            android.util.Log.w("PatchManager", "downloadAppApk failed", e)
            return null
        }
    }

    /** 打开带 Range 头的连接（返回 206 或 200）。 */
    private fun openRange(base: String, start: Long, end: Long): HttpURLConnection {
        val conn = URL("$base/api/app/latest.apk").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 20000
        conn.setRequestProperty("Authorization", authHeader())
        conn.setRequestProperty("User-Agent", "MioLauncher/updater")
        conn.setRequestProperty("Range", "bytes=$start-$end")
        return conn
    }

    private fun authHeader(): String =
        "Basic " + Base64.encodeToString(
            "${LogUploader.BASIC_USER}:${LogUploader.BASIC_PASS}".toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP,
        )

    /** 应用所有已下载的补丁到 runtime 目录（游戏启动前调用）。 */
    fun applyAllToRuntime(context: Context) {
        val patchDir = patchDir(context)
        val runtime = File(context.filesDir, "mio/runtime")
        patchDir.listFiles { f -> f.isFile && f.name.endsWith(".jar") }?.forEach { src ->
            try {
                src.copyTo(File(runtime, src.name), overwrite = true)
            } catch (_: Exception) {
            }
        }
    }

    /** 把用户忽略的补丁记下来，不再提示。 */
    fun skipPatch(context: Context, target: String) {
        try {
            val a = JSONArray(prefs(context).getString(KEY_SKIPPED, "[]"))
            if (target !in (0 until a.length()).map { a.optString(it) }) {
                a.put(target)
                prefs(context).edit().putString(KEY_SKIPPED, a.toString()).apply()
            }
        } catch (_: Exception) {
        }
    }

    private fun markInstalled(context: Context, target: String, version: String) {
        val m = installed(context).toMutableMap()
        m[target] = version
        saveInstalled(context, m)
    }

    /** 比较版本号字符串（按点分段数值比较）。a>b 返回正，a<b 返回负，相等返回 0。 */
    private fun versionCompare(a: String, b: String): Int {
        val as_ = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bs = b.split(".").map { it.toIntOrNull() ?: 0 }
        val n = maxOf(as_.size, bs.size)
        for (i in 0 until n) {
            val x = as_.getOrElse(i) { 0 }
            val y = bs.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }

    private fun sha256File(f: File): String {
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            f.inputStream().use { input ->
                val buf = ByteArray(65536)
                while (true) {
                    val n = input.read(buf)
                    if (n == -1) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }
}
