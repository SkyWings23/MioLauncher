package com.miolauncher.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jackhuang.hmcl.download.DownloadProvider
import org.jackhuang.hmcl.download.DefaultDependencyManager
import org.jackhuang.hmcl.download.DefaultGameBuilder
import org.jackhuang.hmcl.download.game.GameRemoteVersion
import org.jackhuang.hmcl.download.game.GameVersionList
import org.jackhuang.hmcl.game.DefaultGameRepository
import org.jackhuang.hmcl.game.GameInstanceID
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.task.TaskListener
import java.io.File

/**
 * 负责与 HMCLCore 交互的仓库层。
 * 使用多下载源自动切换（Mojang 官方优先，BMCLAPI 镜像兜底）。
 */
class MioRepository(private val context: Context) {

    val gameDir: File = File(context.filesDir, "mio/game").apply { mkdirs() }

    private val downloadProvider: DownloadProvider = org.jackhuang.hmcl.download.MultiMirrorDownloadProvider(
        "https://bmclapi2.bangbang93.com",
        "https://bmclapi.bangbang93.com",
    )

    private val gameRepository: DefaultGameRepository = DefaultGameRepository(gameDir.toPath())

    private val cacheRepository: org.jackhuang.hmcl.download.DefaultCacheRepository =
        org.jackhuang.hmcl.download.DefaultCacheRepository(gameDir.toPath())

    init {
        // 让 HMCLCore 全局缓存仓库指向我们的实例（否则 index 为 null）
        org.jackhuang.hmcl.util.CacheRepository.setInstance(cacheRepository)
        // 限制全局下载并发：HMCL FetchTask 默认并发 = CPU核数*4（10 核设备=40 个文件同时下载），
        // 大版本下载时内存暴涨被系统 LMK 杀掉（"下载到 70% 闪退"）。Android 移动端网络+内存
        // 限制到 4 并发最稳。
        org.jackhuang.hmcl.task.FetchTask.setDownloadExecutorConcurrency(4)
    }

    private val dependencyManager = DefaultDependencyManager(gameRepository, downloadProvider, cacheRepository)

    private val gameVersionList = GameVersionList(downloadProvider)

    private val versionsCacheFile = File(context.filesDir, "mio/versions_cache.json")

