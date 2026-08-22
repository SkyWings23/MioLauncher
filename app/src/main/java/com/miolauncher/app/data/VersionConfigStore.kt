package com.miolauncher.app.data

import android.content.Context
import org.json.JSONObject

/**
 * 每版本独立配置存储：组件隔离、自定义 JVM 参数等。
 * 存储在 SharedPreferences 中，以 JSON 格式按版本 ID 索引。
 */
object VersionConfigStore {
    private const val PREF = "mio_version_config"

    data class VersionConfig(
        val isolated: Boolean = false,       // 组件隔离（mods/config/saves/resourcepacks 独立）
        val customJvmArgs: String = "",      // 版本专属 JVM 参数（追加到全局设置）
        val customMemoryMb: Int = 0,         // 版专属内存（0 = 用全局设置）
    )

    fun load(context: Context, versionId: String): VersionConfig {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val json = sp.getString(versionId, null) ?: return VersionConfig()
        return try {
            val obj = JSONObject(json)
            VersionConfig(
                isolated = obj.optBoolean("isolated", false),
                customJvmArgs = obj.optString("customJvmArgs", ""),
                customMemoryMb = obj.optInt("customMemoryMb", 0),
            )
        } catch (_: Exception) {
            VersionConfig()
        }
    }

    fun save(context: Context, versionId: String, config: VersionConfig) {
        val obj = JSONObject().apply {
            put("isolated", config.isolated)
            put("customJvmArgs", config.customJvmArgs)
            put("customMemoryMb", config.customMemoryMb)
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(versionId, obj.toString()).apply()
    }

    fun setIsolated(context: Context, versionId: String, isolated: Boolean) {
        save(context, versionId, load(context, versionId).copy(isolated = isolated))
    }

    /**
     * 获取版本的隔离实例目录。
     * 隔离启用时返回 filesDir/mio/game/instances/<versionId>/，否则返回 null。
     */
    fun getInstanceDir(context: Context, versionId: String): java.io.File? {
        val config = load(context, versionId)
        if (!config.isolated) return null
        return java.io.File(context.filesDir, "mio/game/instances/$versionId")
    }

    /**
     * 确保隔离实例目录存在，并从游戏根目录链接/复制 HMCL 解析所需的 versions/libraries，
     * 同时创建独立的 mods/config/resourcepacks/saves 目录。
     */
    fun ensureInstanceDir(context: Context, versionId: String, gameDir: java.io.File): java.io.File? {
        val instanceDir = getInstanceDir(context, versionId) ?: return null
        if (!instanceDir.isDirectory) instanceDir.mkdirs()

        // 隔离目录必须能被 HMCL 解析版本（versions/）+ 解析库（libraries/），
        // 否则 buildCommand 抛 NoSuchGameInstanceException。优先软链接共享，失败则复制。
        linkOrCopy(java.io.File(gameDir, "versions"), java.io.File(instanceDir, "versions"))
        linkOrCopy(java.io.File(gameDir, "libraries"), java.io.File(instanceDir, "libraries"))
        // assets/ 资源索引（indexes/objects）必须存在，否则游戏找不到资源索引，
        // 主菜单全景图等资源加载失败 → 纯色背景。
        linkOrCopy(java.io.File(gameDir, "assets"), java.io.File(instanceDir, "assets"))

        // 为隔离版本创建独立的 mods/config/saves/resourcepacks/shaderpacks 目录
        val subdirs = listOf("mods", "config", "saves", "resourcepacks", "shaderpacks", "modpacks")
        for (name in subdirs) {
            val target = java.io.File(instanceDir, name)
            if (!target.isDirectory) target.mkdirs()
        }
        return instanceDir
    }

    /** 尝试符号链接共享目录；链接失败时回退为整体复制。 */
    private fun linkOrCopy(src: java.io.File, dst: java.io.File) {
        if (!src.isDirectory || dst.exists()) return
        try {
            java.nio.file.Files.createSymbolicLink(dst.toPath(), src.toPath())
        } catch (e: Exception) {
            try {
                src.copyRecursively(dst, overwrite = false)
            } catch (e2: Exception) {
                android.util.Log.w("MioVCStore", "链接/复制 ${src.name} 失败", e2)
            }
        }
    }
}
