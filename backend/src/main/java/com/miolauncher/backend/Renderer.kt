package com.miolauncher.backend

/**
 * 渲染后端。对应 APK 内置的真实 GL 实现：
 * - NGGL4ES：默认，gl4es 直通系统 EGL（通用性最好，已充分验证）
 * - GL4ES：经典 gl4es 1.1.4（兼容老机）
 * - ANGLE：Google 的 OpenGL ES → Vulkan 转换层（兼容无 GLES2 驱动的设备）
 * - OSMESA：Mesa 纯软件渲染（无需任何 GPU 驱动，全型号全 CPU 可跑）
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
    ),
    GL4ES(
        id = "gl4es",
        label = "GL4ES（兼容）",
        glLibName = "libgl4es_114.so",
        eglLibName = "libEGL.so",
        amethystRenderer = "opengles",
        glEsVersion = 2,
        glVersionCode = "20",
        isGl4es = true,
    ),
    ANGLE(
        id = "angle",
        label = "ANGLE（Vulkan）",
        glLibName = "libGLESv2_angle.so",
        eglLibName = "libEGL_angle.so",
        amethystRenderer = "angle",
        glEsVersion = 2,
        glVersionCode = "20",
        isGl4es = false,
    ),
    OSMESA(
        id = "osmesa",
        label = "OSMesa（纯软件）",
        glLibName = "libOSMesa_81.so",
        eglLibName = "libOSMesa_81.so",
        amethystRenderer = "osmesa",
        glEsVersion = 2,
        glVersionCode = "20",
        isGl4es = false,
    ),
    ;

    companion object {
        fun fromId(id: String?): Renderer = entries.firstOrNull { it.id == id } ?: NGGL4ES
    }
}
