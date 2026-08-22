package com.miolauncher.backend;

import android.content.Context;
import android.os.Build;
import android.system.Os;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 进程内 JVM 启动器。
 *
 * 复用 Amethyst/PojavLauncher 的 jre_launcher.c（native pojavexec），
 * 在 app 进程内通过 JLI_Launch 拉起 Java 运行时。
 * JRE 使用 PojavLauncher 的 bionic 构建（APK assets 打包）。
 */
public final class JRE {

    /** 默认 JRE 主版本（APK 内置的最低保障版本） */
    public static final int DEFAULT_JAVA_MAJOR = 21;
    /** 旧版 JRE 目录名（兼容） */
    public static final String JRE_DIR = "mio/jre21";

    private JRE() {}

    /** 指定主版本对应的 JRE 目录（filesDir/mio/jre<javaMajor>） */
    public static File jreDir(Context context, int javaMajor) {
        return new File(context.getFilesDir(), "mio/jre" + javaMajor);
    }

    /**
     * 返回默认（21）JRE 主目录，若不存在则返回 null。
     */
    public static File getJreHome(Context context) {
        return getJreHome(context, DEFAULT_JAVA_MAJOR);
    }

    /** 返回指定主版本的 JRE 主目录，若不存在则返回 null。 */
    public static File getJreHome(Context context, int javaMajor) {
        File dir = jreDir(context, javaMajor);
        return dir.isDirectory() ? dir : null;
    }

    public static boolean isInstalled(Context context) {
        return isInstalled(context, DEFAULT_JAVA_MAJOR);
    }

    public static boolean isInstalled(Context context, int javaMajor) {
        File jre = jreDir(context, javaMajor);
        if (!jre.isDirectory() || !new File(jre, "lib/server/libjvm.so").exists()) return false;
        // Java 9+ 有 lib/modules；Java 8 用 lib/rt.jar（可能被 pack 为 .pack）
        if (javaMajor >= 9) {
            return new File(jre, "lib/modules").exists();
        } else {
            return new File(jre, "lib/rt.jar").exists()
                    || new File(jre, "lib/rt.jar.pack").exists();
        }
    }

    /**
     * 确保指定版本的 JRE 已安装（未安装则从 assets 解压）。
     * 版本资产缺失时回退到默认 21（避免旧 APK 资产不齐全导致启动失败）。
     */
    public static void ensureInstalled(Context context, int javaMajor) throws Exception {
        if (isInstalled(context, javaMajor)) return;
        try {
            install(context, javaMajor, null);
        } catch (java.io.IOException e) {
            android.util.Log.w("MioJRE", "JRE " + javaMajor + " 资产缺失，回退到 " + DEFAULT_JAVA_MAJOR, e);
            install(context, DEFAULT_JAVA_MAJOR, null);
        }
    }

    /** 运行时资源目录（lwjgl.jar / MioLibPatcher.jar 解压到这里）。 */
    public static File getRuntimeDir(Context context) {
        File dir = new File(context.getFilesDir(), "mio/runtime");
        dir.mkdirs();
        return dir;
    }

    /**
     * 从 APK assets 解压运行时 jar（FCL 的 lwjgl.jar 与 MioLibPatcher.jar）。
     */
    public static void extractRuntime(Context context) throws Exception {
        File dir = getRuntimeDir(context);
        String[] files = {
                "runtime/lwjgl.jar", "runtime/lwjglx.jar", "runtime/MioLibPatcher.jar", "runtime/MioExitAgent.jar", "runtime/DumpAgent.jar",
                "runtime/caciocavallo17/cacio-agent.jar",
                "runtime/caciocavallo17/cacio-shared-1.19.1-SNAPSHOT.jar",
                "runtime/caciocavallo17/cacio-tta-1.19.1-SNAPSHOT.jar",
                "runtime/caciocavallo/cacio-androidnw-1.10-SNAPSHOT.jar",
                "runtime/caciocavallo/cacio-shared-1.10-SNAPSHOT.jar"};
        for (String asset : files) {
            String name = asset.substring(asset.lastIndexOf('/') + 1);
            File out;
            if (asset.startsWith("runtime/caciocavallo17/")) {
                out = new File(new File(dir, "caciocavallo17"), name);
            } else if (asset.startsWith("runtime/caciocavallo/")) {
                out = new File(new File(dir, "caciocavallo"), name);
            } else {
                out = new File(dir, name);
            }
            if (out.isFile() && out.length() > 0) continue;
            out.getParentFile().mkdirs();
            try (InputStream in = context.getAssets().open(asset);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
            }
        }
    }

