#include <jni.h>
#include <assert.h>
#include <dlfcn.h>

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <unistd.h>

#include <EGL/egl.h>
#define TAG __FILE_NAME__
#include "log.h"
#include <GL/osmesa.h>
#include "ctxbridges/osmesa_loader.h"
#include "driver_helper/nsbypass.h"

#ifdef GLES_TEST
#include <GLES2/gl2.h>
#endif

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/rect.h>
#include <string.h>
#include <environ/environ.h>
#include <android/dlext.h>
#include "utils.h"
#include "ctxbridges/bridge_tbl.h"
#include "ctxbridges/osm_bridge.h"

#define GLFW_CLIENT_API 0x22001
/* Consider GLFW_NO_API as Vulkan API */
#define GLFW_NO_API 0
#define GLFW_OPENGL_API 0x30001

// This means that the function is an external API and that it will be used
#define EXTERNAL_API __attribute__((used))
// This means that you are forced to have this function/variable for ABI compatibility
#define ABI_COMPAT __attribute__((unused))


struct PotatoBridge {

    /* EGLContext */ void* eglContext;
    /* EGLDisplay */ void* eglDisplay;
    /* EGLSurface */ void* eglSurface;
/*
    void* eglSurfaceRead;
    void* eglSurfaceDraw;
*/
};
EGLConfig config;
struct PotatoBridge potatoBridge;

#include "ctxbridges/egl_loader.h"
#include "ctxbridges/osmesa_loader.h"

#define RENDERER_GL4ES 1
#define RENDERER_VK_ZINK 2
#define RENDERER_VULKAN 4

