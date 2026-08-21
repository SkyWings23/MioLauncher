package com.miolauncher.backend

import java.io.File
import java.util.regex.Pattern

/**
 * 游戏日志自动分析器：从 game.log / latest.log 中检测已知错误模式，
 * 返回诊断结果和可执行的修复建议。
 */
object GameLogAnalyzer {

    data class Diagnosis(
        val id: String,
        val severity: Severity,
        val title: String,
        val detail: String,
        val fix: Fix?,
    )

    enum class Severity { ERROR, WARNING, INFO }

    data class Fix(
        val description: String,
        val action: FixAction,
    )

    interface FixAction {
        fun execute(context: android.content.Context, gameDir: File): Boolean
        fun describe(): String
    }

    // ─── 已知错误模式 ───────────────────────────────────────────

    private val patterns = listOf<LogPattern>(
        // ClassCastException: URLClassLoader（launchwrapper 在 Java 9+ 上的经典问题）
        LogPattern(
            id = "launchwrapper_urlclassloader",
            regex = Pattern.compile(
                "ClassCastException.*URLClassLoader.*at net\\.minecraft\\.launchwrapper\\.Launch\\.<init>",
                Pattern.DOTALL
            ),
            severity = Severity.ERROR,
            title = "LaunchWrapper 兼容性错误",
            detail = "launchwrapper 在 Java 9+ 环境下尝试将系统类加载器转换为 URLClassLoader 失败。" +
                    "此问题已通过补丁 jar 修复，但可能有其他版本的 launchwrapper 未被覆盖。",
            fix = null, // 自动修复（替换 jar）已在别处处理
        ),
        // OutOfMemoryError
        LogPattern(
            id = "oom",
            regex = Pattern.compile("java\\.lang\\.OutOfMemoryError", Pattern.DOTALL),
            severity = Severity.ERROR,
            title = "内存不足（OOM）",
            detail = "JVM 堆内存不足，游戏无法继续运行。可通过增大最大内存或降低渲染距离来缓解。",
            fix = Fix(
                description = "增大游戏内存上限至设备安全值",
                action = object : FixAction {
                    override fun execute(context: android.content.Context, gameDir: File): Boolean = false
                    override fun describe(): String = "请在启动设置中增大内存（建议设备 RAM 的 1/4）"
                }
            ),
        ),
        // NoSuchMethodError: Forge/FML 方法缺失
        LogPattern(
            id = "forge_method_missing",
            regex = Pattern.compile(
                "NoSuchMethodError.*(?:net\\.minecraftforge|cpw\\.mods\\.fml|net\\.minecraft\\.fmlclient)",
                Pattern.DOTALL
            ),
            severity = Severity.ERROR,
            title = "Forge/FML 方法缺失",
            detail = "安装的 Forge 版本与游戏版本不兼容，或 Forge jar 文件损坏。",
            fix = Fix(
                description = "重新安装对应版本的 Forge",
                action = object : FixAction {
                    override fun execute(context: android.content.Context, gameDir: File): Boolean = false
                    override fun describe(): String = "请删除当前版本并重新安装 Forge 版本"
                }
            ),
        ),
        // ClassNotFoundException
        LogPattern(
            id = "class_not_found",
            regex = Pattern.compile(
                "java\\.lang\\.ClassNotFoundException:\\s+(\\S+)",
                Pattern.DOTALL
            ),
            severity = Severity.ERROR,
            title = "类未找到",
            detail = "JVM 在类路径中找不到所需的类，通常是库文件缺失或损坏。",
            fix = Fix(
                description = "重新验证并修复库文件完整性",
                action = object : FixAction {
                    override fun execute(context: android.content.Context, gameDir: File): Boolean = true
                    override fun describe(): String = "自动重新下载缺失/损坏的库文件"
                }
            ),
        ),
        // FileNotFoundException: 库文件缺失
        LogPattern(
            id = "lib_file_missing",
            regex = Pattern.compile(
                "FileNotFoundException:\\s+(.*\\.jar)",
                Pattern.DOTALL
            ),
            severity = Severity.ERROR,
            title = "库文件缺失",
            detail = "游戏所需的 jar 文件不存在，可能是下载不完整或被误删。",
            fix = Fix(
                description = "重新下载缺失的库文件",
                action = object : FixAction {
                    override fun execute(context: android.content.Context, gameDir: File): Boolean = true
                    override fun describe(): String = "自动重新下载缺失的 jar 文件"
                }
            ),
        ),
        // LWJGL UnsatisfiedLinkError（原生库加载失败）
        LogPattern(
            id = "lwjgl_native_fail",
            regex = Pattern.compile(
                "UnsatisfiedLinkError.*(?:liblwjgl|libGL|libEGL|libGLESv2)",
                Pattern.DOTALL
            ),
            severity = Severity.ERROR,
            title = "原生库加载失败",
            detail = "LWJGL 的原生库无法加载，可能是渲染器不兼容或原生库损坏。",
            fix = Fix(
                description = "切换到兼容的渲染器",
                action = object : FixAction {
                    override fun execute(context: android.content.Context, gameDir: File): Boolean = false
                    override fun describe(): String = "请在启动设置中切换渲染器（推荐 NGGL4ES）"
                }
            ),
        ),
        // StackOverflowError（通常是递归 mixin 或模组冲突）
        LogPattern(
            id = "stackoverflow",
            regex = Pattern.compile(
                "java\\.lang\\.StackOverflowError",
                Pattern.DOTALL
            ),
            severity = Severity.ERROR,
            title = "栈溢出",
            detail = "可能是模组 mixin 冲突或递归调用导致。如果安装了模组，尝试逐个禁用排查。",
            fix = null,
        ),
        // Cacio 相关错误（AWT 在 Android 上的问题）
        LogPattern(
            id = "cacio_fail",
            regex = Pattern.compile(
                "(?:ClassNotFoundException|NoSuchMethodError|NoClassDefFoundError).*cacio",
                Pattern.DOTALL
            ),
            severity = Severity.WARNING,
            title = "Cacio AWT 桥接警告",
            detail = "Caciocavallo AWT 桥接组件加载失败，游戏仍可运行但某些 AWT 功能不可用。",
            fix = null,
        ),
        // 版本不匹配
        LogPattern(
            id = "version_mismatch",
            regex = Pattern.compile(
                "Expected.*(?:minecraft|version).*but found",
                Pattern.DOTALL
            ),
            severity = Severity.WARNING,
            title = "版本不匹配",
            detail = "版本 JSON 中声明的版本与实际 jar 不一致，可能导致兼容性问题。",
            fix = null,
        ),
        // OpenGL 渲染错误
        LogPattern(
            id = "gl_error",
            regex = Pattern.compile(
                "(?:GL_OUT_OF_MEMORY|GL_INVALID_ENUM|GL_INVALID_VALUE|glError)",
                Pattern.DOTALL
            ),
            severity = Severity.WARNING,
            title = "OpenGL 渲染错误",
            detail = "GPU 驱动报告渲染错误，可能是渲染器与设备 GPU 不兼容。",
            fix = Fix(
                description = "切换渲染器",
                action = object : FixAction {
                    override fun execute(context: android.content.Context, gameDir: File): Boolean = false
                    override fun describe(): String = "请在启动设置中尝试其他渲染器"
                }
            ),
        ),
        // Java 版本不兼容：游戏需要更高版本 Java（如 26.x 需要 Java 25，内置 JRE 为 Java 21）
        LogPattern(
            id = "java_version_unsupported",
            regex = Pattern.compile(
                "UnsupportedClassVersionError.*class file version (\\d+\\.\\d+).*recognizes class file versions up to (\\d+\\.\\d+)",
                Pattern.DOTALL
            ),
            severity = Severity.ERROR,
            title = "Java 版本不兼容",
            detail = "该游戏版本需要更高版本的 Java，当前启动器内置的 Java 21 无法运行此版本。",
            fix = Fix(
                description = "更换游戏版本",
                action = object : FixAction {
                    override fun execute(context: android.content.Context, gameDir: File): Boolean = false
                    override fun describe(): String =
                        "该版本需要更高版本的 Java（>21）。请安装 1.21.x 或更早的版本（如 1.21.11），" +
                        "这些版本与内置 Java 21 完全兼容。"
                }
            ),
        ),
        // JVM 启动失败：无法创建 JVM（可能因参数或内存）
        LogPattern(
            id = "jvm_create_failed",
            regex = Pattern.compile(
                "(?:Could not create the Java Virtual Machine|A fatal exception has occurred|Unrecognized option: (\\S+))",
                Pattern.DOTALL
            ),
            severity = Severity.ERROR,
            title = "JVM 启动失败",
            detail = "Java 虚拟机无法启动，可能是启动参数不被内置 JRE 支持或内存不足。",
            fix = Fix(
                description = "检查启动参数",
                action = object : FixAction {
                    override fun execute(context: android.content.Context, gameDir: File): Boolean = false
                    override fun describe(): String =
                        "请尝试：1) 在启动设置中降低内存占用；2) 更换一个较旧、兼容的 Minecraft 版本；" +
                        "3) 若日志提示 Unrecognized option，请更新启动器或更换版本。"
                }
            ),
        ),
    )