    /**
     * 当前运行 ABI 对应的 JRE 资产名（assets/components/jre-<major>/ 下的合并包）。
     */
    public static String jreAssetForAbi(Context context, int javaMajor) {
        String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";
        String v = "jre" + javaMajor;
        if (abi.startsWith("armeabi")) return v + "-armeabi-v7a.tar.xz";
        if (abi.startsWith("x86_64") || abi.startsWith("x86")) return v + "-x86_64.tar.xz";
        return v + "-arm64-v8a.tar.xz";
    }

    /** 默认（21）版本资产名 */
    public static String jreAssetForAbi(Context context) {
        return jreAssetForAbi(context, DEFAULT_JAVA_MAJOR);
    }

    /**
     * 从 APK assets 解压 JRE（首次启动调用）。默认安装 Java 21。
     */
    public static void install(Context context, java.util.function.DoubleConsumer onProgress) throws Exception {
        install(context, DEFAULT_JAVA_MAJOR, onProgress);
    }

    /**
     * 从 APK assets 解压指定版本的 JRE。
     *
     * @param onProgress 进度回调 (0..1)
     */
    public static void install(Context context, int javaMajor, java.util.function.DoubleConsumer onProgress) throws Exception {
        File jreHome = jreDir(context, javaMajor);
        if (isInstalled(context, javaMajor)) return;

        File tmp = new File(context.getFilesDir(), "mio/jre" + javaMajor + ".tmp");
        deleteRecursively(tmp);
        tmp.mkdirs();

        String asset = "components/jre-" + javaMajor + "/" + jreAssetForAbi(context, javaMajor);
        InputStream in = context.getAssets().open(asset);
        XZCompressorInputStream xz = new XZCompressorInputStream(new BufferedInputStream(in));
        TarArchiveInputStream tar = new TarArchiveInputStream(xz);
        TarArchiveEntry entry;
        long total = context.getAssets().open(asset).available();
        long done = 0;
        while ((entry = tar.getNextEntry()) != null) {
            File out = new File(tmp, entry.getName());
            if (entry.isDirectory()) {
                out.mkdirs();
            } else {
                out.getParentFile().mkdirs();
                FileOutputStream fos = new FileOutputStream(out);
                byte[] buf = new byte[65536];
                int n;
                long fileTotal = entry.getSize();
                long fileDone = 0;
                while ((n = tar.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                    fileDone += n;
                    if (onProgress != null && fileTotal > 0) {
                        onProgress.accept((done + fileDone / (double) Math.max(fileTotal, 1)) / total);
                    }
                }
                fos.close();
            }
            done += entry.getSize();
        }
        tar.close();

        // 设置执行权限（assets 解压后默认无 exec 位）
        setExecutable(tmp);

        // Java 8 JRE 的 jar 以 .pack 压缩格式分发，需用 libunpack200.so 解包为 .jar
        unpackPackedJars(context, tmp);

        File old = new File(context.getFilesDir(), "mio/jre" + javaMajor + ".old");
        deleteRecursively(old);
        if (jreHome.exists()) {
            if (!jreHome.renameTo(old)) {
                deleteRecursively(jreHome);
            }
        }
        if (!tmp.renameTo(jreHome)) {
            deleteRecursively(jreHome);
            if (!tmp.renameTo(jreHome)) {
                throw new IllegalStateException("JRE 解压失败：无法移动临时目录");
            }
        }
        deleteRecursively(old);
    }

    /**
     * Java 8 JRE 的 lib/*.jar.pack 需要 unpack200 解包为 .jar 才能使用。
     * 使用 APK 内置的 libunpack200.so（与 FCL 相同方式）。
     */
    private static void unpackPackedJars(Context context, File jreDir) {
        File unpack = new File(context.getApplicationInfo().nativeLibraryDir, "libunpack200.so");
        if (!unpack.isFile()) return;
        File lib = new File(jreDir, "lib");
        File[] packs = lib.listFiles((d, name) -> name.endsWith(".pack"));
        if (packs == null) return;
        for (File pack : packs) {
            String jarPath = pack.getAbsolutePath().replace(".pack", "");
            try {
                java.lang.Process p = new ProcessBuilder("./libunpack200.so", "-r",
                        pack.getAbsolutePath(), jarPath)
                        .directory(new File(context.getApplicationInfo().nativeLibraryDir))
                        .redirectErrorStream(true)
                        .start();
                p.waitFor();
                android.util.Log.i("MioJRE", "unpacked " + pack.getName());
            } catch (Exception e) {
                android.util.Log.w("MioJRE", "unpack200 failed for " + pack.getName(), e);
            }
        }
    }

    private static void setExecutable(File dir) {        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                setExecutable(f);
            } else if (f.getName().endsWith(".so") || f.getName().equals("java")) {
                f.setExecutable(true, false);
            }
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }

