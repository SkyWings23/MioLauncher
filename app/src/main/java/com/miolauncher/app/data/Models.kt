package com.miolauncher.app.data

import com.miolauncher.backend.Renderer

enum class GameVersionType { RELEASE, SNAPSHOT, BETA, ALPHA }

data class GameVersion(
    val id: String,
    val type: GameVersionType,
    val releaseTime: String,
    val size: Long = 0,
    val isDownloaded: Boolean = false,
)

enum class ModLoader { FABRIC, QUILT, FORGE, NEO_FORGE, LITE_LOADER, OPTIFINE, NONE }

/**
 * Minecraft 加载器（可选安装）。
 */
enum class McLoader(
    val id: String,
    val label: String,
) {
    NONE("none", "原版"),
    FABRIC("fabric", "Fabric"),
    QUILT("quilt", "Quilt"),
    FORGE("forge", "Forge"),
    NEO_FORGE("neoforge", "NeoForge"),
    LITELOADER("liteloader", "LiteLoader"),
    OPTIFINE("optifine", "OptiFine"),
}

/**
 * 单个下载任务条目，用于进度展示。
 */
data class DownloadItem(
    val name: String,
    val progress: Float,
    val state: DownloadItemState,
)

enum class DownloadItemState { PENDING, DOWNLOADING, DONE, FAILED }

/**
 * 安装整体状态。
 */
data class InstallProgress(
    val versionId: String,
    val loader: McLoader = McLoader.NONE,
    val currentStage: String = "准备中",
    val items: List<DownloadItem> = emptyList(),
    val overallProgress: Float = 0f,
    val isDone: Boolean = false,
    val error: String? = null,
)

data class ModInfo(
    val name: String,
    val author: String,
    val description: String,
    val version: String,
    val slug: String = "",
    val downloadUrl: String = "",
    val fileSize: Long = 0,
    val downloads: Long = 0,
    val rating: Float = 0f,
    val iconUrl: String = "",
)

data class ShaderInfo(
    val name: String,
    val author: String,
    val description: String,
    val version: String,
    val slug: String = "",
    val downloadUrl: String = "",
    val fileSize: Long = 0,
    val screenshots: List<String> = emptyList(),
    val downloads: Long = 0,
    val iconUrl: String = "",
)

data class WorldInfo(
    val name: String,
    val author: String,
    val description: String,
    val version: String,
    val slug: String = "",
    val downloadUrl: String = "",
    val fileSize: Long = 0,
    val downloads: Long = 0,
    val iconUrl: String = "",
)

data class ModpackInfo(
    val name: String,
    val author: String,
    val description: String,
    val version: String,
    val slug: String = "",
    val downloadUrl: String = "",
    val fileSize: Long = 0,
    val downloads: Long = 0,
    val gameVersion: String = "",
    val loader: ModLoader = ModLoader.NONE,
    val iconUrl: String = "",
)

/** 性能档位预设（低/中/高/自定义）。 */
enum class PerfProfile(val label: String) {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高"),
    CUSTOM("自定义"),
}

data class LaunchSettings(
    var renderer: Renderer = Renderer.NGGL4ES,
    var javaMemory: Int = 1536,
    var showLog: Boolean = true,
    var virtualMouseEnabled: Boolean = true,

    // ---- FCL 式启动设置 ----
    var perfProfile: PerfProfile = PerfProfile.LOW,
    var resolutionScale: Int = 80,       // 渲染分辨率缩放（默认 LOW 档，保证流畅）
    var renderDistance: Int = 4,         // 可见距离 2..32
    var simulationDistance: Int = 4,     // 模拟距离 2..16（默认 ≤ 渲染距离）
    var maxFps: Int = 60,                // 帧率上限 30..240, 240 = 无上限
    var fov: Int = 70,                   // 视角 30..110
    var guiScale: Int = 0,               // 界面缩放 0=自动, 1..4
    var lang: String = "zh_cn",          // 游戏语言
    var vsync: Boolean = false,          // 垂直同步
    var particles: Int = 1,              // 粒子 0..3 (off/decreased/minimal/all)
    var extraJvmArgs: String = "",       // 附加 JVM 参数
    var extendedMemory: Boolean = false, // 内存扩展（允许超过安全上限）
) {
    companion object {
        /** 各档位对应的参数 */
        fun preset(p: PerfProfile): LaunchSettings = when (p) {
            PerfProfile.LOW -> LaunchSettings().also {
                it.perfProfile = PerfProfile.LOW
                it.renderDistance = 4
                it.simulationDistance = 4
                it.maxFps = 60
                it.particles = 1
                it.resolutionScale = 80
            }
            PerfProfile.MEDIUM -> LaunchSettings().also {
                it.perfProfile = PerfProfile.MEDIUM
                it.renderDistance = 8
                it.simulationDistance = 8
                it.maxFps = 120
                it.particles = 2
                it.resolutionScale = 100
            }
            PerfProfile.HIGH -> LaunchSettings().also {
                it.perfProfile = PerfProfile.HIGH
                it.renderDistance = 16
                it.simulationDistance = 12
                it.maxFps = 0
                it.particles = 3
                it.resolutionScale = 100
            }
            else -> LaunchSettings()
        }
    }
}
