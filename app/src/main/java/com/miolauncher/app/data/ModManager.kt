package com.miolauncher.app.data

import android.content.Context
import java.io.File

/**
 * 本地模组管理：列出已安装/已禁用模组、启用/禁用切换（重命名 .jar <-> .jar.disabled，加载器只认 .jar）、删除。
 * 兼容性：加载器一致 + 声明支持的 MC 版本覆盖当前版本。
 */
object ModManager {

    /** 已禁用后缀：加载器只加载以 .jar 结尾的文件，改名即停用，可无损还原。 */
    const val DISABLED_SUFFIX = ".jar.disabled"

    /** 模组来源：全局目录还是隔离实例目录 */
    enum class Source { GLOBAL, ISOLATED }

    data class ModEntry(
        val fileName: String,          // 磁盘上的实际文件名（可能带 .disabled）
        val baseName: String,          // 原始 mods/<baseName> 文件名（启用态）
        val displayName: String,
        val modVersion: String,
        val description: String,
        val loader: ModLoader,
        val minecraftRange: String,
        val iconBytes: ByteArray?,
        val enabled: Boolean,
        val compatible: Boolean,
        val source: Source,            // 来源：全局 / 隔离实例
    )

    private val iconCache = HashMap<String, ByteArray?>()

    fun clearCache() = iconCache.clear()

    /** 全局 mods 目录 */
    private fun globalModsDir(context: Context): File =
        File(MioRepository(context).gameDir, "mods").apply { mkdirs() }

    /** 该版本 mods 目录：隔离启用时返回实例目录，否则全局目录 */
    private fun modsDir(context: Context, versionId: String?): File {
        if (versionId != null) {
            val inst = com.miolauncher.app.data.VersionConfigStore.getInstanceDir(context, versionId)
            if (inst != null) {
                val dir = File(inst, "mods")
                if (!dir.isDirectory) dir.mkdirs()
                return dir
            }
        }
        return globalModsDir(context)
    }

    /** 按文件名定位实际文件（全局或隔离），用于启停/删除 */
    private fun resolveFile(context: Context, versionId: String?, fileName: String): File {
        val dirs = listOf(modsDir(context, versionId), globalModsDir(context))
        for (d in dirs) {
            val f = File(d, fileName)
            if (f.isFile) return f
        }
        return File(modsDir(context, versionId), fileName)
    }

    /**
     * 列出模组。隔离启用时合并全局 + 隔离实例两处（各自标注来源），
     * 隔离未启用时只列全局。
     * @param gameVersion 当前版本 id（如 1.21.11），null 则不判版本兼容
     * @param gameLoader  当前版本加载器（ModJarReader.detectVersionLoader），null=原版
     */
    fun list(context: Context, gameVersion: String?, gameLoader: ModLoader?, versionId: String? = null): List<ModEntry> {
        val dirs = mutableListOf<Pair<Source, File>>()
        val inst = if (versionId != null)
            com.miolauncher.app.data.VersionConfigStore.getInstanceDir(context, versionId) else null
        if (inst != null) dirs.add(Source.ISOLATED to File(inst, "mods").also { it.mkdirs() })
        dirs.add(Source.GLOBAL to globalModsDir(context))

        val result = mutableListOf<ModEntry>()
        for ((source, dir) in dirs) {
            val files = dir.listFiles { f -> f.isFile } ?: continue
            files.filter { f -> f.name.endsWith(".jar") || f.name.endsWith(DISABLED_SUFFIX) }
                .forEach { f ->
                    val enabled = f.name.endsWith(".jar") && !f.name.endsWith(DISABLED_SUFFIX)
                    val baseName = if (enabled) f.name else f.name.removeSuffix(".disabled")
                    val meta = ModJarReader.readMeta(f)
                    val iconBytes = meta?.iconPath?.let { path ->
                        synchronized(iconCache) {
                            iconCache.getOrPut("${source}|$baseName|$path") { ModJarReader.readIconBytes(f, path) }
                        }
                    }
                    result.add(
                        ModEntry(
                            fileName = f.name,
                            baseName = baseName,
                            displayName = meta?.name ?: baseName,
                            modVersion = meta?.version ?: "",
                            description = meta?.description ?: "",
                            loader = meta?.loader ?: ModLoader.NONE,
                            minecraftRange = meta?.minecraftVersionRange ?: "",
                            iconBytes = iconBytes,
                            enabled = enabled,
                            compatible = ModJarReader.isCompatible(meta, gameVersion, gameLoader),
                            source = source,
                        )
                    )
                }
        }
        return result.sortedWith(compareBy({ it.source == Source.ISOLATED }, { it.baseName.lowercase() }))
    }

    /** 启用（还原文件名）。 */
    fun setEnabled(context: Context, versionId: String?, fileName: String, enabled: Boolean): Boolean {
        return try {
            val src = resolveFile(context, versionId, fileName)
            val dir = src.parentFile ?: return false
            if (!src.isFile) return false
            val target = if (enabled) {
                if (fileName.endsWith(DISABLED_SUFFIX)) File(dir, fileName.removeSuffix(".disabled")) else src
            } else {
                if (fileName.endsWith(".jar")) File(dir, fileName + ".disabled") else src
            }
            if (target != src) src.renameTo(target)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 删除模组文件。返回是否删除成功。 */
    fun delete(context: Context, versionId: String?, fileName: String): Boolean {
        var ok = false
        val base = fileName.removeSuffix(DISABLED_SUFFIX)
        for (name in listOf(fileName, base, base + DISABLED_SUFFIX).distinct()) {
            val f = resolveFile(context, versionId, name)
            try {
                if (f.exists() && f.delete()) ok = true
            } catch (_: Exception) {
            }
        }
        return ok
    }

    /** 往指定来源的 mods 目录写入文件（自定义导入用）。返回目标文件。 */
    fun writeModFile(context: Context, versionId: String?, source: Source, name: String, bytes: ByteArray): File? {
        return try {
            val dir = if (source == Source.ISOLATED) modsDir(context, versionId)
            else globalModsDir(context)
            val f = File(dir, name)
            f.writeBytes(bytes)
            f
        } catch (_: Exception) {
            null
        }
    }
}