    suspend fun loadVersions(onProgress: (Double) -> Unit = {}): List<GameVersion> = withContext(Dispatchers.IO) {
        // 先读磁盘缓存（秒开，不阻塞显示）
        readVersionsCache(allowStale = true)?.let { return@withContext it }

        // 无缓存：网络加载，但加总超时（25s），避免网络慢时无限卡"正在加载"
        try {
            kotlinx.coroutines.withTimeout(25_000) {
                val task = gameVersionList.loadAsync("")
                val executor = task.executor()
                val success = executor.test()
                if (!success) {
                    val ex = executor.exception
                    android.util.Log.e("MioRepo", "版本加载失败", ex)
                    throw Exception(ex?.message ?: "版本加载失败", ex)
                }

                val result = gameVersionList.getVersions("").map { remote ->
                    remote.toGameVersion()
                }.sortedByDescending { it.releaseTime }
                writeVersionsCache(result)
                result
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            android.util.Log.w("MioRepo", "版本列表加载超时，返回空")
            throw Exception("版本列表加载超时，请检查网络", e)
        } catch (e: Exception) {
            throw e
        }
    }

    /** 只读缓存（可能过期），无缓存返回 null */
    fun loadVersionsCached(): List<GameVersion>? =
        readVersionsCache(allowStale = true)

    /** 读取版本列表磁盘缓存。allowStale=true 时即使过期也返回（网络失败兜底）。 */
    private fun readVersionsCache(allowStale: Boolean = false): List<GameVersion>? {
        return try {
            if (!versionsCacheFile.isFile) return null
            if (!allowStale
                && System.currentTimeMillis() - versionsCacheFile.lastModified() > 7 * 24 * 3600 * 1000) {
                return null
            }
            val arr = com.google.gson.JsonParser.parseString(versionsCacheFile.readText()).asJsonArray
            arr.mapNotNull { e ->
                val o = e.asJsonArray
                GameVersion(
                    id = o[0].asString,
                    type = runCatching { GameVersionType.valueOf(o[1].asString) }.getOrDefault(GameVersionType.RELEASE),
                    releaseTime = if (o.size() > 2) o[2].asString else "",
                    size = if (o.size() > 3) o[3].asLong else 0L,
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeVersionsCache(versions: List<GameVersion>) {
        try {
            versionsCacheFile.parentFile?.mkdirs()
            val arr = com.google.gson.JsonArray()
            versions.forEach { v ->
                val o = com.google.gson.JsonArray()
                o.add(v.id)
                o.add(v.type.name)
                o.add(v.releaseTime)
                o.add(v.size)
                arr.add(o)
            }
            versionsCacheFile.writeText(arr.toString())
        } catch (e: Exception) {
            android.util.Log.w("MioRepo", "版本缓存写入失败", e)
        }
    }

    /**
     * 下载并安装指定版本的 Minecraft，支持可选加载器。
     *
     * @param loader 选择的加载器（NONE=原版）
     * @param onStage 阶段进度回调：(stageName, overallProgress 0..1)
     * @param onItem 单个文件进度回调：(fileName, fileProgress 0..1, done)
     */
    suspend fun installVersion(
        versionId: String,
        loader: McLoader = McLoader.NONE,
        onStage: (String, Float) -> Unit = { _, _ -> },
        onItem: (String, Float, Boolean) -> Unit = { _, _, _ -> },
        onTaskCount: (Int) -> Unit = {},  // 预知下载任务总数（按 Task 身份去重）
        onTaskDone: () -> Unit = {},      // 一个下载任务完成
    ) = withContext(Dispatchers.IO) {
        // Forge/NeoForge 安装需要运行外部 Java 进程（Installer processor），
        // 指定内置 JRE 的 java 路径（app 进程的 java.home 是 ART，无 bin/java 会崩溃）
        if (loader == McLoader.FORGE || loader == McLoader.NEO_FORGE) {
            System.setProperty("mio.android", "true")
            runCatching {
                val jreHome = com.miolauncher.backend.JRE.getJreHome(context, 21)
                    ?: com.miolauncher.backend.JRE.getJreHome(context)
                if (jreHome != null) {
                    val javaBin = java.io.File(jreHome, "bin/java")
                    if (javaBin.isFile) {
                        System.setProperty("mio.forge.java", javaBin.absolutePath)
                        System.setProperty("mio.forge.java.home", jreHome.absolutePath)
                    }
                }
            }
        }
        val builder = DefaultGameBuilder(dependencyManager)
            .gameVersion(versionId)
            .name(GameInstanceID(if (loader == McLoader.NONE) versionId else "${versionId}-${loader.id}"))

        if (loader != McLoader.NONE) {
            // 获取加载器版本列表，选择最新版本
            val loaderList = dependencyManager.getVersionList(loader.id)
            loaderList.loadAsync(versionId).executor().test()
            val remoteVersions = loaderList.getVersions(versionId)
            if (remoteVersions.isEmpty()) {
                throw Exception("找不到 ${loader.label} 适用于 $versionId 的版本")
            }
            val latest = requireNotNull(remoteVersions.maxWithOrNull(
                compareBy { (it as? org.jackhuang.hmcl.download.RemoteVersion)?.getReleaseDate() ?: java.time.Instant.EPOCH }
            ) ?: remoteVersions.firstOrNull())
            onStage("下载 ${loader.label} 加载器", 0.05f)
            builder.version(latest as org.jackhuang.hmcl.download.RemoteVersion)        }

        val task = builder.buildAsync()

        // 用 Task 身份去重统计下载任务总数与完成数，避免 HMCL 的 Task 名不唯一导致的计数膨胀
        val seenTasks = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<org.jackhuang.hmcl.task.Task<*>, Boolean>())
        val doneTasks = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<org.jackhuang.hmcl.task.Task<*>, Boolean>())

        val executor = task.executor(object : org.jackhuang.hmcl.task.TaskListener() {
            override fun onRunning(task: org.jackhuang.hmcl.task.Task<*>) {
                if (seenTasks.add(task)) {
                    onTaskCount(seenTasks.size)
                }
                val stage = task.getInheritedStage()?.takeIf { it.isNotBlank() } ?: task.getName() ?: "任务进行中"
                val p = task.progressProperty().get().toFloat()
                if (p in 0.0..1.0) {
                    onStage(stage, p)
                } else {
                    onStage(stage, 0f)
                }
                onItem(task.getName() ?: stage, p.coerceIn(0f, 1f), false)
            }

            override fun onFinished(task: org.jackhuang.hmcl.task.Task<*>) {
                // 只对"运行过(onRunning)"的任务计数完成，避免父任务/未运行任务使完成数超过总数
                if (seenTasks.contains(task) && doneTasks.add(task)) {
                    onTaskDone()
                }
                onItem(task.getName() ?: "任务完成", 1f, true)
                // 下载任务完成一个就主动 GC：大版本下载时文件下载/解压会产生大量临时对象，
                // 及时回收避免内存持续攀升被系统 LMK 杀掉（"下载到 70% 闪退"）。
                try {
                    val rt = Runtime.getRuntime()
                    if (rt.freeMemory() < rt.totalMemory() / 3) {
                        rt.gc()
                    }
                } catch (_: Throwable) {}
            }
        })

        val success = executor.test()
        if (!success) {
            val ex = executor.exception
            android.util.Log.e("MioRepo", "安装版本 $versionId 失败", ex)
            onStage("安装失败", 0f)
            throw Exception(ex?.message ?: "安装失败", ex)
        }
        onStage("安装完成", 1f)
        versionId
    }

    /**
     * 安装 Modrinth 整合包（.mrpack）。
     * 下载 mrpack → 读取清单 → 创建实例（自动装 MC 版本 + 加载器）→ 下载依赖模组 → 解压到实例 mods。
     * 完成后可通过主页选择安装生成的版本启动。
     *
     * @param modpackUrl  整合包文件直链（Modrinth 版本文件 URL）
     * @param instanceName 实例名（版本 id，如 "pack-xxx"）
     * @return 安装生成的版本 id（实例名）
     */
    suspend fun installModpack(
        modpackUrl: String,
        instanceName: String,
        onStage: (String, Float) -> Unit = { _, _ -> },
        onItem: (String, Float, Boolean) -> Unit = { _, _, _ -> },
        onTaskCount: (Int) -> Unit = {},
        onTaskDone: () -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val zipFile = File(context.cacheDir, "modpack_${System.currentTimeMillis()}.mrpack")
        try {
            onStage("下载整合包文件", 0.02f)
            downloadFileTo(modpackUrl, zipFile)

            onStage("读取整合包清单", 0.08f)
            val modpack = try {
                org.jackhuang.hmcl.modpack.ModpackInstaller.readModpackManifest(zipFile.toPath())
            } catch (e: Exception) {
                throw Exception("无法解析整合包文件（不是有效的 Modrinth 整合包）", e)
            }

            // 检查整合包所需 MC 版本是否受支持（启动器 LWJGL 3.3 仅支持到 1.21.x；26.x 需要 LWJGL 3.4）
            val mcVer = modpack.getGameVersion()
            if (mcVer.isNotBlank() && isMcAboveSupport(mcVer)) {
                throw Exception("该整合包需要 Minecraft $mcVer，当前启动器最高支持 1.21.x（LWJGL 3.3），请选择目标版本为 1.21 及以下的整合包版本")
            }

            onStage("安装 ${modpack.getName()}", 0.1f)
            val instanceId = GameInstanceID(instanceName)
            val task: Task<*> = org.jackhuang.hmcl.modpack.ModpackInstaller.createInstallTask(
                dependencyManager, zipFile.toPath(), modpack, instanceId)

            val seenTasks = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Task<*>, Boolean>())
            val doneTasks = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Task<*>, Boolean>())
            val executor = task.executor(object : TaskListener() {
                override fun onRunning(t: Task<*>) {
                    if (seenTasks.add(t)) onTaskCount(seenTasks.size)
                    val stage = t.getInheritedStage()?.takeIf { it.isNotBlank() } ?: t.getName() ?: "任务进行中"
                    val p = t.progressProperty().get().toFloat()
                    onStage(stage, if (p in 0.0..1.0) p else 0f)
                    onItem(t.getName() ?: stage, p.coerceIn(0f, 1f), false)
                }

                override fun onFinished(t: Task<*>) {
                    if (seenTasks.contains(t) && doneTasks.add(t)) onTaskDone()
                    onItem(t.getName() ?: "任务完成", 1f, true)
                }
            })
            val success = executor.test()
            if (!success) {
                val ex = executor.exception
                android.util.Log.e("MioRepo", "安装整合包 $instanceName 失败", ex)
                // 失败时清理不完整的实例，避免残留
                runCatching { gameRepository.removeInstanceFromDisk(instanceId) }
                throw Exception(ex?.message ?: "整合包安装失败", ex)
            }
            onStage("安装完成", 1f)
            instanceName
        } finally {
            zipFile.delete()
        }
    }

    /** 简单下载文件（带超时与重试），用于整合包文件拉取 */
    private fun downloadFileTo(url: String, target: File) {
        var lastErr: Exception? = null
        for (attempt in 0 until 3) {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "MioLauncher/0.1.0")
                conn.instanceFollowRedirects = true
                try {
                    if (conn.responseCode !in 200..299) {
                        throw java.io.IOException("HTTP ${conn.responseCode}")
                    }
                    target.outputStream().use { out -> conn.inputStream.use { it.copyTo(out) } }
                    return
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                lastErr = e
                target.delete()
            }
        }
        throw lastErr ?: java.io.IOException("下载失败")
    }

