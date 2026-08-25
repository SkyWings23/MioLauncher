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
/** 完整 APK 更新信息（不可热更新的内容通过整包更新下发）。 */
data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val file: String,
    val sha256: String,
    val size: Long,
    val desc: String,
)

object PatchManager {

    private const val PREF = "mio_patches"
    private const val KEY_INSTALLED = "installed"  // JSON: {"<target>": "<version>"}
    private const val KEY_SKIPPED = "skipped"       // JSON: ["<target>..."] 用户点"忽略"的

    /** 局域网候选（服务器主机，平板）；与 cpolar 域名并列兜底。 */
    private const val LAN_ENDPOINTS = "http://192.168.10.41:8787"

    /** 补丁下载域名候选：局域网优先，其次 cpolar 多域名。 */
    private fun patchEndpoints(context: Context): List<String> {
        val merged = LinkedHashSet<String>()
        LAN_ENDPOINTS.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { merged.add(it) }
        LogUploader.endpoints(context).forEach { merged.add(it) }
        // 主动拉取服务器最新隧道列表（含手机/平板多隧道），合并进候选域名。
        // 服务器 /api/endpoints 返回当前存活的全部隧道，保证新增隧道即时可用。
        // 关键：拉取到的列表持久化（mergeEndpoints），平板重启域名变化后，
        // App 用已存的稳定手机隧道域名重新发现平板新隧道。
        try {
            for (probe in merged.toList()) {
                val conn = URL("$probe/api/endpoints").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                try {
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
                        if (arr != null) {
                            val fetched = ArrayList<String>()
                            for (i in 0 until arr.length()) {
                                val e = arr.optString(i)
                                if (e.isNotBlank()) {
                                    val norm = if (e.startsWith("http")) e else "http://$e"
                                    merged.add(norm)
                                    fetched.add(norm)
                                }
                            }
                            // 持久化本次拉取到的全部隧道域名，供下次启动/平板重启后复用
                            LogUploader.mergeEndpoints(context, fetched)
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                if (merged.size >= 24) break
            }
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
                    return AppUpdate(
                        versionName = app.optString("versionName", ""),
                        versionCode = versionCode,
                        file = app.optString("file", ""),
                        sha256 = app.optString("sha256", ""),
                        size = app.optLong("size", 0L),
                        desc = app.optString("desc", ""),
                    )
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** 计算需要下载/更新的补丁列表（未安装、或已装版本低于服务器版本）。 */    fun pendingPatches(context: Context, manifest: List<JSONObject>): List<JSONObject> {
        val installed = installed(context)
        val skipped = try {
            val a = JSONArray(prefs(context).getString(KEY_SKIPPED, "[]"))
            (0 until a.length()).map { a.optString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
        return manifest.filter { p ->
            val target = p.optString("target")
            val serverVer = p.optString("version")
            val localVer = installed[target]
            // 需要更新：服务器版本存在且比本地新（本地缺失或版本号更小）
            !skipped.contains(target) &&
                serverVer.isNotBlank() &&
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
        // 整体重试：慢隧道导致部分分片失败时，第二次重试（慢隧道已被淘汰）通常成功。
        // 最多尝试 2 次，避免无限重试拖时间。
        var lastResult: File? = null
        for (attempt in 1..2) {
            if (com.miolauncher.app.UpdateDownloadService.isCancelRequested()) return null
            lastResult = downloadAppApkOnce(context, update, onProgress)
            if (lastResult != null) return lastResult
            if (attempt == 1) {
                android.util.Log.w("PatchManager", "download attempt $attempt failed, retrying once")
                // 清掉残留 tmp，干净重试
                try {
                    File(context.cacheDir, "mio_update_${update.versionCode}.apk.tmp").delete()
                } catch (_: Exception) {
                }
            }
        }
        return lastResult
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
            // 干净的临时文件：分片各自从头写，避免上次残留脏数据。
            if (tmp.exists()) tmp.delete()
            tmp.createNewFile()
            val startMs = System.currentTimeMillis()
            var downloadedAtomic = java.util.concurrent.atomic.AtomicLong(0L)
            val completed = java.util.concurrent.atomic.AtomicInteger(0)
            val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
            val tasks = ArrayList<java.util.concurrent.Future<*>>()

            // 下载中的失败分片范围（并发安全），全部完成后用稳定通道补齐
            val failedChunks = java.util.concurrent.ConcurrentLinkedQueue<LongArray>()
            for (t in 0 until threads) {
                val start = t * chunk
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
                        // 分片级硬超时：每分片约 18MB @130KB/s ≈ 2.4 分钟，给 180 秒缓冲
                        val chunkDeadline = System.currentTimeMillis() + 180000L
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
                                    while (localPos <= blockEnd && System.currentTimeMillis() < chunkDeadline) {
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
                                    // 块未满：重试（pos 从 localPos 继续）
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
            val waitDeadline = System.currentTimeMillis() + 240000L  // 总超时 4 分钟
            while (completed.get() < tasks.size) {
                if (com.miolauncher.app.UpdateDownloadService.isCancelRequested()) {
                    pool.shutdownNow()
                    return null
                }
                if (System.currentTimeMillis() > waitDeadline) {
                    android.util.Log.w("PatchManager", "download timed out, ${completed.get()}/${tasks.size} chunks done")
                    pool.shutdownNow()
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
                                while (localPos <= blockEnd) {
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
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
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
