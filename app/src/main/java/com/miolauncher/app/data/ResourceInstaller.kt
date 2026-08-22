package com.miolauncher.app.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 资源安装器：从 Modrinth 下载模组 / 光影 / 世界 / 整合包到游戏目录对应子目录。
 * 支持指定版本、自动补齐 required 前置依赖（递归、去重、跳过已安装）。
 */
object ResourceInstaller {

    /** 资源类型 → 游戏目录子目录 */
    enum class Type(val dirName: String, val facet: String) {
        MOD("mods", "mod"),
        SHADER("shaderpacks", "shader"),
        WORLD("saves", "world"),
        MODPACK("modpacks", "modpack"),
    }

    class InstallException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class InstallResult(
        val installedFiles: List<String>,
        val dependencyNames: List<String>,
    )

    private fun targetDir(context: Context, type: Type, versionId: String? = null): File {
        if (versionId != null) {
            val inst = com.miolauncher.app.data.VersionConfigStore.getInstanceDir(context, versionId)
            if (inst != null) {
                val dir = File(inst, type.dirName)
                if (!dir.isDirectory) dir.mkdirs()
                return dir
            }
        }
        return File(MioRepository(context).gameDir, type.dirName).apply { mkdirs() }
    }

    /** 目标目录下已安装的资源名列表 */
    fun installedFiles(context: Context, type: Type, versionId: String? = null): List<String> =
        targetDir(context, type, versionId).listFiles { f -> f.isFile }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

    fun delete(context: Context, type: Type, fileName: String, versionId: String? = null) {
        File(targetDir(context, type, versionId), fileName).delete()
    }

    /** 写入一个本地资源文件（自定义导入用），返回目标文件 */
    fun writeFile(context: Context, type: Type, versionId: String?, name: String, bytes: ByteArray): File? {
        return try {
            val f = File(targetDir(context, type, versionId), name)
            f.writeBytes(bytes)
            f
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 下载并安装资源，自动补齐 required 前置依赖。
     * @param version 指定版本（null 则取最新兼容版本）
     * @param taskId  下载任务 ID（写入 DownloadManager 供悬浮窗展示）
     * @param onStatus 状态文案回调（如"下载前置 xxx"）
     */
    suspend fun install(
        context: Context,
        type: Type,
        slug: String,
        version: ModrinthApi.ModrinthVersion?,
        gameVersion: String?,
        loaders: List<String>,
        taskId: String,
        includeDeps: Boolean = true,
        versionId: String? = null,
        onStatus: (String) -> Unit = {},
    ): InstallResult = withContext(Dispatchers.IO) {
        // 任务必须在任何网络操作前注册，保证悬浮窗 / 按钮状态总是可追踪
        DownloadManager.start(taskId, slug)
        try {
            val dir = targetDir(context, type, versionId)
            val chosen = version ?: ModrinthApi.versions(slug, gameVersion, loaders).firstOrNull()
                ?: throw InstallException("未找到 $slug 的下载资源，请检查网络或项目 ID")

            // 已存在同 slug 文件时先清理（避免同名冲突）
            installedFiles(context, type, versionId).filter { it.startsWith(slug) }.forEach {
                File(dir, it).delete()
            }

            val installed = mutableListOf<String>()
            val depNames = mutableListOf<String>()
            val resolved = HashSet<String>()

            // 递归收集 required 依赖（最多 3 层，避免环）
            fun collectDependencies(target: ModrinthApi.ModrinthVersion): List<ModrinthApi.ModrinthVersion> {
                val deps = mutableListOf<ModrinthApi.ModrinthVersion>()
                fun walk(v: ModrinthApi.ModrinthVersion, depth: Int) {
                    if (depth > 3) return
                    for (dep in v.dependencies) {
                        if (dep.dependencyType != "required") continue
                        val pid = dep.projectId ?: continue
                        if (!resolved.add(pid)) continue
                        val depVersion: ModrinthApi.ModrinthVersion? =
                            dep.versionId?.let { ModrinthApi.versionById(it) }
                                ?: ModrinthApi.pickVersionFor(pid, gameVersion, loaders)
                        if (depVersion != null) {
                            deps.add(depVersion)
                            walk(depVersion, depth + 1)
                        }
                    }
                }
                walk(target, 0)
                return deps
            }

            val deps = if (includeDeps) collectDependencies(chosen) else emptyList()
            DownloadManager.setFilesTotal(taskId, 1 + deps.size)

            fun downloadFile(target: ModrinthApi.ModrinthVersion, dep: Boolean) {
                val file = target.files.firstOrNull()
                    ?: throw InstallException("版本 ${target.versionNumber} 无可用文件")
                val out = File(dir, file.filename)
                DownloadManager.beginFile(taskId, file.filename, file.size)
                if (out.isFile() && out.length() > 0 && out.length() >= file.size.coerceAtLeast(0)) {
                    DownloadManager.addBytes(taskId, file.size)
                    DownloadManager.fileDone(taskId)
                    installed.add(file.filename)
                    return
                }
                if (dep) {
                    onStatus("下载前置：${file.filename}")
                    DownloadManager.setStage(taskId, "前置：${file.filename}")
                }
                val conn = URL(file.url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 15000
                    conn.readTimeout = 20000
                    conn.setRequestProperty("User-Agent", "MioLauncher/0.1.0")
                    if (conn.responseCode !in 200..299) {
                        throw InstallException("下载失败 HTTP ${conn.responseCode}（${file.filename}）")
                    }
                    val buf = ByteArray(65536)
                    conn.inputStream.use { input ->
                        out.outputStream().use { output ->
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                DownloadManager.addBytes(taskId, n.toLong())
                            }
                        }
                    }
                    DownloadManager.fileDone(taskId)
                    installed.add(file.filename)
                } catch (e: Exception) {
                    out.delete()
                    throw InstallException("下载失败：${e.message}", e)
                }
            }

            downloadFile(chosen, dep = false)
            deps.forEach { d ->
                // 若前置已存在（如 Fabric API 已装），跳过
                val f = d.files.firstOrNull()
                if (f != null) {
                    val exists = installedFiles(context, type, versionId).any { it == f.filename }
                    if (exists) {
                        depNames.add(d.versionNumber.ifBlank { f.filename })
                        DownloadManager.fileDone(taskId)
                        return@forEach
                    }
                }
                depNames.add(d.versionNumber.ifBlank { d.files.firstOrNull()?.filename ?: "" })
                downloadFile(d, dep = true)
            }
            DownloadManager.finish(taskId)
            onStatus("完成")
            InstallResult(installed, depNames.distinct())
        } catch (e: Exception) {
            DownloadManager.finish(taskId, error = e.message)
            throw e
        }
    }
}
