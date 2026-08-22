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

    fun load(context: Context): LaunchSettings {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val renderer = Renderer.fromId(sp.getString(KEY_RENDERER, Renderer.NGGL4ES.id))
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
            simulationDistance = sp.getInt(KEY_SIM_DIST, 4),
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
