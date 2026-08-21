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
    )

    private val iconCache = HashMap<String, ByteArray?>()

    fun clearCache() = iconCache.clear()

    private fun modsDir(context: Context): File =
        File(MioRepository(context).gameDir, "mods").apply { mkdirs() }

    /**
     * 列出本地模组。
     * @param gameVersion 当前版本 id（如 1.21.11），null 则不判版本兼容
     * @param gameLoader  当前版本加载器（ModJarReader.detectVersionLoader），null=原版
     */
    fun list(context: Context, gameVersion: String?, gameLoader: ModLoader?): List<ModEntry> {
        val dir = modsDir(context)
        val files = dir.listFiles { f -> f.isFile } ?: return emptyList()
        return files
            .filter { f ->
                f.name.endsWith(".jar") || f.name.endsWith(DISABLED_SUFFIX)
            }
            .mapNotNull { f ->
                val enabled = f.name.endsWith(".jar") && !f.name.endsWith(DISABLED_SUFFIX)
                val baseName = if (enabled) f.name else f.name.removeSuffix(".disabled")
                val meta = ModJarReader.readMeta(f)
                val iconBytes = meta?.iconPath?.let { path ->
                    synchronized(iconCache) {
                        iconCache.getOrPut("$baseName|$path") { ModJarReader.readIconBytes(f, path) }
                    }
                }
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
                )
            }
            .sortedBy { it.baseName.lowercase() }
    }

    /** 启用（还原文件名）。 */
    fun setEnabled(context: Context, fileName: String, enabled: Boolean): Boolean {
        val dir = modsDir(context)
        return try {
            val src = File(dir, fileName)
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

    fun delete(context: Context, fileName: String) {
        File(modsDir(context), fileName).delete()
    }
}
