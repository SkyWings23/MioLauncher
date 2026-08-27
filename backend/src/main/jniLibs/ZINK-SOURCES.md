# Zink 渲染器第三方二进制来源与许可

本目录下与 Zink 渲染器相关的预编译库来源如下。全部为 Mesa 3D Graphics Library
（MIT 许可）的构建产物，未修改任何二进制内容。

| 文件 | 来源 | 说明 |
| --- | --- | --- |
| libEGL_mesa.so | IronizedZink v1.1.0 APK（github.com/GoyDevv/IronizedZink） | Mesa EGL（Android/Kopper 平台） |
| libglapi.so | 同上 | Mesa 共享 GLAPI |
| libglxshim.so | 同上 | 桌面 GL 入口 shim（供 LWJGL dlopen） |
| libzink_dri.so | 同上 | Zink Gallium 驱动（GL over Vulkan） |
| libcutils.so | 同上 | Mesa Android 构建所需的 cutils 存根 |
| libvulkan_freedreno.so | K11MCH1/AdrenoToolsDrivers v25.3.0-R11（Turnip_v25.3.0_R11.zip 内 vulkan.ad07xx.so 重命名） | Turnip Vulkan 驱动（Adreno a6xx/a7xx），meta：Vulkan 1.4.328 |

- Mesa 许可证文本：https://docs.mesa3d.org/license.html
- liblinkerhook.so 由本仓库 `src/Amethyst-Android-3_openjdk/app_pojavlauncher/src/main/jni/driver_helper/hook.c` 编译（见 backend/scripts/build_native.sh）
- Turnip 需 Android 9 (API 28)+；低版本或非 Adreno 设备自动回退系统 Vulkan 驱动
