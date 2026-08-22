package com.miolauncher.app.data

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

/**
 * 本地自定义导入：把用户选择的 .jar / .zip 文件复制或解压到游戏对应目录。
 * 模组 jar 直接复制；光影 / 地图 / 整合包 zip 解压到独立子目录。
 */
object ResourceImporter {

    /**
     * 导入一个本地文件到目标资源目录。
     * @param source 用户选择的文件（jar / zip）
     * @param versionId 当前版本（隔离启用时导入到实例目录，否则全局）
     * @return 错误信息（null = 成功）
     */
    fun import(
        context: Context,
        type: ResourceInstaller.Type,
        source: File,
        versionId: String?,
    ): String? {
        return try {
            if (!source.isFile) return "文件不存在"
            val targetRoot = targetRoot(context, type, versionId)
            when (type) {
                ResourceInstaller.Type.MOD -> {
                    // 模组：仅接受 jar，直接复制
                    if (!source.name.endsWith(".jar", ignoreCase = true)) return "模组仅支持 .jar 文件"
                    source.copyTo(File(targetRoot, uniqueName(targetRoot, source.name)), overwrite = true)
                }
                ResourceInstaller.Type.SHADER,
                ResourceInstaller.Type.WORLD,
                ResourceInstaller.Type.MODPACK -> {
                    // 光影/地图/整合包：zip 解压为独立目录
                    if (!source.name.endsWith(".zip", ignoreCase = true)) {
                        return "${typeLabel(type)}仅支持 .zip 压缩包"
                    }
                    extractZip(source, targetRoot)
                }
            }
            null
        } catch (e: Exception) {
            e.message ?: "导入失败"
        }
    }

    /** 目标根目录（隔离实例或全局） */
    private fun targetRoot(context: Context, type: ResourceInstaller.Type, versionId: String?): File {
        if (versionId != null) {
            val inst = com.miolauncher.app.data.VersionConfigStore.getInstanceDir(context, versionId)
            if (inst != null) return File(inst, type.dirName).apply { mkdirs() }
        }
        return File(MioRepository(context).gameDir, type.dirName).apply { mkdirs() }
    }

    /** 解压 zip 到目标目录下与 zip 同名的独立子目录 */
    private fun extractZip(zipFile: File, targetRoot: File) {
        val baseName = zipFile.name.removeSuffix(".zip").removeSuffix(".ZIP")
        val outDir = File(targetRoot, uniqueName(targetRoot, baseName)).apply { mkdirs() }
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val target = File(outDir, entry.name).normalize()
                // 防止 zip slip
                if (!target.path.startsWith(outDir.path)) continue
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    private fun uniqueName(dir: File, name: String): String {
        var candidate = name
        var i = 1
        while (File(dir, candidate).exists()) {
            val dot = name.lastIndexOf('.')
            candidate = if (dot > 0) {
                "${name.substring(0, dot)}_$i${name.substring(dot)}"
            } else {
                "${name}_$i"
            }
            i++
        }
        return candidate
    }

    private fun typeLabel(type: ResourceInstaller.Type): String = when (type) {
        ResourceInstaller.Type.MOD -> "模组"
        ResourceInstaller.Type.SHADER -> "光影"
        ResourceInstaller.Type.WORLD -> "地图"
        ResourceInstaller.Type.MODPACK -> "整合包"
    }
}
