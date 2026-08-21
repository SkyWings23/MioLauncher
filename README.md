# MioLauncher

> 自由 · 开源 · 属于你的 Minecraft Java 版手机启动器

在 Android 手机上直接运行 **Minecraft: Java Edition**，内置 Java 运行时，开箱即玩。

## ✨ 功能

- 🚀 **手机直接运行 Java 版 Minecraft**（内置 Java 21 / 25 运行时，按版本自动选择）
- 📥 **一键下载** 游戏版本（原版 / Fabric / Quilt / Forge / NeoForge / OptiFine）
- 🎮 **资源中心**：模组 / 光影 / 整合包（Modrinth 实时数据）
- 🌐 **联机**：本机开服 + 局域网 + cpolar 公网隧道
- 🎛️ **启动设置**：渲染器 / 内存（含扩展）/ 分辨率 / 性能档位 / JVM 参数
- ⌨️ **自定义虚拟键位**，支持游戏内呼出输入法（聊天/指令）
- 📄 **崩溃日志自动分析**，自动诊断问题并给出处理建议
- 🛡️ 离线账户，无需正版即可游玩

## 📲 安装

下载 APK 安装即可，首次启动会自动解压 Java 运行时。

> 支持 Android 8.0+ (API 26+)，ARM64 / ARMv7 / x86_64

## 🎮 支持的版本

| 版本范围 | Java | 状态 |
| --- | --- | --- |
| 1.21.x 及更早 | Java 21 | ✅ 完全支持 |
| 26.x（需 Java 25） | Java 25 | ⚠️ 打包中，待上游 JRE 修复 |

## 🛠️ 构建

```bash
# 需要 JDK 17+ 和 Android SDK
./gradlew :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 📄 许可证

[GPL-3.0](LICENSE)

## 💬 交流

- 玩家交流群：1079023595
- Bug 提交群：601765045
