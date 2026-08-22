package org.jackhuang.hmcl.util.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 跨平台资源加载：在 Android 上优先从 APK assets 读取 /assets 前缀资源，
 * 在 JVM 桌面环境回退到 classpath。避免 HMCLCore 的 getResourceAsStream("/assets/...")
 * 在 Android 上失效的问题。
 *
 * 使用方式：{@link #open(String)} 传入以 /assets/ 开头的资源路径。
 */
public final class MioAssets {

    private MioAssets() {
    }

    /**
     * 打开资源。路径形如 /assets/game/versions.txt。
     * Android 上从 assets 读取；桌面 JVM 回退 classpath。
     */
    public static @Nullable InputStream open(String path) {
        String assetPath = stripPrefix(path);
        InputStream androidStream = openFromAndroidAssets(assetPath);
        if (androidStream != null) return androidStream;

        InputStream classpathStream = openFromClasspath(path);
        if (classpathStream != null) return classpathStream;
        return null;
    }

    public static @NotNull String readAsString(String path) throws IOException {
        InputStream in = open(path);
        if (in == null) throw new IOException("Resource not found: " + path);
        try (InputStream is = in) {
            byte[] bytes = readFullyBytes(is);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /** 流式读取全部字节（readAllBytes 需 API 33，手动实现兼容所有版本） */
    private static byte[] readFullyBytes(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = is.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static @Nullable InputStream openFromAndroidAssets(String assetPath) {
        try {
            Class<?> contextClass = Class.forName("android.content.Context");
            Class<?> applicationClass = Class.forName("android.app.Application");
            // 尝试通过当前进程的 Application 实例获取 assets
            Object app = getCurrentApplication();
            if (app != null && contextClass.isInstance(app)) {
                Object assets = contextClass.getMethod("getAssets").invoke(app);
                return (InputStream) assets.getClass().getMethod("open", String.class).invoke(assets, assetPath);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static @Nullable Object getCurrentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("currentApplication").invoke(null);
            return thread;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable InputStream openFromClasspath(String path) {
        try {
            InputStream in = MioAssets.class.getResourceAsStream(path);
            if (in != null) return in;
            // 部分环境需要去掉前导斜杠
            String stripped = path.startsWith("/") ? path.substring(1) : path;
            in = MioAssets.class.getClassLoader().getResourceAsStream(stripped);
            return in;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String stripPrefix(String path) {
        return path.startsWith("/assets/") ? path.substring("/assets/".length()) : path;
    }
}
