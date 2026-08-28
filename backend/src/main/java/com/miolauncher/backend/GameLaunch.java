package com.miolauncher.backend;

import android.content.Context;

import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.launch.DefaultLauncher;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 使用 HMCLCore 生成 Minecraft 启动命令行，并在进程内拉起游戏 JVM。
 */
public final class GameLaunch {

    private GameLaunch() {}

    /**
     * 生成游戏启动参数（JVM args + classpath + 主类 + 游戏参数）。
     *
     * @param gameDir   游戏目录（.minecraft 同级）
     * @param versionId 版本 ID
     * @param username  离线用户名
     * @return 完整命令行（首元素为 java 路径）
     */
    public static List<String> buildCommand(
            Context context, File gameDir, String versionId, String username) throws Exception {
        return buildCommand(context, gameDir, versionId, username, 2316, 1080, 2048);
    }

    /**
     * 生成游戏启动参数，使用渲染分辨率作为游戏窗口尺寸。
     */
    public static List<String> buildCommand(
            Context context, File gameDir, String versionId, String username,
            int windowW, int windowH) throws Exception {
        return buildCommand(context, gameDir, versionId, username, windowW, windowH, 2048);
    }

    /**
     * 生成游戏启动参数，可指定最大内存（MB）。
     */
    public static List<String> buildCommand(
            Context context, File gameDir, String versionId, String username,
            int windowW, int windowH, int maxMemoryMb) throws Exception {
        return buildCommand(context, gameDir, versionId, username, windowW, windowH, maxMemoryMb, null);
    }

    /**
     * 生成游戏启动参数，可指定最大内存与要加入的服务器（host:port）。
     */
    public static List<String> buildCommand(
            Context context, File gameDir, String versionId, String username,
            int windowW, int windowH, int maxMemoryMb, String serverAddress) throws Exception {
        return buildCommand(context, gameDir, versionId, username, windowW, windowH, maxMemoryMb, serverAddress,
                (String) null, (String) null, "legacy", "{}");
    }

    /**
     * 生成游戏启动参数（完整认证信息版，支持 LittleSkin 外置登录）。
     * @param accessToken 登录后的 accessToken（离线时为 "0"）
     * @param uuid        玩家 UUID（离线时随机）
     * @param userType    mojang / legacy / msa
     * @param userProperties 用户属性 JSON（外置登录服务器返回）
     */
    public static List<String> buildCommand(
            Context context, File gameDir, String versionId, String username,
            int windowW, int windowH, int maxMemoryMb, String serverAddress,
            String accessToken, String uuid, String userType, String userProperties) throws Exception {
        DefaultGameRepository repository = new DefaultGameRepository(gameDir.toPath());
        repository.refresh();
        GameInstanceID id = new GameInstanceID(versionId);
        org.jackhuang.hmcl.game.GameInstanceManifest manifest =
                repository.getResolvedInstanceManifest(id).launchManifest();

        // 按版本所需 Java 主版本选择对应 JRE（8 / 17 / 21 / 25）
        int javaMajor = 21;
        if (manifest.javaVersion() != null) {
            int need = manifest.javaVersion().majorVersion();
            if (need >= 25) javaMajor = 25;
            else if (need >= 17) javaMajor = 21;
            else if (need >= 9) javaMajor = 17;
            else if (need > 0) javaMajor = 8;
        }
        // 确保对应 JRE 已安装（assets 内置，解压即可）
        JRE.ensureInstalled(context, javaMajor);
        File jreHome = JRE.getJreHome(context, javaMajor);
        if (jreHome == null) {
            throw new IllegalStateException("JRE " + javaMajor + " 未安装");
        }

        android.util.Log.d("MioGame", "javaMajor=" + javaMajor + " jreHome=" + jreHome.getAbsolutePath());
        android.util.Log.d("MioGame", "os.name=" + System.getProperty("os.name")
                + " os.arch=" + System.getProperty("os.arch"));
        android.util.Log.d("MioGame", "libraries=" + (manifest.libraries() == null ? 0 : manifest.libraries().size())
                + " classpath=" + repository.getClasspath(manifest));
        if (manifest.libraries() != null && !manifest.libraries().isEmpty()) {
            var lib = manifest.libraries().get(0);
            android.util.Log.d("MioGame", "lib0=" + lib.name() + " applies=" + lib.appliesToCurrentEnvironment()
                    + " native=" + lib.isNative());
            try {
                android.util.Log.d("MioGame", "lib0 file=" + repository.getLibraryFile(manifest, lib));
            } catch (Throwable t) {
                android.util.Log.e("MioGame", "lib0 file err", t);
            }
        }

        JavaInfo info = new JavaInfo(
                Platform.getPlatform(OperatingSystem.LINUX, Architecture.ARM64),
                javaMajor + ".0", null);
        JavaRuntime java = JavaRuntime.of(
                Paths.get(jreHome.getAbsolutePath(), "bin", "java"), info, false);

        LaunchOptions.Builder builder = new LaunchOptions.Builder()
                .setInstanceId(id)
                .setGameDir(gameDir.toPath())
                .setJava(java)
                // Xms 不跟 Xmx 同值：否则开跑即占满整个堆，白白抬高 RSS 引发系统内存压力。
                // 取 Xmx 一半但不超过 768m：让堆有足够初始空间，减少区块加载时频繁扩容 GC。
                .setMinMemory(Math.min(768, maxMemoryMb))
                .setMaxMemory(maxMemoryMb)
                .setWidth(windowW)
                .setHeight(windowH)
                .setNoGeneratedOptimizingJVMArgs(true)
                .setVersionName(versionId)
                .setVersionType("release");
        if (serverAddress != null && !serverAddress.isBlank()) {
            // 生成 --server <host> --port <port>（或不支持 quick play 时同样回退）
            builder.setQuickPlayOption(new org.jackhuang.hmcl.game.QuickPlayOption.MultiPlayer(serverAddress));
            android.util.Log.d("MioGame", "join server: " + serverAddress);
        }
        LaunchOptions options = builder.create();

        AuthInfo authInfo;
        if (accessToken != null && uuid != null && !accessToken.equals("0")) {
            // 外置登录（LittleSkin）：用真实 accessToken + uuid
            java.util.UUID realUuid = safeParseUuid(uuid);
            authInfo = new AuthInfo(username, realUuid, accessToken,
                    userType == null || userType.isBlank() ? "mojang" : userType,
                    userProperties == null ? "{}" : userProperties);
        } else {
            // 离线模式：用用户名生成稳定 UUID（与官方离线模式/常见服务器一致），
            // 避免每次启动随机 UUID 导致多人服务器/插件不认会话（"玩不了多人"）。
            authInfo = new AuthInfo(
                    username, offlineUuid(username), "0", "legacy", "{}");
        }

        DefaultLauncher launcher = new DefaultLauncher(
                repository, repository.getResolvedInstanceManifest(id).launchManifest(),
                authInfo, options, (ProcessListener) null);

        return launcher.buildRawCommand();
    }

