package com.miolauncher.app.data

import android.content.Context
import com.miolauncher.backend.Renderer

/**
 * 启动设置存储：渲染器 / 内存 / 日志 / 虚拟鼠标 / 性能档位 / 分辨率 / 距离 / 帧率 / FOV 等。
 */
object LaunchSettingsStore {
    private const val PREF = "mio_settings"
    private const val KEY_RENDERER = "launch_renderer"
    private const val KEY_MEMORY = "launch_memory_mb"
    private const val KEY_SHOW_LOG = "launch_show_log"
    private const val KEY_VMOUSE = "launch_virtual_mouse"
    private const val KEY_PROFILE = "launch_perf_profile"
    private const val KEY_RES_SCALE = "launch_res_scale"
    private const val KEY_RENDER_DIST = "launch_render_dist"
    private const val KEY_SIM_DIST = "launch_sim_dist"
    private const val KEY_MAX_FPS = "launch_max_fps"
    private const val KEY_FOV = "launch_fov"
    private const val KEY_GUI_SCALE = "launch_gui_scale"
    private const val KEY_LANG = "launch_lang"
    private const val KEY_VSYNC = "launch_vsync"
    private const val KEY_PARTICLES = "launch_particles"
    private const val KEY_JVM_ARGS = "launch_jvm_args"
    private const val KEY_EXT_MEMORY = "launch_ext_memory"
    private const val KEY_LAST_RENDERER = "last_launch_renderer"
    private const val KEY_RENDERER_FALLBACK = "renderer_fallback"

    /** 记录本次实际使用的渲染器（供崩溃后判断是否回退） */
    fun recordLaunchRenderer(context: Context, rendererId: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_RENDERER, rendererId).apply()
    }

    /** 上次启动用的渲染器 */
    fun lastLaunchRenderer(context: Context): String? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_LAST_RENDERER, null)

    /** 设置渲染器回退标记（mg 崩溃后，下次启动自动用 NGGL4ES 保证能进游戏） */
    fun setRendererFallback(context: Context, enable: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RENDERER_FALLBACK, enable).apply()
    }

    /** 消费回退标记（一次性）：已设置则返回 true 并清除 */
    fun consumeRendererFallback(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!sp.getBoolean(KEY_RENDERER_FALLBACK, false)) return false
        sp.edit().putBoolean(KEY_RENDERER_FALLBACK, false).apply()
        return true
    }

    /** 按 GPU/CPU 厂商推荐默认渲染器：
     * Mali（联发科/部分麒麟）与 Adreno（高通）用 MobileGlues 兼容性最佳（官方注释），
     * 避免默认 NGGL4ES 在部分 GPU 上黑屏/渲染异常。 */
    fun recommendedRenderer(): Renderer {
        val hw = (android.os.Build.HARDWARE + " " + android.os.Build.BOARD + " " +
            (if (android.os.Build.VERSION.SDK_INT >= 31) android.os.Build.SOC_MODEL else "")).lowercase()
        val mtk = hw.contains("mt") || hw.contains("mediatek") || hw.contains("mali") || hw.contains("kirin")
        val qcom = hw.contains("qcom") || hw.contains("sm8") || hw.contains("sm7") || hw.contains("adreno")
        return if (mtk || qcom) Renderer.MOBILEGLUES else Renderer.NGGL4ES
    }

    fun load(context: Context): LaunchSettings {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        var renderer = Renderer.fromId(sp.getString(KEY_RENDERER, recommendedRenderer().id))
        // 自动回退：上次 mg 崩溃后，本次强制用 NGGL4ES（保证能进游戏），用户手动改回 mg 时重新生效
        if (consumeRendererFallback(context)) {
            renderer = Renderer.NGGL4ES
        }
        val profile = runCatching {
            PerfProfile.valueOf(sp.getString(KEY_PROFILE, PerfProfile.LOW.name) ?: PerfProfile.LOW.name)
        }.getOrDefault(PerfProfile.LOW)
        return LaunchSettings(
            renderer = renderer,
            javaMemory = sp.getInt(KEY_MEMORY, DeviceInfo.defaultGameMemoryMb(context)),
            showLog = sp.getBoolean(KEY_SHOW_LOG, true),
            virtualMouseEnabled = sp.getBoolean(KEY_VMOUSE, true),
            perfProfile = profile,
            resolutionScale = sp.getInt(KEY_RES_SCALE, 80),
            renderDistance = sp.getInt(KEY_RENDER_DIST, 4),
            simulationDistance = sp.getInt(KEY_SIM_DIST, 5).coerceIn(5, 32),
            maxFps = sp.getInt(KEY_MAX_FPS, 60),
            fov = sp.getInt(KEY_FOV, 70),
            guiScale = sp.getInt(KEY_GUI_SCALE, 0),
            lang = sp.getString(KEY_LANG, "zh_cn") ?: "zh_cn",
            vsync = sp.getBoolean(KEY_VSYNC, false),
            particles = sp.getInt(KEY_PARTICLES, 1),
            extraJvmArgs = sp.getString(KEY_JVM_ARGS, "") ?: "",
            extendedMemory = sp.getBoolean(KEY_EXT_MEMORY, false),
        )
    }

    fun save(context: Context, settings: LaunchSettings) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RENDERER, settings.renderer.id)
            .putInt(KEY_MEMORY, settings.javaMemory)
            .putBoolean(KEY_SHOW_LOG, settings.showLog)
            .putBoolean(KEY_VMOUSE, settings.virtualMouseEnabled)
            .putString(KEY_PROFILE, settings.perfProfile.name)
            .putInt(KEY_RES_SCALE, settings.resolutionScale)
            .putInt(KEY_RENDER_DIST, settings.renderDistance)
            .putInt(KEY_SIM_DIST, settings.simulationDistance)
            .putInt(KEY_MAX_FPS, settings.maxFps)
            .putInt(KEY_FOV, settings.fov)
            .putInt(KEY_GUI_SCALE, settings.guiScale)
            .putString(KEY_LANG, settings.lang)
            .putBoolean(KEY_VSYNC, settings.vsync)
            .putInt(KEY_PARTICLES, settings.particles)
            .putString(KEY_JVM_ARGS, settings.extraJvmArgs)
            .putBoolean(KEY_EXT_MEMORY, settings.extendedMemory)
            .apply()
    }
}
