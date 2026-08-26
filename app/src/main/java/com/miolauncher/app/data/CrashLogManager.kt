package com.miolauncher.app.data

import android.content.Context
import java.io.File

/**
 * 崩溃日志收集：检测并汇总 MC 崩溃报告（crash-reports）、JVM 错误日志（hs_err）、
 * 游戏日志（latest.log）、JVM 控制台输出（game.log），供查看 / 复制 / 分享 / 导出。
 */
object CrashLogManager {

    private const val PREF = "mio_crashlog"
    private const val KEY_CONSUMED = "consumed_ms"

    data class CrashEvidence(val name: String, val path: String, val content: String)

    data class CrashReport(
        val title: String,
        val summary: String,
        val primaryPath: String?,
        val evidence: List<CrashEvidence>,
        val combined: String,
    )

    private fun gameDir(context: Context): File = MioRepository(context).gameDir

    /** 最新的 MC 崩溃报告（crash-reports/crash-*.txt） */
    fun crashReports(context: Context): List<File> =
        File(gameDir(context), "crash-reports").listFiles { f -> f.isFile && f.name.startsWith("crash-") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** 最新的 JVM 错误日志（hs_err_<pid>.log 或 hs_err_pid<pid>.log） */
    fun hsErrFiles(context: Context): List<File> =
        gameDir(context).listFiles { f ->
            f.isFile && (f.name.startsWith("hs_err_pid") || f.name.matches(Regex("hs_err_\\d+\\.log")))
        }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** 现有崩溃证据中最新文件的时间戳（无则 0） */
    fun newestCrashTime(context: Context): Long {
        var t = crashReports(context).firstOrNull()?.lastModified() ?: 0L
        hsErrFiles(context).firstOrNull()?.let { if (it.lastModified() > t) t = it.lastModified() }
        return t
    }

    private fun consumedMs(context: Context): Long =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_CONSUMED, 0L)

    /** 是否有尚未查看的崩溃（时间晚于上次消费点） */
    fun hasUnviewedCrash(context: Context): Boolean = newestCrashTime(context) > consumedMs(context)

    /** 标记已查看（下次不再重复提示） */
    fun consume(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putLong(KEY_CONSUMED, System.currentTimeMillis()).apply()
    }

