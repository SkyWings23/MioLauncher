package com.miolauncher.backend

/**
 * 渲染后端。对应 APK 内置的真实 GL 实现：
 * - NGGL4ES：默认，gl4es 直通系统 EGL（通用性最好，已充分验证）
 * - GL4ES：兼容档，与默认同用 ng_gl4es（对齐 FCL 的 opengles2），强制 GLES2
 * - MOBILEGLUES：MobileGlues（Mali/Adreno GPU 兼容性最佳，对齐 FCL 的 opengles_mobileglues）
 * - ZINK：Mesa Zink 经 Vulkan 渲染桌面 OpenGL（Kopper 窗口系统），
 *   Adreno 走 Turnip 驱动、其余走系统 Vulkan；仅 arm64-v8a 打包了 Mesa 运行时
 *
 * 说明：
 * - native EGL 桥（egl_bridge.c）识别 "opengles*" 前缀 renderer 名，
 *   其中 opengles3_desktopgl_zink_kopper 走 Mesa EGL 分支。
 */
enum class Renderer(
    val id: String,
    val label: String,
    val glLibName: String,
    val eglLibName: String,
    val amethystRenderer: String,
    val glEsVersion: Int,
    val glVersionCode: String,
    val isGl4es: Boolean,
    val isMobileGlues: Boolean,
) {
    NGGL4ES(
        id = "nggl4es",
        label = "NG GL4ES（默认）",
        glLibName = "libng_gl4es.so",
        eglLibName = "libEGL.so",
        amethystRenderer = "opengles",
        glEsVersion = 3,
        glVersionCode = "31",
        isGl4es = true,
        isMobileGlues = false,
    ),
    GL4ES(
        id = "gl4es",
        label = "GL4ES 兼容（GLES2）",
        glLibName = "libng_gl4es.so",
        eglLibName = "libEGL.so",
        amethystRenderer = "opengles",
        glEsVersion = 2,
        glVersionCode = "20",
        isGl4es = true,
        isMobileGlues = false,
    ),
    MOBILEGLUES(
        id = "mobileglues",
        label = "MobileGlues（兼容最佳）",
        glLibName = "libmobileglues.so",
        eglLibName = "libmobileglues.so",
        amethystRenderer = "opengles",
        glEsVersion = 3,
        glVersionCode = "31",
        isGl4es = false,
        isMobileGlues = true,
    ),
    ZINK(
        id = "zink",
        label = "Zink（Vulkan 桌面 GL）",
        glLibName = "libglxshim.so",
        eglLibName = "libEGL_mesa.so",
        amethystRenderer = "opengles3_desktopgl_zink_kopper",
        glEsVersion = 3,
        glVersionCode = "46",
        isGl4es = false,
        isMobileGlues = false,
    ),
    ;

    companion object {
        fun fromId(id: String?): Renderer = entries.firstOrNull { it.id == id } ?: NGGL4ES
    }
}
