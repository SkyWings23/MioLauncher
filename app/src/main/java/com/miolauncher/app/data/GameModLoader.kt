package com.miolauncher.app.data

import android.content.Context
import java.io.File

/**
 * 从游戏日志（latest.log）解析模组加载结果，用于"模组加载状态"展示。
 * 支持 Fabric（Loading N mods / Failed to load / Mod rejected）、
 * Forge/NeoForge（Loading N mods / Failed to load / ERROR: ...）的常见日志格式。
 */
object GameModLoader {

    data class LoadResult(
        val loadedCount: Int = 0,       // 成功加载的模组数
        val failedCount: Int = 0,       // 加载失败的模组数
        val failedMods: List<String> = emptyList(),  // 失败/被拒绝的模组名
        val loader: String = "",        // 识别到的加载器（Fabric/Forge/NeoForge）
        val logExists: Boolean = false, // 游戏日志是否存在
        val message: String = "",       // 汇总文案
    )

    private val FABRIC_LOADING = Regex("Loading (\\d+) mods?:")
    private val FORGE_LOADING = Regex("Loading Minecraft \\d.* with ([0-9]+) mods")
    private val FAILED_FABRIC = Regex("Mod (.+?) failed to load|Failed to load mod (.+?)|Mod rejected: (.+?)|com\\.google\\.gson.*?([A-Za-z0-9_]+)")
    private val FAILED_FORGE = Regex("ERROR: (?:Failed to load|Fatal error|Exception loading|Mod '?)([A-Za-z0-9_ -]+?)'?")

    /** 读取最近一次游戏启动的模组加载结果。 */
    fun loadResult(context: Context): LoadResult {
        val repo = MioRepository(context)
        val latest = File(repo.gameDir, "logs/latest.log")
        if (!latest.isFile) return LoadResult()
        val lines = runCatching { latest.readLines() }.getOrNull() ?: return LoadResult()

        var loaded = 0
        var loader = ""
        for (line in lines) {
            FABRIC_LOADING.find(line)?.let {
                loaded = it.groupValues[1].toIntOrNull() ?: 0
                loader = "Fabric"
            }
            FORGE_LOADING.find(line)?.let {
                loaded = it.groupValues[1].toIntOrNull() ?: 0
                loader = "Forge/NeoForge"
            }
            if (loader.isNotEmpty() && loaded > 0) break
        }

        val failed = LinkedHashSet<String>()
        // 只分析加载器已识别之后的日志，避免误判
        val startIdx = lines.indexOfFirst { it.contains("Loading") && (it.contains("mods") || it.contains("mod")) }
        val relevant = if (startIdx >= 0) lines.subList(startIdx, lines.size) else lines
        for (line in relevant) {
            if (line.contains("Failed to load") || line.contains("Mod rejected") || line.contains("mod failed")) {
                FAILED_FABRIC.findAll(line).forEach {
                    val name = it.groupValues.firstOrNull { v -> v.isNotBlank() && v.length in 2..60 }
                    if (name != null && !name.startsWith("com.") && !name.contains(".")) failed.add(name)
                }
                FAILED_FORGE.find(line)?.let {
                    val name = it.groupValues[1].trim()
                    if (name.isNotBlank() && name.length in 2..60) failed.add(name)
                }
            }
        }

        val message = buildString {
            append(loader.ifBlank { "原版/未知" }).append(" · ")
            if (loaded > 0) append("成功加载 $loaded 个模组") else append("未检测到模组加载")
            if (failed.isNotEmpty()) append(" · ${failed.size} 个失败")
        }
        return LoadResult(
            loadedCount = loaded,
            failedCount = failed.size,
            failedMods = failed.toList(),
            loader = loader,
            logExists = true,
            message = message,
        )
    }
}