    /**
     * 启动进程内 JVM。
     *
     * @param args JVM 参数，如 {"-version"}
     * @return JLI_Launch 返回码（0 表示成功）
     */
    public static int launch(Context context, List<String> args) throws Exception {
        return launch(context, args, null);
    }

    public static int launch(Context context, List<String> args, File workDir) throws Exception {
        return launch(context, args, workDir, Renderer.NGGL4ES, false);
    }

    public static int launch(Context context, List<String> args, File workDir, Renderer renderer) throws Exception {
        return launch(context, args, workDir, renderer, false);
    }

    public static int launch(Context context, List<String> args, File workDir, Renderer renderer, boolean vsync) throws Exception {
        return launch(context, args, workDir, renderer, vsync, DEFAULT_JAVA_MAJOR);
    }

    /** 按指定 Java 主版本选择 JRE 启动（如 26.x 用 Java 25） */
    public static int launch(Context context, List<String> args, File workDir, Renderer renderer, boolean vsync, int javaMajor) throws Exception {
        File jreHome = getJreHome(context, javaMajor);
        if (jreHome == null) {
            throw new IllegalStateException("JRE " + javaMajor + " 未安装，请先下载 Java 运行时");
        }
        setEnvironment(jreHome, context.getApplicationInfo().nativeLibraryDir, renderer, vsync, javaMajor);

        System.loadLibrary("pojavexec");
        net.kdt.pojavlaunch.utils.JREUtils.setDalvikJavaVM();
        net.kdt.pojavlaunch.utils.JREUtils.setLdLibraryPath(ldLibraryPath(jreHome, javaMajor));

        // 切换到游戏工作目录（native 库已加载）
        if (workDir != null) {
            net.kdt.pojavlaunch.utils.JREUtils.chdir(workDir.getAbsolutePath());
        }
        // 验证 LWJGL 库能否从 app nativeLibraryDir 加载
        try {
            android.util.Log.d("MioJRE", "nativeDir=" + context.getApplicationInfo().nativeLibraryDir);
            android.util.Log.d("MioJRE", "lwjglAbs=" + net.kdt.pojavlaunch.utils.JREUtils.dlopen(
                    context.getApplicationInfo().nativeLibraryDir + "/liblwjgl.so"));
            android.util.Log.d("MioJRE", "lwjglByName=" + net.kdt.pojavlaunch.utils.JREUtils.dlopen("liblwjgl.so"));
        } catch (Throwable t) {
            android.util.Log.e("MioJRE", "lwjgl dlopen test failed", t);
        }

        // 通知 JVM 与 ART 共存（PojavLauncher 标准做法）
        long artVm = net.kdt.pojavlaunch.Tools.getJavaVMPointer();
        Os.setenv("DALVIK_JAVAVM", Long.toHexString(artVm), true);
        Os.setenv("DALVIK_APPLICATION", context.getPackageName(), true);

        // MioLauncher: 把崩溃标记路径传给 native 退出钩子——游戏干净退出（code 0）时由
        // nominal_exit 删除标记，避免正常退出后下次启动误报崩溃。
        if (workDir != null) {
            Os.setenv("MIO_CRASH_MARKER", new File(workDir, ".mio_crash_marker").getAbsolutePath(), true);
        }

        // 预加载 JVM 运行所需的核心库，使后续 dlopen("libjli.so") 能找到。
        String jre = jreHome.getAbsolutePath();
        // Java 8 的 JVM 库在 lib/<abi>/ 下（如 lib/aarch64/），Java 9+ 在 lib/ 下。
        boolean java8 = javaMajor < 9;
        String libRoot = java8 ? libRootForAbi(jreHome) : jre + "/lib";
        preload(jre + "/lib/libjli.so");
        preload(libRoot + "/jli/libjli.so");
        preload(jre + "/lib/server/libjvm.so");
        preload(libRoot + "/server/libjvm.so");
        // 按依赖顺序预加载核心 JVM 库。Java 8 的 libjava/libnet/libnio 互相依赖，
        // 且依赖 libverify/libzip，顺序错误会 dlopen 失败。这里先加载底层库再加载上层。
        String[] coreLibs = {
            "libverify.so", "libzip.so", "libjimage.so", "libextnet.so",
            "libjava.so", "libnet.so", "libnio.so",
            "libsunec.so", "libjsig.so", "libinstrument.so", "libj2pkcs11.so",
            "libj2gss.so", "libprefs.so", "librmi.so", "libsctp.so",
            "libdt_socket.so", "libjdwp.so", "libsyslookup.so",
            "libunpack.so",
        };
        for (String lib : coreLibs) {
            preload(libRoot + "/" + lib);
        }
        // 失败的库重试（依赖可能由后续库补齐）
        for (String lib : coreLibs) {
            preload(libRoot + "/" + lib);
        }
        // 预加载其余所有共享库（失败的如 AWT 类可忽略）
        File libDir = new File(libRoot);
        File[] libs = libDir.listFiles((dir, name) -> name.endsWith(".so"));
        if (libs != null) {
            for (File lib : libs) {
                preload(lib.getAbsolutePath());
            }
        }

        // 捕获 JVM stdout/stderr 到日志文件（进程内 JVM 输出默认不进 logcat）
        try {
            File logDir = new File(context.getFilesDir(), "mio/logs");
            logDir.mkdirs();
            File logFile = new File(logDir, "game.log");
            net.kdt.pojavlaunch.utils.JREUtils.redirectStdout(logFile.getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("MioJRE: redirect failed: " + t);
        }

        // MioLauncher: 设置 exit trap（nominal_exit 依赖 exitTrap_jvm，缺失时 SIGABRT 二次 SIGSEGV 挂死）
        try {
            net.kdt.pojavlaunch.utils.JREUtils.setupExitMethod(context);
        } catch (Throwable t) {
            System.out.println("MioJRE: setupExitMethod failed: " + t);
        }

        List<String> fullArgs = new ArrayList<>();
        // argv[0] 需满足 dirname(argv[0]) 与 LD_LIBRARY_PATH 首段一致，
        // 否则 launcher 会尝试 re-exec（Android 上被 SELinux 拒绝）。
        // Java 8 用 bin/java（无 lib/java），Java 9+ 用 lib/java。
        String launcherBinary = java8 ? jre + "/bin/java" : jre + "/lib/java";
        fullArgs.add(launcherBinary);
        fullArgs.add("-Djava.home=" + jre);
        fullArgs.addAll(args);

        System.out.println("MioJRE: launching JVM with args " + fullArgs);
        return com.oracle.dalvik.VMLauncher.launchJVM(fullArgs.toArray(new String[0]));
    }

    /**
     * 服务器专用轻量启动：跳过游戏专用的 GL / lwjgl / cacio 初始化。
     * 供独立 :server 进程内运行 Minecraft 服务器（JLI_Launch，无 SELinux exec 限制）。
     */
    public static int launchServer(Context context, List<String> args, File workDir) throws Exception {
        File jreHome = getJreHome(context);
        if (jreHome == null) {
            throw new IllegalStateException("JRE 未安装");
        }
        String nativeDir = context.getApplicationInfo().nativeLibraryDir;

        // 最小环境：JAVA_HOME / LD_LIBRARY_PATH / PATH / DALVIK
        Os.setenv("JAVA_HOME", jreHome.getAbsolutePath(), true);
        String libDir = jreHome.getAbsolutePath() + "/lib";
        String ld = jreHome.getAbsolutePath() + "/lib/server" + ":" + libDir
                + ":" + jreHome.getAbsolutePath() + "/lib/jli" + ":" + nativeDir;
        String oldLd = System.getenv("LD_LIBRARY_PATH");
        if (oldLd != null && !oldLd.isEmpty()) ld = ld + ":" + oldLd;
        Os.setenv("LD_LIBRARY_PATH", ld, true);
        Os.setenv("PATH", jreHome.getAbsolutePath() + "/bin", true);

        System.loadLibrary("pojavexec");
        net.kdt.pojavlaunch.utils.JREUtils.setDalvikJavaVM();
        if (workDir != null) {
            net.kdt.pojavlaunch.utils.JREUtils.chdir(workDir.getAbsolutePath());
        }
        long artVm = net.kdt.pojavlaunch.Tools.getJavaVMPointer();
        Os.setenv("DALVIK_JAVAVM", Long.toHexString(artVm), true);
        Os.setenv("DALVIK_APPLICATION", context.getPackageName(), true);

        // 预加载 JVM 核心库
        String jre = jreHome.getAbsolutePath();
        preload(jre + "/lib/libjli.so");
        preload(jre + "/lib/server/libjvm.so");
        String[] coreLibs = {
            "libjava.so", "libnet.so", "libnio.so", "libzip.so",
            "libjimage.so", "libverify.so", "libextnet.so",
            "libsunec.so", "libjsig.so", "libinstrument.so", "libj2pkcs11.so",
            "libj2gss.so", "libprefs.so", "librmi.so", "libsctp.so",
            "libdt_socket.so", "libjdwp.so", "libsyslookup.so",
        };
        for (String lib : coreLibs) {
            preload(jre + "/lib/" + lib);
        }
        File libDirFile = new File(jreHome, "lib");
        File[] libs = libDirFile.listFiles((dir, name) -> name.endsWith(".so"));
        if (libs != null) {
            for (File f : libs) preload(f.getAbsolutePath());
        }

        // 服务器 stdout → 服务器日志（MC 自身也写 logs/latest.log）
        try {
            File serverDir = workDir == null ? new File(jre, "server") : workDir;
            File logDir = new File(serverDir, "logs");
            logDir.mkdirs();
            net.kdt.pojavlaunch.utils.JREUtils.redirectStdout(new File(logDir, "jvm.out.log").getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("MioJRE: server redirect failed: " + t);
        }

        List<String> fullArgs = new ArrayList<>();
        fullArgs.add(jre + "/lib/java");
        fullArgs.add("-Djava.home=" + jre);
        fullArgs.addAll(args);
        System.out.println("MioJRE: launching server JVM");
        return com.oracle.dalvik.VMLauncher.launchJVM(fullArgs.toArray(new String[0]));
    }

    private static void preload(String path) {
        boolean ok = net.kdt.pojavlaunch.utils.JREUtils.dlopen(path);
        if (!ok) {
            // dlopen 失败：记录文件是否存在及 dlerror（JREUtils 会打印 dlerror）
            System.out.println("MioJRE: preload " + path + " -> false");
        } else {
            System.out.println("MioJRE: preload " + path + " -> true");
        }
    }

    private static String ldLibraryPath(File jreHome, int javaMajor) {
        String jre = jreHome.getAbsolutePath();
        boolean java8 = javaMajor < 9;
        String libRoot = java8 ? libRootForAbi(jreHome) : jre + "/lib";
        String base = libRoot + ":" + libRoot + "/jli" + ":" + libRoot + "/server";
        String old = System.getenv("LD_LIBRARY_PATH");
        return (old == null || old.isEmpty()) ? base : base + ":" + old;
    }

    /** Java 8 的 JVM 库目录：lib/<abi>/（arm64 → lib/aarch64，armeabi → lib/arm） */
    private static String libRootForAbi(File jreHome) {
        String[] abis = android.os.Build.SUPPORTED_ABIS;
        String abi = (abis != null && abis.length > 0) ? abis[0] : "arm64-v8a";
        if (abi.startsWith("armeabi")) {
            File arm = new File(jreHome, "lib/arm");
            return arm.isDirectory() ? arm.getAbsolutePath() : new File(jreHome, "lib/aarch64").getAbsolutePath();
        }
        if (abi.startsWith("x86")) {
            File x86 = new File(jreHome, "lib/i386");
            return x86.isDirectory() ? x86.getAbsolutePath() : new File(jreHome, "lib/amd64").getAbsolutePath();
        }
        return new File(jreHome, "lib/aarch64").getAbsolutePath();
    }

    /**
     * 设置 JVM 运行所需的环境变量：JAVA_HOME / LD_LIBRARY_PATH / PATH / 渲染后端。
     */
    private static void setEnvironment(File jreHome, String nativeDir, Renderer renderer, boolean vsync, int javaMajor) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            throw new IllegalStateException("需要 Android 8.0+");
        }
        String jre = jreHome.getAbsolutePath();
        boolean java8 = javaMajor < 9;
        String libDir = java8 ? libRootForAbi(jreHome) : jre + "/lib";
        String jliDir = java8 ? libDir + "/jli" : jre + "/lib/jli";
        String serverDir = java8 ? libDir + "/server" : jre + "/lib/server";
        String jvmPath = serverDir + "/libjvm.so";

        Os.setenv("JAVA_HOME", jre, true);
        // POJAV_ENVIRON 不设置，让 env_init 构造函数自动创建 pojav_environ
        // （若设为空串，strtoul("") 会得到 NULL，导致第二次加载崩溃）。

        // 渲染后端：按所选 Renderer 配置 EGL / OpenGL ES 环境。
        Os.setenv("AMETHYST_RENDERER", renderer.getAmethystRenderer(), true);
        Os.setenv("POJAV_RENDERER", renderer.getGlEsVersion() >= 3 ? "opengles3" : "opengles2", true);
        Os.setenv("POJAVEXEC_EGL", renderer.getEglLibName(), true);
        Os.setenv("FORCE_VSYNC", vsync ? "true" : "false", true);

        if (renderer.isGl4es()) {
            // gl4es 家族（NGGL4ES / GL4ES）：GL → GLES 翻译
            Os.setenv("LIBGL_ES", Integer.toString(renderer.getGlEsVersion()), true);
            Os.setenv("LIBGL_GL", renderer.getGlVersionCode(), true);
            Os.setenv("LIBGL_NORMALIZE", "1", true);
            Os.setenv("LIBGL_NOINTOVLHACK", "1", true);
            Os.setenv("LIBGL_NOERROR", "1", true);
            // 禁用 gl4es 的"全屏 Blit 到默认 FBO 时触发 SwapBuffers" hack：
            // 该 hack 每帧触发额外 swap（双倍产出缓冲），既造成菜单背景闪烁，
            // 也会填满 EGL 缓冲队列导致 eglSwapBuffers 阻塞（渲染线程卡死）。
            Os.setenv("LIBGL_FEATURES", "-BLITFULLSCREEN", true);
            Os.setenv("LIBGL_USE_MC_COLOR", "1", true);
        } else {
            // ANGLE / OSMesa：不经 gl4es，无需 LIBGL_* 系列
            Os.unsetenv("LIBGL_ES");
            Os.unsetenv("LIBGL_GL");
            Os.unsetenv("LIBGL_NORMALIZE");
            Os.unsetenv("LIBGL_NOINTOVLHACK");
            Os.unsetenv("LIBGL_NOERROR");
            Os.unsetenv("LIBGL_FEATURES");
            Os.unsetenv("LIBGL_USE_MC_COLOR");
        }

        // 通用 env：native 库目录 + spirv-cross（着色器编译）+ TMPDIR
        Os.setenv("POJAV_NATIVEDIR", nativeDir, true);
        Os.setenv("DRIVER_PATH", nativeDir, true);
        Os.setenv("DLOPEN", "libspirv-cross-c-shared.so", true);
        Os.setenv("TMPDIR", System.getProperty("java.io.tmpdir"), true);
        // FCL 用 cacio.managed.screensize + glfwstub 窗口尺寸，不再设 AWTSTUB
        // 对齐 FCL：mod 运行时目录（占位，与 FCL app_runtime_mod 对应）
        File modDir = new File(jreHome.getParentFile(), "runtime_mod");
        modDir.mkdirs();
        Os.setenv("MOD_ANDROID_RUNTIME", modDir.getAbsolutePath(), true);

        // launcher 的 SetEnvironmentVariables 要求 LD_LIBRARY_PATH 以 jvmpath 开头，
        // 否则会尝试 re-exec（Android 上被 SELinux 拒绝）。故把 jvmpath 放最前。
        String ld = jvmPath + ":" + libDir + ":" + jliDir + ":" + serverDir;
        // 对齐 FCL：把 /vendor/lib64/hw（Mali GLES 驱动目录）等系统目录放入搜索路径，
        // 使 gl4es/EGL 能 dlopen 到真实驱动、dlsym(RTLD_DEFAULT) 能解析 glBufferStorage。
        ld = ld + ":/system/lib64:/vendor/lib64:/vendor/lib64/hw:/system_ext/lib64:" + nativeDir;
        String oldLd = System.getenv("LD_LIBRARY_PATH");
        if (oldLd != null && !oldLd.isEmpty()) {
            ld = ld + ":" + oldLd;
        }
        Os.setenv("LD_LIBRARY_PATH", ld, true);

        String path = jre + "/bin";
        String oldPath = System.getenv("PATH");
        if (oldPath != null && !oldPath.isEmpty()) {
            path = path + ":" + oldPath;
        }
        Os.setenv("PATH", path, true);
    }
}
