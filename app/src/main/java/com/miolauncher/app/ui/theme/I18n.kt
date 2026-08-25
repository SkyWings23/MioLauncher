package com.miolauncher.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 极简 i18n：中/英文案切换（实时生效，Compose 自动重组）。
 */
object I18n {

    @Volatile
    var current: String = "zh_cn"
        private set

    /** 切换语言（触发全界面重组） */
    fun setLocale(lang: String) {
        current = lang
    }

    @Composable
    fun tr(key: String): String {
        val c = current
        return STRINGS[key]?.let { pair -> if (c == "en_us") pair.second else pair.first } ?: key
    }

    private val STRINGS = mapOf(
        // 导航
        "nav.home" to ("主页" to "Home"),
        "nav.download" to ("下载" to "Download"),
        "nav.resource" to ("资源" to "Resources"),
        "nav.profile" to ("我的" to "Profile"),

        // 主页
        "home.tagline" to ("自由 · 开源 · 属于你的 Minecraft 启动器" to "Free · Open-source · Your Minecraft launcher"),
        "home.current_version" to ("当前游戏版本" to "Current version"),
        "home.no_version" to ("未安装任何版本" to "No version installed"),
        "home.start_game" to ("开始游戏" to "Play"),
        "home.launch" to ("启动" to "Launch"),
        "home.installed_versions" to ("已安装版本" to "Installed versions"),
        "home.quick_features" to ("快捷功能" to "Quick features"),
        "home.version_mgmt" to ("版本管理" to "Versions"),
        "home.version_mgmt_sub" to ("全版本 / 快照" to "All versions / snapshots"),
        "home.mods_center" to ("模组中心" to "Mods"),
        "home.mods_center_sub" to ("Fabric / Forge" to "Fabric / Forge"),
        "home.shaders" to ("光影" to "Shaders"),
        "home.shaders_sub" to ("着色器下载" to "Shader downloads"),
        "home.modpacks" to ("整合包" to "Modpacks"),
        "home.modpacks_sub" to ("一键安装" to "One-click install"),
        "home.worlds" to ("世界地图" to "Worlds"),
        "home.worlds_sub" to ("地图下载" to "Map downloads"),
        "home.resources" to ("资源管理" to "Resources"),
        "home.resources_sub" to ("本地模组 / 光影" to "Local mods / shaders"),
        "home.launch_settings" to ("启动设置" to "Launch settings"),
        "home.launch_settings_sub" to ("渲染器 / 内存 / 性能" to "Renderer / memory / perf"),
        "home.java_runtime" to ("Java 运行时" to "Java runtime"),
        "home.java_runtime_sub" to ("版本 / 设备信息" to "Version / device info"),

        // 下载页
        "dl.title" to ("资源下载" to "Download"),
        "dl.tab_versions" to ("版本" to "Versions"),
        "dl.tab_mods" to ("模组" to "Mods"),
        "dl.tab_shaders" to ("光影" to "Shaders"),
        "dl.tab_resources" to ("材质" to "Resource Packs"),
        "dl.tab_worlds" to ("地图" to "Worlds"),
        "dl.tab_modpacks" to ("整合包" to "Modpacks"),
        "dl.search_mods" to ("搜索模组（Modrinth）" to "Search mods (Modrinth)"),
        "dl.search_modpacks" to ("搜索整合包（Modrinth）" to "Search modpacks (Modrinth)"),
        "dl.search" to ("搜索" to "Search"),
        "dl.all" to ("全部" to "All"),
        "dl.load_more" to ("加载更多" to "Load more"),
        "dl.mod_shared_warn" to ("提示：mods 目录由所有版本共享，安装前请确认模组适配你的游戏版本与加载器。" to "Note: the mods folder is shared by all versions. Make sure a mod matches your game version and loader."),
        "dl.download_install" to ("下载安装" to "Download"),
        "dl.cancel" to ("取消" to "Cancel"),
        "dl.done" to ("完成" to "Done"),
        "dl.downloading" to ("下载中…" to "Downloading…"),
        "dl.choose_version" to ("选择版本（含不同适配）" to "Choose version (loaders)"),
        "dl.compatible_versions" to ("适配版本" to "Supported versions"),
        "dl.description" to ("说明" to "Description"),
        "dl.install" to ("安装版本" to "Install version"),
        "dl.vanilla" to ("纯净原版" to "Vanilla"),
        "dl.close_all" to ("关闭所有下载？" to "Cancel all downloads?"),
        "dl.installed" to ("已安装" to "Installed"),
        "dl.tap_to_download" to ("点击下载 · 可选加载器" to "Tap to download"),

        // 资源页
        "res.title" to ("我的资源" to "My resources"),
        "res.empty" to ("暂无本地资源" to "No local resources"),
        "res.go_download" to ("去「下载」页获取内容吧" to "Go to Download to get content"),
        "res.delete" to ("删除" to "Delete"),
        "res.installed_count" to ("已安装" to "Installed"),

        // 我的
        "profile.title" to ("我的" to "Profile"),
        "profile.offline" to ("离线玩家" to "Offline player"),
        "profile.not_logged" to ("未登录 · 点击登录" to "Not logged in · Tap to login"),
        "profile.login" to ("离线账户" to "Offline account"),
        "profile.save" to ("保存" to "Save"),
        "profile.launch_runtime" to ("启动与运行" to "Launch & runtime"),
        "profile.launch_settings" to ("启动设置" to "Launch settings"),
        "profile.launch_settings_sub" to ("渲染器 / 内存 / 日志" to "Renderer / memory / log"),
        "profile.virtual_keys" to ("虚拟键位" to "Virtual keys"),
        "profile.virtual_keys_sub" to ("游戏内虚拟按键布局修改" to "Edit in-game touch controls"),
        "profile.java" to ("Java 运行时" to "Java runtime"),
        "profile.ms_login" to ("微软登录（正版）" to "Microsoft login"),
        "profile.appearance" to ("外观与语言" to "Appearance & language"),
        "profile.dark_mode" to ("深色模式" to "Dark mode"),
        "profile.dark_on" to ("当前：深色" to "Dark"),
        "profile.dark_off" to ("当前：浅色" to "Light"),
        "profile.splash" to ("开机动画" to "Splash animation"),
        "profile.splash_on" to ("已开启（粒子动画）" to "On (particles)"),
        "profile.splash_off" to ("已关闭" to "Off"),
        "profile.language" to ("语言" to "Language"),
        "profile.info" to ("信息" to "Info"),
        "profile.about" to ("关于 MioLauncher" to "About MioLauncher"),

        // 启动设置
        "ls.title" to ("启动设置" to "Launch settings"),
        "ls.perf_profile" to ("性能档位" to "Performance profile"),
        "ls.perf_hint" to ("预设会调整可见距离 / 模拟距离 / 帧率 / 粒子 / 分辨率" to "Presets adjust render/sim distance, FPS, particles, resolution"),
        "ls.renderer" to ("渲染器" to "Renderer"),
        "ls.memory" to ("内存" to "Memory"),
        "ls.memory_cap" to ("设备可用上限" to "Device limit"),
        "ls.resolution" to ("分辨率缩放" to "Resolution scale"),
        "ls.render_dist" to ("可见距离" to "Render distance"),
        "ls.sim_dist" to ("模拟距离" to "Simulation distance"),
        "ls.max_fps" to ("帧率上限" to "Max FPS"),
        "ls.unlimited" to ("无上限" to "Unlimited"),
        "ls.fov" to ("视角 FOV" to "FOV"),
        "ls.gui_scale" to ("界面缩放" to "GUI scale"),
        "ls.auto" to ("自动" to "Auto"),
        "ls.particles" to ("粒子" to "Particles"),
        "ls.lang" to ("游戏语言" to "Game language"),
        "ls.vsync" to ("垂直同步" to "V-Sync"),
        "ls.show_log" to ("显示日志" to "Show log"),
        "ls.virtual_mouse" to ("虚拟鼠标" to "Virtual mouse"),
        "ls.jvm_args" to ("附加 JVM 参数" to "Extra JVM args"),
        "ls.restore" to ("恢复默认" to "Restore defaults"),
        "ls.save" to ("保存" to "Save"),
    )
}