    /** 离线模式稳定 UUID：与官方离线登录一致（nameUUIDFromBytes("OfflinePlayer:"+name)）。 */
    private static java.util.UUID offlineUuid(String username) {
        try {
            return java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return java.util.UUID.randomUUID();
        }
    }

    private static java.util.UUID safeParseUuid(String uuid) {
        try {
            return java.util.UUID.fromString(uuid);
        } catch (Exception e) {
            // 兼容无连字符的 UUID
            try {
                return java.util.UUID.fromString(uuid.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            } catch (Exception e2) {
                return java.util.UUID.randomUUID();
            }
        }
    }

    /**
     * 用给定的启动参数在进程内拉起游戏 JVM。
     *
     * @param rawCommand buildCommand 的返回值（首元素为 java 路径）
     * @return JLI_Launch 返回码
     */
    public static int launch(Context context, File gameDir, List<String> rawCommand) throws Exception {
        return launch(context, gameDir, rawCommand, 2316, 1080, Renderer.NGGL4ES, new LaunchConfig());
    }

    /**
     * 用给定的启动参数在进程内拉起游戏 JVM，使用实际 surface 尺寸对齐 glfwstub 窗口。
     */
    public static int launch(Context context, File gameDir, List<String> rawCommand,
                             int windowW, int windowH) throws Exception {
        return launch(context, gameDir, rawCommand, windowW, windowH, Renderer.NGGL4ES, new LaunchConfig());
    }

    /**
     * 用给定的启动参数在进程内拉起游戏 JVM，指定渲染后端。
     */
    public static int launch(Context context, File gameDir, List<String> rawCommand,
                             int windowW, int windowH, Renderer renderer) throws Exception {
        return launch(context, gameDir, rawCommand, windowW, windowH, renderer, new LaunchConfig());
    }

    /**
     * 用给定的启动参数在进程内拉起游戏 JVM，指定渲染后端与启动配置。
     */
    public static int launch(Context context, File gameDir, List<String> rawCommand,
                              int windowW, int windowH, Renderer renderer, LaunchConfig config) throws Exception {
        String versionId = findVersionId(rawCommand);
        // 按版本所需 Java 主版本选择对应 JRE（8 / 17 / 21 / 25）
        int javaMajor = 21;
        try {
            DefaultGameRepository repo = new DefaultGameRepository(gameDir.toPath());
            repo.refresh();
            org.jackhuang.hmcl.game.GameInstanceManifest m =
                    repo.getResolvedInstanceManifest(new GameInstanceID(versionId)).launchManifest();
            int mv = m.javaVersion() != null ? m.javaVersion().majorVersion() : 0;
            android.util.Log.i("MioGame", "resolve javaMajor: versionId=" + versionId + " declaredJavaVersion="
                    + (m.javaVersion() == null ? "null" : m.javaVersion().toString()) + " mv=" + mv);
            if (mv >= 25) javaMajor = 25;       // 26.x 等需要 Java 25
            else if (mv >= 17) javaMajor = 21;  // 17..24 用 Java 21（jre21 在 Android 上稳定，jre25 在旧设备有 StrToI 崩溃）
            else if (mv >= 9) javaMajor = 17;   // 9..16 用 Java 17
            else if (mv > 0) javaMajor = 8;     // Java 6/8 老版本用 Java 8
            // mv == 0（版本 JSON 无 javaVersion 声明）：老版本按版本号推断
            else {
                String ver = versionId.startsWith("forge-") || versionId.startsWith("neoforge-")
                        ? versionId.substring(versionId.indexOf('-') + 1) : versionId;
                String lower = ver.toLowerCase(java.util.Locale.ROOT);
                // Beta/Alpha 等远古版本（b1.x/a1.x/Beta 1.x）一律 Java 8
                boolean ancient = lower.startsWith("b") || lower.startsWith("a1")
                        || lower.contains("beta") || lower.contains("alpha")
                        || lower.contains("infdev") || lower.contains("pre-classic");
                if (!ancient && compareVersion(ver, "1.17") >= 0) javaMajor = 21;   // 1.17+ 默认 Java 17/21
                else javaMajor = 8;                                                  // 其余老版本用 Java 8
            }
        } catch (Throwable t) {
            android.util.Log.w("MioGame", "resolve javaMajor failed, use 21", t);
        }
        // 按需从 assets 解压对应 JRE（jre8/jre17/jre21/jre25）
        JRE.ensureInstalled(context, javaMajor);
        File jreHome = JRE.getJreHome(context, javaMajor);
        if (jreHome == null) {
            throw new IllegalStateException("JRE " + javaMajor + " 未安装");
        }
        JRE.extractRuntime(context);
        File runtimeDir = JRE.getRuntimeDir(context);
        File lwjglJar = new File(runtimeDir, "lwjgl.jar");
        // zipfs：Android adhoc JRE 的 jdk.zipfs 非根模块不解析，游戏 FileSystems.getFileSystem("jar")
        // 抛 ProviderNotFoundException: Provider "jar" not found。用自定义 zipfs.jar 前置 classpath
        // 提供 jdk.nio.zipfs.ZipFileSystemProvider（ServiceLoader 自动注册）。
        File zipfsJar = new File(runtimeDir, "zipfs.jar");
        File mioLibPatcher = new File(runtimeDir, "MioLibPatcher.jar");
        File mioExitAgent = new File(runtimeDir, "MioExitAgent.jar");

        // cacio 的 Toolkit.loadLibraries 需要 libawt_xawt.so（Pojav stub）。
        // bionic JVM 只从 sun.boot.library.path（jreHome/lib）加载 java.desktop 原生库。
        // JRE 自带 libawt.so/libawt_headless.so/libawt_xawt.so 时直接用；缺失才从 nativeLibraryDir 补齐。
        File awtXawt = new File(jreHome, "lib/libawt_xawt.so");
        if (!awtXawt.isFile()) {
            File awtSrc = new File(context.getApplicationInfo().nativeLibraryDir, "libawt_xawt.so");
            if (awtSrc.isFile()) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(awtSrc);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(awtXawt)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
                android.util.Log.i("MioGame", "copied libawt_xawt.so to jre lib: " + awtXawt.getAbsolutePath());
            } else {
                android.util.Log.w("MioGame", "libawt_xawt.so not found in nativeLibraryDir");
            }
        }

        // Java 25 的 libfontmanager.so 依赖 libc++_shared.so，但 JRE 资产未内置。
        // 从 app nativeLib 补齐，否则 java.awt.Font.initIDs() 报 UnsatisfiedLinkError。
        File cppShared = new File(jreHome, "lib/libc++_shared.so");
        if (!cppShared.isFile()) {
            File cppSrc = new File(context.getApplicationInfo().nativeLibraryDir, "libc++_shared.so");
            if (cppSrc.isFile()) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(cppSrc);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(cppShared)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
                android.util.Log.i("MioGame", "copied libc++_shared.so to jre lib: " + cppShared.getAbsolutePath());
            } else {
                android.util.Log.w("MioGame", "libc++_shared.so not found in nativeLibraryDir");
            }
        }

