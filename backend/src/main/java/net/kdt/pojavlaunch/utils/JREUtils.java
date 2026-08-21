package net.kdt.pojavlaunch.utils;

/**
 * 对应 libpojavexec.so 中的 native 方法。
 */
public final class JREUtils {
    private JREUtils() {}

    /** 调用 Android 隐藏 API android_update_LD_LIBRARY_PATH，让 dlopen 搜索指定路径。 */
    public static native void setLdLibraryPath(String ldLibraryPath);

    /** dlopen 指定库（名称或绝对路径），返回是否成功。 */
    public static native boolean dlopen(String name);

    /** 设置 pojav_environ 中的 ART JavaVM 指针。 */
    public static native void setDalvikJavaVM();

    /** 把进程 stdout/stderr 重定向到指定文件（进程内 JVM 输出直达文件）。 */
    public static native void redirectStdout(String path);

    /** 切换进程工作目录。 */
    public static native int chdir(String path);

    /** 把 Android Surface 设置给 native 渲染桥（GLFW 窗口表面）。 */
    public static native void setupBridgeWindow(android.view.Surface surface);

    /** 设置退出 trap（nominal_exit 依赖的 JavaVM/ExitActivity 引用）。 */
    public static native void setupExitMethod(android.content.Context context);
}
