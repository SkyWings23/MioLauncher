//
// Created by maks on 06.01.2025.
//

#include "jvm_hooks.h"

#include <android/api-level.h>

#include "environ/environ.h"

#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>

#define TAG __FILE_NAME__
#include <log.h>

extern void* maybe_load_vulkan();

/**
 * Basically a verbatim implementation of ndlopen(), found at
 * https://github.com/PojavLauncherTeam/lwjgl3/blob/3.3.1/modules/lwjgl/core/src/generated/c/linux/org_lwjgl_system_linux_DynamicLinkLoader.c#L11
 * but with our own additions for stuff like vulkanmod.
 */
static jlong ndlopen_bugfix(__attribute__((unused)) JNIEnv *env,
                     __attribute__((unused)) jclass class,
                     jlong filename_ptr,
                     jint jmode) {
    const char* filename = (const char*) filename_ptr;

    // Oveeride vulkan loading to let us load vulkan ourselves
    if(strstr(filename, "libvulkan.so") == filename) {
        printf("LWJGL linkerhook: replacing load for libvulkan.so with custom driver\n");
        return (jlong) maybe_load_vulkan();
    }

    // This hook also serves the task of mitigating a bug: the idea is that since, on Android 10 and
    // earlier, the linker doesn't really do namespace nesting.
    // It is not a problem as most of the libraries are in the launcher path, but when you try to run
    // VulkanMod which loads shaderc outside of the default jni libs directory through this method,
    // it can't load it because the path is not in the allowed paths for the anonymous namesapce.
    // This method fixes the issue by being in libpojavexec, and thus being in the classloader namespace

    int mode = (int)jmode;
    return (jlong) dlopen(filename, mode);
}

/**
 * MioLauncher: hook DynamicLinkLoader.ndlsym。
 * 与 FCL 对齐：FCL 的 linkerhook 只重定向 dlopen，不把 ndlsym 回退到全局命名空间。
 * GL 函数的解析（glBufferStorage 等）走 glfwGetProcAddress -> eglGetProcAddress/dlsym，
 * 由 gl4es + 环境（LD_LIBRARY_PATH 含 /vendor/lib64/hw）保证驱动可解析。
 * 这里保留纯透传钩子：不注入任何回退，行为与未 hook 一致，便于按需恢复。
 */
static jlong (*orig_ndlsym)(JNIEnv*, jclass, jlong, jlong) = NULL;

static jlong hooked_ndlsym(JNIEnv *env, jclass clazz, jlong handle, jlong nameptr) {
    if (orig_ndlsym != NULL) return orig_ndlsym(env, clazz, handle, nameptr);
    return 0;
}

/**
 * Install the LWJGL dlopen hook. This allows us to mitigate linker bugs and add custom library overrides.
 */
void installLwjglDlopenHook(JNIEnv *env) {
    LOGI("Installing LWJGL dlopen() hook");
    jclass dynamicLinkLoader = (*env)->FindClass(env, "org/lwjgl/system/linux/DynamicLinkLoader");
    if(dynamicLinkLoader == NULL) {
        LOGE("Failed to find the target class");
        (*env)->ExceptionClear(env);
        return;
    }
    JNINativeMethod ndlopenMethod[] = {
            {"ndlopen", "(JI)J", &ndlopen_bugfix}
    };
    if((*env)->RegisterNatives(env, dynamicLinkLoader, ndlopenMethod, 1) != 0) {
        LOGE("Failed to register the hooked method");
        (*env)->ExceptionClear(env);
    }

    // MioLauncher: hook ndlsym（纯透传，与 FCL 行为一致）
    if (orig_ndlsym == NULL) {
        orig_ndlsym = (jlong (*)(JNIEnv*, jclass, jlong, jlong))
                dlsym(RTLD_DEFAULT, "Java_org_lwjgl_system_linux_DynamicLinkLoader_ndlsym");
        LOGI("LWJGL ndlsym original at %p", (void*)orig_ndlsym);
    }
    JNINativeMethod dlsymMethod[] = {
            {"ndlsym", "(JJ)J", &hooked_ndlsym}
    };
    if((*env)->RegisterNatives(env, dynamicLinkLoader, dlsymMethod, 1) == 0) {
        LOGI("Installed LWJGL ndlsym() hook");
    } else {
        LOGE("Failed to register ndlsym hook");
        (*env)->ExceptionClear(env);
    }
}