        // 启动前预写游戏选项：中文语言 + GUI 缩放自适应 + 低视距 + 低画质（按启动配置）
        prepareGameOptions(gameDir, windowW, windowH, config);

        // 清除游戏 jar 的数字签名（META-INF/*.SF/*.RSA/*.DSA）。
        // Fabric/Forge 的类转换会改写 net.minecraft 包内类，与 Mojang 签名冲突，
        // 导致 SecurityException: signer information does not match（class "gzg"/"ezz"）。
        stripJarSignatures(new File(gameDir, "versions/" + versionId + "/" + versionId + ".jar"));

        // 游戏线程优先级提到最高（FCL 同样把游戏线程设 MAX_PRIORITY），让区块加载优先拿 CPU
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        // rawCommand[0] 是 java 可执行文件路径，其余是 JVM + 游戏参数。
        // 关键：JVM 参数必须插在主类之前，否则会被当作游戏参数忽略。
        List<String> src = rawCommand.subList(1, rawCommand.size());
        List<String> extra = new ArrayList<>();
        // authlib-injector：外置登录（LittleSkin 等 Yggdrasil 服务）必须注入，否则
        // 游戏仍请求 Mojang 官方 sessionserver，联机校验失败、皮肤不显示。
        // 判定：游戏参数含 --userType mojang 且 --accessToken 为真实令牌（离线为 "0"/"legacy"）。
        boolean isExternalLogin = false;
        for (int i = 0; i + 1 < src.size(); i++) {
            String a = src.get(i);
            if ("--userType".equals(a) && "mojang".equals(src.get(i + 1))) {
                for (int j = 0; j + 1 < src.size(); j++) {
                    if ("--accessToken".equals(src.get(j)) && !"0".equals(src.get(j + 1))
                            && !src.get(j + 1).isBlank()) {
                        isExternalLogin = true;
                        break;
                    }
                }
                break;
            }
        }
        if (isExternalLogin) {
            File authlib = new File(runtimeDir, "authlib-injector.jar");
            if (authlib.isFile()) {
                extra.add("-javaagent:" + authlib.getAbsolutePath() + "=https://littleskin.cn/api/yggdrasil");
            } else {
                android.util.Log.w("MioGame", "authlib-injector.jar missing, external login won't validate");
            }
        }
        // 离线可玩：authlib/Yggdrasil 访问 Mojang 服务器（api.minecraftservices.com 等）在部分网络连不上，
        // 默认会长时间阻塞导致卡加载界面。这里强制极短超时让请求快速失败，游戏可离线进主菜单。
        extra.add("-Dsun.net.client.defaultConnectTimeout=3000");
        extra.add("-Dsun.net.client.defaultReadTimeout=3000");
        // JVM 崩溃（native SIGSEGV 等）时把 hs_err 写到固定位置（游戏目录），
        // 不依赖进程存活/当前工作目录，保证崩溃后日志可读取。
        extra.add("-XX:ErrorFile=" + new File(gameDir, "hs_err_%p.log").getAbsolutePath());
        extra.add("-Djava.library.path=" + jreHome.getAbsolutePath() + "/lib"
                + ":" + context.getApplicationInfo().nativeLibraryDir);
        extra.add("-Dorg.lwjgl.opengl.libname=" + context.getApplicationInfo().nativeLibraryDir + "/" + renderer.getGlLibName());
        extra.add("-Dorg.lwjgl.freetype.libname=" + context.getApplicationInfo().nativeLibraryDir + "/libfreetype.so");
        extra.add("-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared");
        // FCL 全套 AWT/Cacio 配置（Caciocavallo 纯 Java AWT，替 Android 提供 java.awt）
        // Java 8 老版本（B1.0 等）用 LWJGL2 + java.awt 窗口，需要 Java 8 版 cacio
        // （net.java.openjdk.cacio.ctc.CTCToolkit + -Xbootclasspath/p 前置）。
        // 现代版本（1.17+）：用 LWJGL 渲染不依赖 java.awt，cacio 反而在 bionic 上加载
        // native awt 崩溃（Could not allocate library name / fontmanager 加载失败），故跳过。
        if (javaMajor < 9) {
            // Java 8 版 Caciocavallo（net.java.openjdk 包名）
            File cacio8Dir = new File(runtimeDir, "caciocavallo");
            File cacioAndroidNw = new File(cacio8Dir, "cacio-androidnw-1.10-SNAPSHOT.jar");
            File cacioShared8 = new File(cacio8Dir, "cacio-shared-1.10-SNAPSHOT.jar");
            extra.add("-Djava.awt.headless=false");
            extra.add("-Dcacio.managed.screensize=" + windowW + "x" + windowH);
            extra.add("-Dcacio.font.fontmanager=sun.awt.X11FontManager");
            extra.add("-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler");
            extra.add("-Dswing.defaultlaf=javax.swing.plaf.metal.MetalLookAndFeel");
            extra.add("-Dawt.toolkit=net.java.openjdk.cacio.ctc.CTCToolkit");
            extra.add("-Djava.awt.graphicsenv=net.java.openjdk.cacio.ctc.CTCGraphicsEnvironment");
            if (cacioAndroidNw.isFile() && cacioShared8.isFile()) {
                extra.add("-Xbootclasspath/p:" + cacioAndroidNw.getAbsolutePath()
                        + File.pathSeparator + cacioShared8.getAbsolutePath());
            }
        } else if (javaMajor >= 9 && javaMajor < 17) {
            File cacioDir = new File(runtimeDir, "caciocavallo17");
            File cacioAgent = new File(cacioDir, "cacio-agent.jar");
            extra.add("-Djava.awt.headless=false");
            extra.add("-Dcacio.managed.screensize=" + windowW + "x" + windowH);
            extra.add("-Dcacio.font.fontmanager=sun.awt.X11FontManager");
            extra.add("-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler");
            extra.add("-Dswing.defaultlaf=javax.swing.plaf.nimbus.NimbusLookAndFeel");
            extra.add("-Dawt.toolkit=com.github.caciocavallosilano.cacio.ctc.CTCToolkit");
            extra.add("-Djava.awt.graphicsenv=com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment");
            if (cacioAgent.isFile()) {
                extra.add("-javaagent:" + cacioAgent.getAbsolutePath());
                extra.add("-Xbootclasspath/a:" + cacioAgent.getAbsolutePath()
                        + File.pathSeparator + new File(cacioDir, "cacio-shared-1.19.1-SNAPSHOT.jar").getAbsolutePath()
                        + File.pathSeparator + new File(cacioDir, "cacio-tta-1.19.1-SNAPSHOT.jar").getAbsolutePath());
            }
            // cacio 需要的 JVM 内部类访问（对齐 FCL 的 add-exports/add-opens）
            extra.add("--add-exports=java.desktop/java.awt=ALL-UNNAMED");
            extra.add("--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED");
            extra.add("--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED");
            extra.add("--add-exports=java.desktop/sun.java2d=ALL-UNNAMED");
            extra.add("--add-exports=java.desktop/java.awt.dnd.peer=ALL-UNNAMED");
            extra.add("--add-exports=java.desktop/sun.awt=ALL-UNNAMED");
            extra.add("--add-exports=java.desktop/sun.awt.event=ALL-UNNAMED");
            extra.add("--add-exports=java.desktop/sun.awt.datatransfer=ALL-UNNAMED");
            extra.add("--add-exports=java.desktop/sun.font=ALL-UNNAMED");
            extra.add("--add-exports=java.base/sun.security.action=ALL-UNNAMED");
            extra.add("--add-opens=java.base/java.util=ALL-UNNAMED");
            extra.add("--add-opens=java.desktop/java.awt=ALL-UNNAMED");
            extra.add("--add-opens=java.desktop/sun.font=ALL-UNNAMED");
            extra.add("--add-opens=java.desktop/sun.java2d=ALL-UNNAMED");
            extra.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");
            extra.add("--add-opens=java.base/java.net=ALL-UNNAMED");
        }
        extra.add("-Dglfwstub.windowWidth=" + windowW);
        extra.add("-Dglfwstub.windowHeight=" + windowH);
        extra.add("-Dglfwstub.initEgl=false");
        extra.add("-Duser.home=" + gameDir.getAbsolutePath());
        extra.add("-XX:ActiveProcessorCount=" + Runtime.getRuntime().availableProcessors());
        // ---- 内存/GC 调优：让 JVM 尽早回收、空闲时把堆归还系统，避免 RSS 虚高被 LMK 误杀 ----
        // 注意：此 adhoc JRE 对部分 G1 微调参数兼容性存疑，先只保留基础项验证
        extra.add("-XX:MinHeapFreeRatio=10");
        extra.add("-XX:MaxHeapFreeRatio=40");
        extra.add("-XX:MaxGCPauseMillis=100");
        extra.add("-XX:MaxMetaspaceSize=256m");
        // 并行引用处理：减少 GC 停顿（弱/软引用多的游戏场景尤其有效）
        extra.add("-XX:+ParallelRefProcEnabled");
        // ---- 帧率稳定性：充足的 JIT 代码缓存（避免反复重编译导致卡顿）----
        extra.add("-XX:ReservedCodeCacheSize=256m");
        // CDS：若 JRE 已带共享归档（lib/server/classes.jsa）则启用——类从只读共享区加载，
        // 既省内存/加速启动，也绕开 libjimage 并发读取的竞态路径。无归档时 -Xshare:auto 静默回退。
        File cdsArchive = new File(jreHome, "lib/server/classes.jsa");
        if (cdsArchive.isFile()) {
            extra.add("-Xshare:auto");
            extra.add("-XX:SharedArchiveFile=" + cdsArchive.getAbsolutePath());
        }
        extra.add("-Dorg.lwjgl.vulkan.libname=libvulkan.so");
        extra.add("-Dcpu.name=MT6893");
        extra.add("-Dminecraft.launcher.brand=MioLauncher");
        extra.add("-Dminecraft.launcher.version=1.0");
        // 对齐 FCL：minecraft.client.jar / log4j / sodium / fml
        File gameJar = new File(gameDir, "versions/" + versionId + "/" + versionId + ".jar");
        if (gameJar.isFile()) extra.add("-Dminecraft.client.jar=" + gameJar.getAbsolutePath());
        File log4jXml = new File(gameDir, "versions/" + versionId + "/log4j2.xml");
        if (log4jXml.isFile()) extra.add("-Dlog4j.configurationFile=" + log4jXml.getAbsolutePath());
        extra.add("-Dsodium.checks.issue2561=false");
        extra.add("-Dfml.earlyprogresswindow=false");
        extra.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
         extra.add("-Dfml.ignorePatchDiscrepancies=true");
         // Java 21 (JEP 400) 默认 file.encoding=UTF-8，显式 -Dfile.encoding 反而会
         // 干扰 NativeCoding 的 native 初始化，触发 "platform encoding not initialized"
         // （NetworkInterface.getMacAddr0 → netty DefaultChannelId 崩溃）。故不设 file.encoding，
         // 只显式指定 sun.jnu.encoding/native.encoding。
         extra.add("-Dsun.jnu.encoding=UTF-8");
         extra.add("-Dnative.encoding=UTF-8");
         // 对齐 FCL：java.io.tmpdir（Android 无 /tmp）、os、用户 locale、JNA
         File cacheDir = new File(context.getCacheDir(), "miojvm");
        cacheDir.mkdirs();
        extra.add("-Djava.io.tmpdir=" + cacheDir.getAbsolutePath());
        extra.add("-Dos.name=Linux");
        extra.add("-Dos.version=Android-" + android.os.Build.VERSION.RELEASE);
        extra.add("-Duser.language=zh");
        extra.add("-Duser.country=CN");
        extra.add("-Duser.timezone=Asia/Shanghai");
        // 禁止 oshi 加载 Udev（避免触发 JNA native 加载，部分设备如华为/麒麟上 libjnidispatch.so
        // 加载或版本校验会崩溃，导致游戏启动闪退）。oshi 的 CPU 探测在 Android 上本就不可靠，
        // 由 OshiPatch 跳过命令执行，这里关闭 Udev 依赖即可。
        extra.add("-Doshi.os.linux.allowudev=false");
        // JNA（oshi 依赖）在 Android 上无法加载桌面版原生库（glibc 的 libc.so.6），
        // 这里让 JNA 优先从 APK 内置的 Bionic 版 libjnidispatch.so（backend jniLibs 提供）
        // 加载，使 oshi 的 CPU/内存探测正常，避免部分版本启动崩溃。
        extra.add("-Djna.boot.library.path=" + context.getApplicationInfo().nativeLibraryDir);
        extra.add("-Djna.tmpdir=" + cacheDir.getAbsolutePath());
        extra.add("-Dorg.lwjgl.system.SharedLibraryExtractPath=" + cacheDir.getAbsolutePath());
        extra.add("-Dio.netty.native.workdir=" + cacheDir.getAbsolutePath());
        // 固定 netty machineId：Netty DefaultChannelId 初始化会调 NetworkInterface.getMacAddr0()，
        // 在 Android 的 Java 21 上触发 "platform encoding not initialized"（InternalError）崩溃。
        // 设固定 machineId（16字节hex）跳过 MAC 地址获取，世界加载不再崩溃。
        extra.add("-Dio.netty.machineId=00:0a:95:9d:68:16");
        extra.add("-Dloader.disable_forked_guis=true");
        extra.add("-Djdk.lang.Process.launchMechanism=FORK");
        // Android 无 /etc/resolv.conf，为 JVM 提供 DNS 解析（对齐 FCL -Dext.net.resolvPath）
        File resolv = new File(cacheDir, "resolv.conf");
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(resolv);
            fos.write("nameserver 223.5.5.5\nnameserver 114.114.114.114\n".getBytes());
            fos.close();
            extra.add("-Dext.net.resolvPath=" + resolv.getAbsolutePath());
        } catch (Exception e) {
            android.util.Log.w("MioGame", "write resolv.conf failed", e);
        }
        extra.add("-javaagent:" + mioLibPatcher.getAbsolutePath());
        // OshiPatch：oshi 在 Android 上会执行 lshw 等外部命令探测 CPU，这些命令在
        // libpojavexec 的 forkAndExec hook 下空指针崩溃（SIGSEGV in libjava.so）。
        // 直接 patch oshi.util.ExecutingCommand.runNative 返回空，跳过一切命令执行。
        // 注意：OshiPatch.jar 为 Java 17 字节码（class 61.0），JRE 8（52.0）加载会抛
        // UnsupportedClassVersionError 导致整个 JVM 启动失败，故 Java 8 下跳过。
        File oshiPatch = new File(runtimeDir, "OshiPatch.jar");
        if (oshiPatch.isFile() && javaMajor >= 9) {
            extra.add("-javaagent:" + oshiPatch.getAbsolutePath());
        }
        // OshiFix：oshi 的 LinuxCentralProcessor.readTopologyFromSysfs 会递归遍历
        // /sys/devices/system/cpu/，Android 上 bus_dcvs/DDR/cur_freq 权限拒绝 →
        // UncheckedIOException 导致游戏启动崩溃。此代理把该方法整体包 try-catch 吞掉。
        File oshiFix = new File(runtimeDir, "OshiFix.jar");
        if (oshiFix.isFile() && javaMajor >= 9) {
            extra.add("-javaagent:" + oshiFix.getAbsolutePath());
        }
        // MioLauncher: 干净退出清理 agent——游戏正常退出时删除崩溃标记，避免误报崩溃；
        // 真实崩溃（信号）不跑 shutdown hook，标记保留供启动时检测。
        // Java 8：MioExitAgent 为 Java 17 字节码无法加载，跳过。
        // Java 24+：SecurityManager 已移除，-Djava.security.manager=allow 会直接报错，跳过。
        if (mioExitAgent.isFile() && javaMajor >= 9 && javaMajor < 24) {
            // 允许安装 SecurityManager（MioExitAgent 用它记录 System.exit 退出码以区分干净/异常退出）
            extra.add("-Djava.security.manager=allow");
            extra.add("-javaagent:" + mioExitAgent.getAbsolutePath());
            extra.add("-Dmio.crash.marker=" + new File(context.getFilesDir(), "mio/game/.mio_crash_marker").getAbsolutePath());
        }
        // 陶瓦联机：游戏主菜单"陶瓦联机"按钮点击后写此标记文件，启动器据此切到联机页
        extra.add("-Dmio.terracotta.marker=" + new File(context.getFilesDir(), "mio/terracotta_switch").getAbsolutePath());
        // 调试：DumpAgent 在 JVM 内轮询标记文件 dump 全部线程栈
        File dumpAgent = new File(runtimeDir, "DumpAgent.jar");
        if (dumpAgent.isFile()) {
            extra.add("-javaagent:" + dumpAgent.getAbsolutePath());
        }
        // 用户附加 JVM 参数
        if (config.extraJvmArgs != null && !config.extraJvmArgs.trim().isEmpty()) {
            for (String arg : config.extraJvmArgs.trim().split("\\s+")) {
                if (!arg.isEmpty()) extra.add(arg);
            }
        }

        // 找到主类位置：HMCL raw command 格式为 [...-jvm args..., -cp, <classpath>, <MainClass>, ...game args...]
        // 主类总是紧跟在 -cp <classpath> 之后，不依赖包名前缀
        List<String> args = new ArrayList<>();
        int mainIdx = src.size();
        for (int i = 0; i < src.size(); i++) {
            if ("-cp".equals(src.get(i)) && i + 2 < src.size()) {
                mainIdx = i + 2;
                break;
            }
        }
        for (int i = 0; i < mainIdx; i++) {
            String arg = src.get(i);
            // 过滤 JRE 不支持的参数：--enable-native-access/--sun-misc-unsafe-memory-access 是
            // Java 24+ 参数，仅 JRE 21 及以下无法识别。Java 25（26.x 版本）需要保留。
            if ((arg.startsWith("--sun-misc-unsafe-memory-access")
                    || arg.startsWith("--enable-native-access"))
                    && javaMajor < 25) {
                android.util.Log.i("MioGame", "filter JRE" + javaMajor + "-unsupported arg: " + arg);
                continue;
            }
            // 替换 HMCL 生成的无效 -Djava.library.path
            if (arg.startsWith("-Djava.library.path=")) {
                args.add(extra.get(0));
            } else if ("-cp".equals(arg) && i + 1 < src.size()) {
                // 用 FCL 的 lwjgl.jar 前置 classpath，覆盖游戏自带 LWJGL。
                if (javaMajor >= 9) {
                    args.add(arg);
                    String pre = lwjglJar.getAbsolutePath();
                    if (zipfsJar.isFile()) {
                        pre = pre + java.io.File.pathSeparator + zipfsJar.getAbsolutePath();
                    }
                    args.add(pre + java.io.File.pathSeparator + dedupeClasspath(src.get(i + 1)));
                } else {
                    // Java 8 老版本（如 Beta 1.0）用 LWJGL 2。保持原 classpath（含游戏自带 LWJGL2），
                    // 末尾追加 FCL 的 lwjglx.jar（LWJGL2→LWJGL3 桥接），使老版本能跑在 Android 的 LWJGL3 上。
                    String cp = dedupeClasspath(src.get(i + 1));
                    File lwjglx = new File(runtimeDir, "lwjglx.jar");
                    if (lwjglx.isFile()) {
                        cp = cp + java.io.File.pathSeparator + lwjglx.getAbsolutePath();
                    }
                    args.add(arg);
                    args.add(cp);
                }
                i++;
            } else {
                args.add(arg);
            }
        }
        args.addAll(extra);
        for (int i = mainIdx; i < src.size(); i++) {
            args.add(src.get(i));
        }

        return JRE.launch(context, args, gameDir, renderer, config.vsync, javaMajor);
    }

    /**
     * classpath 去重：同一 group:artifact 出现多个版本时只保留版本号最高的一个。
     * 原因：Fabric Loader 的 Knot 会校验 classpath 不允许重复类（如 asm 9.6 与 9.10.1 并存直接拒绝启动）。
     */
    private static String dedupeClasspath(String classpath) {
        if (classpath == null || classpath.isEmpty()) return classpath;
        String sep = java.util.regex.Pattern.quote(java.io.File.pathSeparator);
        String[] parts = classpath.split(sep);
        Map<String, String> bestPath = new HashMap<>();   // key = group:artifact -> jar 路径
        Map<String, String> bestVer = new HashMap<>();     // key = group:artifact -> 当前最高版本
        Map<String, Integer> orderIdx = new HashMap<>();   // key = group:artifact -> 在结果中的下标
        List<String> result = new ArrayList<>();
        for (String p : parts) {
            String coords = mavenCoords(p);
            if (coords == null) { result.add(p); continue; }
            int cut = coords.lastIndexOf(':');
            String ga = coords.substring(0, cut);
            String ver = coords.substring(cut + 1);
            if (!bestPath.containsKey(ga)) {
                bestPath.put(ga, p);
                bestVer.put(ga, ver);
                orderIdx.put(ga, result.size());
                result.add(p);
            } else if (compareVersion(ver, bestVer.get(ga)) > 0) {
                bestVer.put(ga, ver);
                result.set(orderIdx.get(ga), p);
            }
        }
        return String.join(java.io.File.pathSeparator, result);
    }

    /** 从 maven 仓库路径提取 group:artifact:version，非标准路径返回 null。 */
    private static String mavenCoords(String path) {
        int idx = path.indexOf("/libraries/");
        if (idx < 0) return null;
        String tail = path.substring(idx + "/libraries/".length());
        String[] seg = tail.split("/");
        // 标准布局: <group...>/<artifact>/<version>/<artifact>-<version>[(-<classifier>)].jar
        if (seg.length < 3) return null;
        String fileName = seg[seg.length - 1];
        String version = seg[seg.length - 2];
        String artifact = seg[seg.length - 3];
        if (!fileName.startsWith(artifact + "-")) return null;
        StringBuilder group = new StringBuilder();
        for (int i = 0; i < seg.length - 3; i++) {
            if (i > 0) group.append('.');
            group.append(seg[i]);
        }
        return group + ":" + artifact + ":" + version;
    }

    /** 版本号比较：数字段按数值，其余按字符串。 */
    private static int compareVersion(String a, String b) {
        String[] as = a.split("[\\.\\-]");
        String[] bs = b.split("[\\.\\-]");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            String x = i < as.length ? as[i] : "";
            String y = i < bs.length ? bs[i] : "";
            if (x.equals(y)) continue;
            Integer xi = parseIntOrNull(x), yi = parseIntOrNull(y);
            if (xi != null && yi != null) {
                if (!xi.equals(yi)) return xi.compareTo(yi);
            } else {
                int c = x.compareTo(y);
                if (c != 0) return c;
            }
        }
        return 0;
    }

    private static Integer parseIntOrNull(String s) {
        try { return Integer.valueOf(s); } catch (Exception e) { return null; }
    }

    /**
     * 从启动命令行解析版本 ID。
     * 优先 "--version <id>"；其次从 classpath 的 versions/<id>/<id>.jar 路径提取；
     * 再其次从 -Dminecraft.client.jar=<...>versions/<id>/<id>.jar 提取。
     */
    private static String findVersionId(List<String> rawCommand) {
        for (int i = 0; i < rawCommand.size() - 1; i++) {
            if ("--version".equals(rawCommand.get(i))) {
                return rawCommand.get(i + 1);
            }
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("versions/([^/]+)/\\1\\.jar").matcher(String.join(" ", rawCommand));
        if (m.find()) {
            return m.group(1);
        }
        return "1.21.11";
    }

    /**
     * 清除游戏 jar 里的数字签名文件（META-INF/*.SF / *.RSA / *.DSA）。
     * Fabric/Forge 的类转换器会改写 net.minecraft 包内类，与 Mojang 签名不一致时，
     * JVM 报 SecurityException: signer information does not match（如 class "gzg"/"ezz"）。
     * 删除签名后加载器可正常转换类；对原版无害（不校验签名）。
     * 重写 jar 前先备份，成功才替换，避免损坏。
     */
    private static void stripJarSignatures(File jar) {
        if (jar == null || !jar.isFile()) return;
        java.util.Set<String> sigNames = new java.util.LinkedHashSet<>();
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jar)) {
            var en = zf.entries();
            while (en.hasMoreElements()) {
                String name = en.nextElement().getName();
                if (name.startsWith("META-INF/")) {
                    String upper = name.toUpperCase(java.util.Locale.ROOT);
                    if (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA")) {
                        sigNames.add(name);
                    }
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("MioGame", "stripJarSignatures: 读取失败 " + jar, t);
            return;
        }
        if (sigNames.isEmpty()) return;

        try {
            File tmp = new File(jar.getParentFile(), jar.getName() + ".nosig");
            try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(new java.io.FileInputStream(jar));
                 java.util.zip.ZipOutputStream zout = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(tmp))) {
                byte[] buf = new byte[65536];
                java.util.zip.ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    if (sigNames.contains(entry.getName())) continue;
                    zout.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                    int n;
                    while ((n = zin.read(buf)) != -1) zout.write(buf, 0, n);
                    zout.closeEntry();
                }
            }
            // 校验重写后的 zip 可读，再原子替换
            try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(tmp)) {
                zf.size();
            }
            java.io.File bak = new File(jar.getParentFile(), jar.getName() + ".bak");
            if (bak.exists()) bak.delete();
            if (!jar.renameTo(bak)) {
                tmp.delete();
                return;
            }
            if (!tmp.renameTo(jar)) {
                bak.renameTo(jar);  // 回滚
                return;
            }
            bak.delete();
            android.util.Log.i("MioGame", "已清除 " + sigNames.size() + " 个签名文件: " + jar);
        } catch (Throwable t) {
            android.util.Log.w("MioGame", "stripJarSignatures: 处理失败 " + jar, t);
        }
    }

    /**
     * 预写游戏 options.txt：语言 / GUI 缩放 / 距离 / 帧率 / FOV / 分辨率 / 粒子（按启动配置）。
     * 保留已有选项，仅在缺失或不同时调整目标键。
     */
    private static void prepareGameOptions(File gameDir, int windowW, int windowH, LaunchConfig cfg) throws Exception {
        File options = new File(gameDir, "options.txt");
        List<String> lines = new ArrayList<>();
        if (options.exists()) {
            try (java.io.BufferedReader reader =
                         new java.io.BufferedReader(new java.io.FileReader(options))) {
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
            }
        }
        // 窗口尺寸 windowW/windowH 传入的已是「分辨率缩放」后的实际渲染尺寸
        //（PREF_SCALE_FACTOR 已在 GameActivity 应用），override 直接用它，避免二次缩放。
        int rw = windowW;
        int rh = windowH;
        int maxFps = cfg.maxFps <= 0 ? 100000 : cfg.maxFps;
        // 模拟距离不能超过渲染距离：否则超出视野的实体仍被 tick，白耗 CPU 导致掉帧。
        // 注意 MC 1.18+ simulationDistance 最小值为 5，压到 4 会被游戏拒绝（"Value 4 outside of range [5:33]"）。
        int simDist = Math.max(5, Math.min(cfg.simulationDistance, Math.max(5, cfg.renderDistance)));
        // FOV 合法范围（MC 1.21 为 30..110）：游戏内写入异常值（如 2870）会导致解析错乱，
        // 这里统一钳制到合法区间，防御 options.txt 被污染。
        int fovClamped = Math.max(30, Math.min(cfg.fov, 110));
        String[][] targets = {
                {"lang:", "lang:" + (cfg.lang == null || cfg.lang.isEmpty() ? "zh_cn" : cfg.lang)},
                {"guiScale:", "guiScale:" + cfg.guiScale},
                {"renderDistance:", "renderDistance:" + cfg.renderDistance},
                {"simulationDistance:", "simulationDistance:" + simDist},
                {"maxFps:", "maxFps:" + maxFps},
                {"overrideWidth:", "overrideWidth:" + rw},
                {"overrideHeight:", "overrideHeight:" + rh},
                {"fov:", "fov:" + fovClamped},
                {"ao:", "ao:false"},
                {"mipmapLevels:", "mipmapLevels:0"},
                {"particles:", "particles:" + cfg.particles},
                {"renderClouds:", "renderClouds:\"false\""},
                {"entityDistanceScaling:", "entityDistanceScaling:0.5"},
                {"biomeBlendRadius:", "biomeBlendRadius:0"},
        };
        List<String> out = new ArrayList<>();
        for (String[] t : targets) {
            boolean found = false;
            for (String line : lines) {
                if (line.startsWith(t[0])) {
                    if (!found) {
                        out.add(t[1]);
                        found = true;
                    }
                } else {
                    out.add(line);
                }
            }
            if (!found) out.add(t[1]);
            lines = out;
            out = new ArrayList<>();
        }
        options.getParentFile().mkdirs();
        try (java.io.FileWriter writer = new java.io.FileWriter(options)) {
            for (String line : lines) writer.write(line + "\n");
        }
    }
}
