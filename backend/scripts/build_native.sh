#!/usr/bin/env bash
# 编译 backend 的 native 库 libpojavexec.so（arm64-v8a）
# 使用 Amethyst 完整源码（含 GLFW 桥、环境初始化、输入桥）。
# 沙箱为 arm64 宿主，NDK 的 x86_64 ndk-build 无法直接运行，
# 改用原生 clang + NDK sysroot 交叉编译。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AMETHYST_JNI="/root/MioLauncher/src/Amethyst-Android-3_openjdk/app_pojavlauncher/src/main/jni"
NDK="${ANDROID_NDK_HOME:-/root/android-sdk/ndk/27.3.13750724}"
SYSROOT="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
RESDIR="$NDK/toolchains/llvm/prebuilt/linux-x86_64/lib/clang/18"
OUT="$ROOT/src/main/jniLibs/arm64-v8a/libpojavexec.so"

mkdir -p "$(dirname "$OUT")"

SRCS="
  $AMETHYST_JNI/bigcoreaffinity.c
  $AMETHYST_JNI/egl_bridge.c
  $AMETHYST_JNI/ctxbridges/loader_dlopen.c
  $AMETHYST_JNI/ctxbridges/gl_bridge.c
  $AMETHYST_JNI/ctxbridges/osm_bridge.c
  $AMETHYST_JNI/ctxbridges/egl_loader.c
  $AMETHYST_JNI/ctxbridges/osmesa_loader.c
  $AMETHYST_JNI/ctxbridges/swap_interval_no_egl.c
  $AMETHYST_JNI/environ/environ.c
  $AMETHYST_JNI/jvm_hooks/emui_iterator_fix_hook.c
  $AMETHYST_JNI/jvm_hooks/java_exec_hooks.c
  $AMETHYST_JNI/jvm_hooks/lwjgl_dlopen_hook.c
  $AMETHYST_JNI/input_bridge_v3.c
  $AMETHYST_JNI/jre_launcher.c
  $AMETHYST_JNI/utils.c
  $AMETHYST_JNI/stdio_is.c
  $AMETHYST_JNI/driver_helper/nsbypass.c
"

clang \
  --target=aarch64-linux-android26 \
  --sysroot="$SYSROOT" \
  -nostdlib \
  -fPIC -shared -O2 \
  -DADRENO_POSSIBLE \
  -I "$AMETHYST_JNI" \
  -I "$AMETHYST_JNI/include" \
  -I "$AMETHYST_JNI/environ" \
  -I "$AMETHYST_JNI/ctxbridges" \
  -I "$AMETHYST_JNI/jvm_hooks" \
  -I "$AMETHYST_JNI/driver_helper" \
  -o "$OUT" \
  $SRCS \
  "$SYSROOT/usr/lib/aarch64-linux-android/26/crtbegin_so.o" \
  -L"$SYSROOT/usr/lib/aarch64-linux-android/26" \
  -L"$RESDIR/lib/linux" \
  -ldl -llog -landroid -lc -lm -lEGL -lGLESv2 \
  -l:libclang_rt.builtins-aarch64-android.a \
  "$SYSROOT/usr/lib/aarch64-linux-android/26/crtend_so.o"

echo "OK: $OUT"

# liblinkerhook.so：Turnip 加载辅助（egl_bridge.c 通过它把自定义驱动句柄
# 注入 android_dlopen_ext / sphal 命名空间）。源码 driver_helper/hook.c。
HOOK_OUT="$ROOT/src/main/jniLibs/arm64-v8a/liblinkerhook.so"

clang \
  --target=aarch64-linux-android26 \
  --sysroot="$SYSROOT" \
  -nostdlib \
  -fPIC -shared -O2 \
  -I "$AMETHYST_JNI/driver_helper" \
  -o "$HOOK_OUT" \
  "$AMETHYST_JNI/driver_helper/hook.c" \
  "$SYSROOT/usr/lib/aarch64-linux-android/26/crtbegin_so.o" \
  -L"$SYSROOT/usr/lib/aarch64-linux-android/26" \
  -lc -lm \
  -l:libclang_rt.builtins-aarch64-android.a \
  "$SYSROOT/usr/lib/aarch64-linux-android/26/crtend_so.o"

echo "OK: $HOOK_OUT"