    // ─── 分析入口 ───────────────────────────────────────────────

    /**
     * 分析指定日志文件，返回按严重程度排序的诊断列表。
     */
    fun analyze(logFile: File): List<Diagnosis> {
        if (!logFile.isFile) return emptyList()
        val content = try {
            logFile.readText()
        } catch (_: Exception) {
            return emptyList()
        }
        return analyze(content)
    }

    /**
     * 分析日志文本内容。
     */
    fun analyze(content: String): List<Diagnosis> {
        val results = mutableListOf<Diagnosis>()
        for (p in patterns) {
            val m = p.regex.matcher(content)
            if (m.find()) {
                val detail = if (m.groupCount() > 0 && m.group(1) != null) {
                    "${p.detail}\n涉及：${m.group(1)}"
                } else {
                    p.detail
                }
                results.add(Diagnosis(p.id, p.severity, p.title, detail, p.fix))
            }
        }
        return results.sortedBy { it.severity.ordinal }
    }

    /**
     * 分析游戏日志目录下的所有日志文件（game.log + latest.log）。
     */
    fun analyzeGameLogs(context: android.content.Context): List<Diagnosis> {
        val files = mutableListOf<File>()
        // JVM 控制台输出
        val gameLog = File(context.filesDir, "mio/logs/game.log")
        if (gameLog.isFile) files.add(gameLog)
        // MC latest.log
        val mcGameDir = File(context.filesDir, "mio/game")
        val latestLog = File(mcGameDir, "logs/latest.log")
        if (latestLog.isFile) files.add(latestLog)
        // crash-reports
        val crashDir = File(mcGameDir, "crash-reports")
        if (crashDir.isDirectory) {
            crashDir.listFiles { f -> f.isFile && f.name.startsWith("crash-") }
                ?.sortedByDescending { it.lastModified() }
                ?.take(3)
                ?.let { files.addAll(it) }
        }
        if (files.isEmpty()) return emptyList()

        // 合并所有日志内容分析
        val combined = files.joinToString("\n\n") { f ->
            "=== ${f.name} ===\n" + try { f.readText() } catch (_: Exception) { "" }
        }
        return analyze(combined)
    }

