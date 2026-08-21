package net.kdt.pojavlaunch;

/**
 * 对应 libpojavexec.so 中的 Logger native 方法（stdio_is.c）。
 * 用于捕获进程内 JVM 的 stdout/stderr 到文件 + 回调。
 */
public final class Logger {

    public interface eventLogListener {
        void onEventLogged(String message);
    }

    private Logger() {}

    /** 重定向 stdout/stderr 到管道，后台线程写入 logPath 文件。 */
    public static native void begin(String logPath);

    public static native void appendToLog(String text);

    public static native void setLogListener(eventLogListener listener);
}