EXTERNAL_API void pojavTerminate() { LOGI("MioGL: pojavTerminate");
    printf("EGLBridge: Terminating\n");

    switch (pojav_environ->config_renderer) {
        case RENDERER_GL4ES: {
            eglMakeCurrent_p(potatoBridge.eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            eglDestroySurface_p(potatoBridge.eglDisplay, potatoBridge.eglSurface);
            eglDestroyContext_p(potatoBridge.eglDisplay, potatoBridge.eglContext);
            eglTerminate_p(potatoBridge.eglDisplay);
            eglReleaseThread_p();

            potatoBridge.eglContext = EGL_NO_CONTEXT;
            potatoBridge.eglDisplay = EGL_NO_DISPLAY;
            potatoBridge.eglSurface = EGL_NO_SURFACE;
        } break;

            //case RENDERER_VIRGL:
        case RENDERER_VK_ZINK: {
            // Nothing to do here
        } break;
    }
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_setupBridgeWindow(JNIEnv* env, ABI_COMPAT jclass clazz, jobject surface) {
    pojav_environ->pojavWindow = ANativeWindow_fromSurface(env, surface);
    if(br_setup_window != NULL) br_setup_window();
}


JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_releaseBridgeWindow(ABI_COMPAT JNIEnv *env, ABI_COMPAT jclass clazz) {
    ANativeWindow_release(pojav_environ->pojavWindow);
}

EXTERNAL_API void* pojavGetCurrentContext() { LOGI("MioGL: pojavGetCurrentContext");
    return br_get_current();
}

//#define ADRENO_POSSIBLE
#ifdef ADRENO_POSSIBLE
void* load_turnip_vulkan() {
    if(getenv("POJAV_LOAD_TURNIP") == NULL) return NULL;
    const char* native_dir = getenv("POJAV_NATIVEDIR");
    const char* cache_dir = getenv("TMPDIR");
    if(!linker_ns_load(native_dir)) return NULL;
    void* linkerhook = linker_ns_dlopen("liblinkerhook.so", RTLD_LOCAL | RTLD_NOW);
    if(linkerhook == NULL) return NULL;
    void* turnip_driver_handle = linker_ns_dlopen("libvulkan_freedreno.so", RTLD_LOCAL | RTLD_NOW);
    if(turnip_driver_handle == NULL) {
        printf("AdrenoSupp: Failed to load Turnip!\n%s\n", dlerror());
        dlclose(linkerhook);
        return NULL;
    }
    void* dl_android = linker_ns_dlopen("libdl_android.so", RTLD_LOCAL | RTLD_LAZY);
    if(dl_android == NULL) {
        dlclose(linkerhook);
        dlclose(turnip_driver_handle);
        return NULL;
    }
    void* android_get_exported_namespace = dlsym(dl_android, "android_get_exported_namespace");
    void (*linkerhook_pass_handles)(void*, void*, void*) = dlsym(linkerhook, "app__pojav_linkerhook_pass_handles");
    if(linkerhook_pass_handles == NULL || android_get_exported_namespace == NULL) {
        dlclose(dl_android);
        dlclose(linkerhook);
        dlclose(turnip_driver_handle);
        return NULL;
    }
    linkerhook_pass_handles(turnip_driver_handle, android_dlopen_ext, android_get_exported_namespace);
    void* libvulkan = linker_ns_dlopen_unique(cache_dir, "libvulkan.so", RTLD_LOCAL | RTLD_NOW);
    return libvulkan;
}
#endif

static void set_vulkan_ptr(void* ptr) {
    char envval[64];
    sprintf(envval, "%"PRIxPTR, (uintptr_t)ptr);
    setenv("VULKAN_PTR", envval, 1);
}

void load_vulkan() {
    if(android_get_device_api_level() >= 28) { // the loader does not support below that
#ifdef ADRENO_POSSIBLE
        void* result = load_turnip_vulkan();
        if(result != NULL) {
            printf("AdrenoSupp: Loaded Turnip, loader address: %p\n", result);
            set_vulkan_ptr(result);
            return;
        }
#endif
    }
    printf("OSMDroid: loading vulkan regularly...\n");
    void* vulkan_ptr = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
    printf("OSMDroid: loaded vulkan, ptr=%p\n", vulkan_ptr);
    set_vulkan_ptr(vulkan_ptr);
}

int pojavInitOpenGL() {
    // Only affects GL4ES as of now
    const char *forceVsync = getenv("FORCE_VSYNC");
    if (strcmp(forceVsync, "true") == 0)
        pojav_environ->force_vsync = true;

    // NOTE: Override for now.
    const char *renderer = getenv("AMETHYST_RENDERER");
    if (strncmp("opengles", renderer, 8) == 0) {
        pojav_environ->config_renderer = RENDERER_GL4ES;
        if (!strcmp(renderer, "opengles3_desktopgl_zink_kopper")) {
            load_vulkan();
            setenv("GALLIUM_DRIVER", "zink", 1);
            setenv("MESA_ANDROID_NO_KMS_SWRAST", "1", 1);
        }
        set_gl_bridge_tbl();
    } else if (strcmp(renderer, "vulkan_zink") == 0) {
        pojav_environ->config_renderer = RENDERER_VK_ZINK;
        load_vulkan();
        setenv("GALLIUM_DRIVER","zink",1);
        set_osm_bridge_tbl();
    } else printf("EGLBridge: Renderer was not configured as a bridge. Consider adding \"opengles\" to the start of renderer name if it crashes");
    if(br_init()) {
        br_setup_window();
    }
    return 0;
}

extern void updateMonitorSize(int width, int height);

EXTERNAL_API int pojavInit() {
    LOGI("MioGL: pojavInit tid=%d", gettid());
    printf("MioGL: pojavInit start, window=%p\n", pojav_environ->pojavWindow);
    pojav_environ->glfwThreadVmEnv = get_attached_env(pojav_environ->runtimeJavaVMPtr);
    if(pojav_environ->glfwThreadVmEnv == NULL) {
        printf("Failed to attach Java-side JNIEnv to GLFW thread\n");
        return 0;
    }
    if (pojav_environ->pojavWindow != NULL) {
        ANativeWindow_acquire(pojav_environ->pojavWindow);
        pojav_environ->savedWidth = ANativeWindow_getWidth(pojav_environ->pojavWindow);
        pojav_environ->savedHeight = ANativeWindow_getHeight(pojav_environ->pojavWindow);
        printf("MioGL: window size %d x %d\n", pojav_environ->savedWidth, pojav_environ->savedHeight);
        // MioLauncher: 不在此处 setBuffersGeometry，避免提前连接窗口导致 eglCreateWindowSurface 失败
    } else {
        printf("MioGL: WARNING pojavWindow is NULL\n");
    }
    updateMonitorSize(pojav_environ->savedWidth, pojav_environ->savedHeight);
    pojavInitOpenGL();
    printf("MioGL: pojavInit done\n");
    return 1;
}

EXTERNAL_API void pojavSetWindowHint(int hint, int value) { LOGI("MioGL: pojavSetWindowHint(%d,%d)", hint, value);
    if (hint != GLFW_CLIENT_API) return;
    switch (value) {
        case GLFW_NO_API:
            pojav_environ->config_renderer = RENDERER_VULKAN;
            /* Nothing to do: initialization is handled in Java-side */
            // pojavInitVulkan();
            break;
        case GLFW_OPENGL_API:
            const char *renderer = getenv("AMETHYST_RENDERER");
            if (strncmp("opengles", renderer, 8) == 0) {
                pojav_environ->config_renderer = RENDERER_GL4ES;
            } else if (strcmp(renderer, "vulkan_zink") == 0) {
                pojav_environ->config_renderer = RENDERER_VK_ZINK;
            }
            /* Nothing to do: initialization is called in pojavCreateContext */
            // pojavInitOpenGL();
            break;
        default:
            printf("GLFW: Unimplemented API 0x%x\n", value);
            abort();
    }
}

static int g_swap_count_total = 0;

JNIEXPORT jint JNICALL Java_com_miolauncher_backend_NativeInput_getSwapCount(JNIEnv* env, jclass clazz) {
    return g_swap_count_total;
}

EXTERNAL_API void pojavSwapBuffers() {
    // MioLauncher 修复：渲染线程可能在上下文未绑定的线程上（"No context is current" 崩溃根因）。
    // swapBuffers 每帧必调，若上下文丢失则重新绑定到当前线程。
    extern void (*br_make_current)(basic_render_window_t*);
    int swap_count = ++g_swap_count_total;
    if (eglGetCurrentContext_p && eglGetCurrentContext_p() == NULL && pojav_environ->mainWindowBundle != NULL) {
        LOGI("MioGL: ctx NULL at swapBuffers tid=%d, re-making current", gettid());
        br_make_current(pojav_environ->mainWindowBundle);
    }
    br_swap_buffers();
}


EXTERNAL_API void pojavMakeCurrent(void* window) {
    LOGI("MioGL: pojavMakeCurrent(%p) tid=%d", window, gettid());
    br_make_current((basic_render_window_t*)window);
    LOGI("MioGL: pojavMakeCurrent done");
}

EXTERNAL_API void* pojavCreateContext(void* contextSrc) {
    LOGI("MioGL: pojavCreateContext(%p) renderer=%d tid=%d", contextSrc, pojav_environ->config_renderer, gettid());
    if (pojav_environ->config_renderer == RENDERER_VULKAN) {
        return (void *) pojav_environ->pojavWindow;
    }
    void* ret = br_init_context((basic_render_window_t*)contextSrc);
    LOGI("MioGL: pojavCreateContext -> %p", ret);
    // 绑定窗口表面并设为当前（首次连接窗口；游戏 makeCurrent 复用已建表面）
    if (ret != NULL) {
        gl_render_window_t* b = (gl_render_window_t*)ret;
        b->newNativeSurface = pojav_environ->pojavWindow;
        br_make_current((basic_render_window_t*)b);
        // MioLauncher 诊断：打印实际 GL 版本与渲染器（判断 gl4es/软渲染）
        typedef const unsigned char* (*glGetString_t)(unsigned int);
        glGetString_t gs = (glGetString_t) dlsym(RTLD_DEFAULT, "glGetString");
        if (gs) {
            LOGI("MioGL: GL_VERSION=%s", (const char*)gs(0x1F02));
            LOGI("MioGL: GL_RENDERER=%s", (const char*)gs(0x1F01));
        } else {
            LOGI("MioGL: glGetString not resolvable");
        }
    }
    return ret;
}

void* maybe_load_vulkan() {
    // We use the env var because
    // 1. it's easier to do that
    // 2. it won't break if something will try to load vulkan and osmesa simultaneously
    if(getenv("VULKAN_PTR") == NULL) load_vulkan();
    return (void*) strtoul(getenv("VULKAN_PTR"), NULL, 0x10);
}

EXTERNAL_API JNIEXPORT jlong JNICALL
Java_org_lwjgl_vulkan_VK_getVulkanDriverHandle(ABI_COMPAT JNIEnv *env, ABI_COMPAT jclass thiz) {
    printf("EGLBridge: LWJGL-side Vulkan loader requested the Vulkan handle\n");
    return (jlong) maybe_load_vulkan();
}

EXTERNAL_API void pojavSwapInterval(int interval) { LOGI("MioGL: pojavSwapInterval(%d)", interval);
    br_swap_interval(interval);
}

