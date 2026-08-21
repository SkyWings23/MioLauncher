package com.miolauncher.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jackhuang.hmcl.download.AutoDownloadProvider
import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider
import org.jackhuang.hmcl.download.DefaultDependencyManager
import org.jackhuang.hmcl.download.DefaultGameBuilder
import org.jackhuang.hmcl.download.DownloadProvider
import org.jackhuang.hmcl.download.MojangDownloadProvider
import org.jackhuang.hmcl.download.game.GameRemoteVersion
import org.jackhuang.hmcl.download.game.GameVersionList
import org.jackhuang.hmcl.game.DefaultGameRepository
import org.jackhuang.hmcl.game.GameInstanceID
import java.io.File

/**
 * 负责与 HMCLCore 交互的仓库层。
 * 使用多下载源自动切换（Mojang 官方优先，BMCLAPI 镜像兜底）。
 */
class MioRepository(private val context: Context) {

    val gameDir: File = File(context.filesDir, "mio/game").apply { mkdirs() }

    private val downloadProvider: DownloadProvider = AutoDownloadProvider(
        MojangDownloadProvider(),
        BMCLAPIDownloadProvider("https://bmclapi2.bangbang93.com"),
    )

    private val gameRepository: DefaultGameRepository = DefaultGameRepository(gameDir.toPath())

    private val cacheRepository: org.jackhuang.hmcl.download.DefaultCacheRepository =
        org.jackhuang.hmcl.download.DefaultCacheRepository(gameDir.toPath())

    init {
        // 让 HMCLCore 全局缓存仓库指向我们的实例（否则 index 为 null）
        org.jackhuang.hmcl.util.CacheRepository.setInstance(cacheRepository)
    }

    private val dependencyManager = DefaultDependencyManager(gameRepository, downloadProvider, cacheRepository)

    private val gameVersionList = GameVersionList(downloadProvider)

    private val versionsCacheFile = File(context.filesDir, "mio/versions_cache.json")

    suspend fun loadVersions(onProgress: (Double) -> Unit = {}): List<GameVersion> = withContext(Dispatchers.IO) {
        // 先读磁盘缓存，秒开
        readVersionsCache()?.let { return@withContext it }

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

    /** 读取版本列表磁盘缓存 */
    private fun readVersionsCache(): List<GameVersion>? {
        return try {
            if (!versionsCacheFile.isFile) return null
            if (System.currentTimeMillis() - versionsCacheFile.lastModified() > 24 * 3600 * 1000) {
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
    ) = withContext(Dispatchers.IO) {
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

        val executor = task.executor(object : org.jackhuang.hmcl.task.TaskListener() {
            override fun onRunning(task: org.jackhuang.hmcl.task.Task<*>) {
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
                onItem(task.getName() ?: "任务完成", 1f, true)
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

    /** 内置 JRE 的 Java 主版本号 */
    fun bundledJavaMajor(): Int = 21

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

    /** 该版本是否与内置 Java 21 兼容（未知则默认兼容，避免误报） */
    fun isVersionJavaCompatible(versionId: String): Boolean {
        val required = requiredJavaMajor(versionId) ?: return true
        return required <= bundledJavaMajor()
    }

    /** 返回不兼容说明（兼容时返回 null） */
    fun javaCompatibilityMessage(versionId: String): String? {
        val required = requiredJavaMajor(versionId) ?: return null
        if (required <= bundledJavaMajor()) return null
        return "该版本需要 Java $required，当前启动器内置 Java ${bundledJavaMajor()}，无法运行此版本。" +
                "请选择 1.21.x 或更早的版本（内置 Java 21 兼容）。"
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