    /**
     * 检查版本是否已安装（游戏文件齐全）。
     */
    fun isInstalled(versionId: String): Boolean {
        return try {
            gameRepository.refresh()
            gameRepository.hasInstance(GameInstanceID(versionId))
        } catch (e: Exception) {
            false
        }
    }

    fun loadInstalledVersions(): List<GameVersion> {
        gameRepository.refresh()
        return gameRepository.getInstanceManifests().map { manifest ->
            val type = when (manifest.type()) {
                org.jackhuang.hmcl.game.ReleaseType.RELEASE -> GameVersionType.RELEASE
                org.jackhuang.hmcl.game.ReleaseType.SNAPSHOT -> GameVersionType.SNAPSHOT
                org.jackhuang.hmcl.game.ReleaseType.OLD_BETA -> GameVersionType.BETA
                org.jackhuang.hmcl.game.ReleaseType.OLD_ALPHA -> GameVersionType.ALPHA
                else -> GameVersionType.RELEASE
            }
            val time = manifest.releaseTime()?.toString()?.take(10) ?: ""
            GameVersion(
                id = manifest.id().toString(),
                type = type,
                releaseTime = time,
                isDownloaded = true,
            )
        }
    }

    /**
     * 补齐缺失的库文件（安装时库下载被静默忽略，需显式重跑）。
     */
    suspend fun ensureLibraries(versionId: String) = withContext(Dispatchers.IO) {
        gameRepository.refresh()
        val id = GameInstanceID(versionId)
        val manifest = gameRepository.getResolvedInstanceManifest(id).launchManifest()
        val task = dependencyManager.checkLibraryCompletionAsync(manifest, true)
        val executor = task.executor()
        if (!executor.test()) {
            val ex = executor.exception
            android.util.Log.e("MioRepo", "库下载失败", ex)
            throw Exception(ex?.message ?: "库下载失败")
        }
    }