    /** 收集最新崩溃报告；无任何崩溃证据返回 null */
    fun collect(context: Context): CrashReport? {
        val reports = crashReports(context)
        val hsErr = hsErrFiles(context)
        if (reports.isEmpty() && hsErr.isEmpty()) return null

        val primary = reports.firstOrNull() ?: hsErr.firstOrNull()
        val title = when {
            reports.isNotEmpty() -> "游戏崩溃（${reports.first().name.removePrefix("crash-")}）"
            else -> "JVM 崩溃（${hsErr.first().name}）"
        }
        val summary = primary?.let { readHead(it, 16) } ?: "崩溃详情见日志"

        val evidence = mutableListOf<CrashEvidence>()
        reports.firstOrNull()?.let { evidence.add(CrashEvidence("MC 崩溃报告", it.absolutePath, readTail(it, 250))) }
        hsErr.firstOrNull()?.let { evidence.add(CrashEvidence("JVM 错误日志", it.absolutePath, readTail(it, 250))) }
        val latest = File(gameDir(context), "logs/latest.log")
        if (latest.isFile) evidence.add(CrashEvidence("游戏日志", latest.absolutePath, readTail(latest, 200)))
        val console = File(context.filesDir, "mio/logs/game.log")
        if (console.isFile) evidence.add(CrashEvidence("JVM 控制台输出", console.absolutePath, readTail(console, 150)))

        val combined = buildString {
            append("===== MioLauncher 崩溃日志 =====").append('\n')
            append("时间：").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())).append('\n')
            primary?.let { append("主文件：").append(it.absolutePath).append('\n') }
            append('\n')
            evidence.forEach { e ->
                append("──────── ${e.name} ────────").append('\n')
                append(e.content).append('\n').append('\n')
            }
        }
        return CrashReport(title, summary, primary?.absolutePath, evidence, combined)
    }

    /** 导出报告到应用外部目录，返回文件（失败返回 null） */
    fun exportReport(context: Context, report: CrashReport): File? = exportText(
        context, "crash-${System.currentTimeMillis()}.txt", report.combined,
    )

    /**
     * 把日志内容写入可分享的文件（外部目录 MioLogs/），返回文件。
     * 分享用 EXTRA_STREAM + FileProvider 走完整文件，避免 EXTRA_TEXT 截断。
     */
    fun shareFile(context: Context, fileName: String, content: String): File? =
        exportText(context, fileName, content)

    /** 构造分享 Intent（完整日志文件），返回 null 表示写入失败 */
    fun shareIntent(context: Context, title: String, fileName: String, content: String): android.content.Intent? {
        val f = shareFile(context, fileName, content) ?: return null
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                f,
            )
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, title)
                putExtra(android.content.Intent.EXTRA_TEXT, title + "（完整日志见附件）")
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            // FileProvider 找不到配置的 root（异常目录）等 → 兜底降级为纯文本分享
            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, title)
                putExtra(android.content.Intent.EXTRA_TEXT, title + "\n\n" + content)
            }
        }
    }

    // ---- 启动标记机制：异常退出（含 native 崩溃/被系统杀）也能检测 ----

    /** 启动标记文件（游戏目录下；正常退出会删除，残留=异常崩溃） */
    private fun crashMarker(context: Context): File =
        File(gameDir(context), ".mio_crash_marker")

    /** 游戏启动前写入标记（内容：版本 + 时间） */
    fun markGameStart(context: Context, versionId: String) {
        try {
            crashMarker(context).writeText(
                "version=$versionId\ntime=${System.currentTimeMillis()}\n" +
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date()),
            )
        } catch (_: Exception) {}
    }

    /** 游戏正常退出时清除标记 */
    fun markGameExited(context: Context) {
        try { crashMarker(context).delete() } catch (_: Exception) {}
    }

    /** 是否残留标记（= 上次游戏异常崩溃，进程未正常走退出流程） */
    fun hasStaleCrashMarker(context: Context): Boolean {
        return try {
            val m = crashMarker(context)
            m.exists() && m.lastModified() > consumedMs(context)
        } catch (_: Exception) {
            false
        }
    }

    /** 无条件构造崩溃报告（不依赖 crash 文件，用于标记残留/异常退出时兜底）。
     * 收集 game.log + latest.log + hs_err + app_crash.log。 */
    fun buildCrashReport(context: Context): CrashReport {
        val gameLog = File(context.filesDir, "mio/logs/game.log")
        val appCrash = File(context.filesDir, "mio/logs/app_crash.log")
        val latest = File(gameDir(context), "logs/latest.log")
        val hsErr = hsErrFiles(context).firstOrNull()

        val combined = buildString {
            append("===== MioLauncher 崩溃日志（异常退出） =====").append('\n')
            append("时间：").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())).append('\n')
            append('\n')
            if (appCrash.isFile) {
                append("──────── 启动器崩溃 app_crash.log ────────").append('\n')
                append(readTail(appCrash, 150)).append('\n').append('\n')
            }
            if (hsErr != null) {
                append("──────── JVM 错误日志 ────────").append('\n')
                append(readTail(hsErr, 200)).append('\n').append('\n')
            }
            if (latest.isFile) {
                append("──────── 游戏日志 latest.log ────────").append('\n')
                append(readTail(latest, 200)).append('\n').append('\n')
            }
            append("──────── JVM 控制台输出 game.log ────────").append('\n')
            append(readTail(gameLog, 200))
        }
        val title = when {
            appCrash.isFile -> "启动器崩溃"
            hsErr != null -> "JVM 崩溃"
            else -> "游戏异常退出"
        }
        val summary = when {
            appCrash.isFile -> "启动器 app 闪退，详见 app_crash.log"
            hsErr != null -> "JVM 崩溃，详见 hs_err"
            else -> "上次游戏未能正常退出，已收集日志"
        }
        return CrashReport(
            title = title,
            summary = summary,
            primaryPath = hsErr?.absolutePath ?: appCrash.absolutePath,
            evidence = emptyList(),
            combined = combined,
        )
    }

    /** 通用导出：写入应用外部目录 MioLogs/，返回文件（失败返回 null） */
    fun exportText(context: Context, fileName: String, content: String): File? {
        return try {
            val dir = File(context.getExternalFilesDir(null), "MioLogs").apply { mkdirs() }
            val f = File(dir, fileName)
            f.writeText(content)
            f
        } catch (_: Exception) {
            null
        }
    }

    // ---------- 读取工具 ----------

    private fun readHead(f: File, maxLines: Int): String =
        runCatching { f.readText().split('\n').take(maxLines).joinToString("\n") }.getOrElse { "" }

    private fun readTail(f: File, maxLines: Int): String =
        runCatching { f.readText().split('\n').takeLast(maxLines).joinToString("\n") }.getOrElse { "" }
}
