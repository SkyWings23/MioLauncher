package com.miolauncher.backend;

/**
 * 游戏启动配置（由启动器侧 LaunchSettings 映射而来）。
 * 透传给 GameLaunch / JRE 以生效：分辨率、距离、帧率、FOV、语言、VSync、粒子、附加 JVM 参数。
 */
public final class LaunchConfig {
    public int resolutionScale = 100;
    public int renderDistance = 4;
    public int simulationDistance = 5;
    public int maxFps = 60;        // 0 = 无上限
    public int fov = 70;
    public int guiScale = 0;       // 0 = 自动
    public String lang = "zh_cn";
    public boolean vsync = false;
    public int particles = 1;      // 0..3
    public String extraJvmArgs = "";
}
