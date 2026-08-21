package javafx.application;

import java.util.concurrent.Executor;

/**
 * 兼容包：把 JavaFX 的 Platform.runLater 映射到 Android 主线程。
 * 在非 Android 环境（如本机沙箱测试）下回退到 JVM 上直接执行。
 */
public final class Platform {

    private static volatile Object mainHandler;
    private static volatile Executor fallbackExecutor;

    private Platform() {
    }

    public static void initPlatform() {
        if (mainHandler != null) return;
        try {
            Class.forName("android.os.Handler");
            mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        } catch (Throwable ignored) {
            // 非 Android 环境，使用直接执行
            fallbackExecutor = Runnable::run;
            mainHandler = new Object();
        }
    }

    public static boolean isFxApplicationThread() {
        if (fallbackExecutor != null) return true;
        try {
            return android.os.Looper.myLooper() == android.os.Looper.getMainLooper();
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void runLater(Runnable runnable) {
        if (mainHandler == null) {
            initPlatform();
        }
        if (fallbackExecutor != null) {
            fallbackExecutor.execute(runnable);
            return;
        }
        ((android.os.Handler) mainHandler).post(runnable);
    }
}