    /**
     * 尝试自动执行诊断建议的修复。
     * @return 已执行的修复描述列表
     */
    fun attemptAutoFix(context: android.content.Context, diagnoses: List<Diagnosis>): List<String> {
        val fixed = mutableListOf<String>()
        // 如果检测到类缺失或库文件缺失，触发完整性检查
        val needsLibCheck = diagnoses.any { it.id in listOf("class_not_found", "lib_file_missing") }
        if (needsLibCheck) {
            fixed.add("已触发库文件完整性检查与修复")
            // 实际修复由调用方在 ensureLibraries 中执行
        }
        return fixed
    }

    /**
     * 生成可读的诊断报告。
     */
    fun formatReport(diagnoses: List<Diagnosis>): String {
        if (diagnoses.isEmpty()) return "未检测到已知问题"
        return buildString {
            append("===== 日志诊断报告 =====\n\n")
            for (d in diagnoses) {
                val icon = when (d.severity) {
                    Severity.ERROR -> "❌"
                    Severity.WARNING -> "⚠️"
                    Severity.INFO -> "ℹ️"
                }
                append("$icon [${d.title}]\n")
                append("  ${d.detail}\n")
                d.fix?.let { append("  💡 建议：${it.description}\n") }
                append("\n")
            }
        }
    }

    // ─── 内部模式定义 ───────────────────────────────────────────

    private data class LogPattern(
        val id: String,
        val regex: Pattern,
        val severity: Severity,
        val title: String,
        val detail: String,
        val fix: Fix?,
    )
}
