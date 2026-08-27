package com.miolauncher.backend

/**
 * 渲染后端。对应 APK 内置的真实 GL 实现：
 * - NGGL4ES：默认，gl4es 直通系统 EGL（通用性最好，已充分验证）
 * - GL4ES：兼容档，与默认同用 ng_gl4es（对齐 FCL 的 opengles2），强制 GLES2
 * - MOBILEGLUES：MobileGlues（Mali/Adreno GPU 兼容性最佳，对齐 FCL 的 opengles_mobileglues）
 *
 * 说明：
 * - native EGL 桥（egl_bridge.c）只识别以 "opengles" 开头的 renderer 名。
 * - ANGLE（libGLESv2_angle.so）在此构建的原生桥下 eglCreateContext 崩溃，
 *   OSMesa（libOSMesa_81.so）不导出标准 EGL 符号，二者都无法工作，故不提供。
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
        // 关键：必须匹配 GLFW.java 识别的 "opengles_mobileglues"（否则 GLFW 用默认 GL 3.3，
        // 与 MobileGlues 的 GL 4.0 冲突，导致 GL.createCapabilities 失败 / Backend API Unknown）。
        amethystRenderer = "opengles_mobileglues",
        glEsVersion = 3,
        glVersionCode = "31",
        isGl4es = false,
        isMobileGlues = true,
    ),
    ;

    companion object {
        fun fromId(id: String?): Renderer = entries.firstOrNull { it.id == id } ?: NGGL4ES
    }
}