    fun deleteVersion(versionId: String) {
        val id = GameInstanceID(versionId)
        val dir = gameDir.resolve("versions").resolve(versionId)
        if (dir.isDirectory) {
            dir.deleteRecursively()
        }
        gameRepository.refresh()
    }

    /** 内置 JRE 支持的最高 Java 主版本号（8 / 17 / 21 / 25，启动时按版本自动选） */
    fun bundledJavaMajor(): Int = 25

    /** 判断 MC 版本是否超出启动器支持范围。 */
    companion object {
        fun isMcAboveSupport(mcVer: String): Boolean {
            return try {
                val clean = mcVer.trim()
                val parse = { s: String ->
                    s.split(".").mapNotNull { it.toIntOrNull() }
                }
                val a = parse(clean)
                val b = parse("1.21.11")
                if (a.isEmpty() || b.isEmpty()) return false
                for (i in 0 until maxOf(a.size, b.size)) {
                    val x = a.getOrElse(i) { 0 }
                    val y = b.getOrElse(i) { 0 }
                    if (x != y) return x > y
                }
                false
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * 检测指定版本所需的最低 Java 主版本号。
     * 读取版本 JSON 的 javaVersion.majorVersion 字段；读取失败返回 null（未知）。
     */
    fun requiredJavaMajor(versionId: String): Int? {
        return try {
            val json = File(gameDir, "versions/$versionId/$versionId.json")
            if (!json.isFile) return null
            val obj = com.google.gson.JsonParser.parseString(json.readText()).asJsonObject
            val java = obj.getAsJsonObject("javaVersion") ?: return null
            java.get("majorVersion").asInt
        } catch (_: Exception) {
            null
        }
    }

    /** 该版本是否与内置 JRE 兼容（未知则默认兼容，避免误报） */
    fun isVersionJavaCompatible(versionId: String): Boolean {
        val required = requiredJavaMajor(versionId) ?: return true
        return required <= bundledJavaMajor()
    }

    /** 返回不兼容说明（兼容时返回 null） */
    fun javaCompatibilityMessage(versionId: String): String? {
        // 26.x 及以上需要 LWJGL 3.4 + SDL3 原生库（当前启动器未提供），即使 Java 版本满足也无法运行
        val major = versionId.substringBefore('-').split('.').firstOrNull()?.toIntOrNull()
        if (major != null && major >= 26) {
            return "该版本需要 LWJGL 3.4 + SDL3 原生支持（$versionId），当前启动器暂不支持运行，可能无法启动。"
        }
        val required = requiredJavaMajor(versionId) ?: return null
        if (required <= bundledJavaMajor()) return null
        return "该版本需要 Java $required，启动器最高支持 Java ${bundledJavaMajor()}，无法运行此版本。"
    }

    private fun GameRemoteVersion.toGameVersion(): GameVersion {
        val type = when (getType()) {
            org.jackhuang.hmcl.game.ReleaseType.RELEASE -> GameVersionType.RELEASE
            org.jackhuang.hmcl.game.ReleaseType.SNAPSHOT -> GameVersionType.SNAPSHOT
            org.jackhuang.hmcl.game.ReleaseType.OLD_BETA -> GameVersionType.BETA
            org.jackhuang.hmcl.game.ReleaseType.OLD_ALPHA -> GameVersionType.ALPHA
            else -> GameVersionType.RELEASE
        }
        val date = getReleaseDate()?.toString()?.substring(0, 10) ?: ""
        val installed = try { isInstalled(getGameVersion()) } catch (e: Exception) { false }
        return GameVersion(
            id = getGameVersion(),
            type = type,
            releaseTime = date,
            isDownloaded = installed,
        )
    }
}
