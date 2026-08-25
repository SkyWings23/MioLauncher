/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.util.io.AndroidFiles;
import org.jackhuang.hmcl.download.ArtifactMalformedException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallProfile.Processor;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.download.game.GameInstanceJsonDownloadTask;
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.DownloadInfo;
import org.jackhuang.hmcl.game.DownloadType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.ZlibUtils;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.AndroidFiles;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.gson.JsonUtils.fromNonNullJson;

public class ForgeNewInstallTask extends Task<GameInstancePatch> {

    private class ProcessorTask extends Task<Void> {

        private Processor processor;
        private Map<String, String> vars;

        public ProcessorTask(@NotNull Processor processor, @NotNull Map<String, String> vars) {
            this.processor = processor;
            this.vars = vars;
            setSignificance(TaskSignificance.MODERATE);
        }

        @Override
        public void execute() throws Exception {
            Map<String, String> outputs = new HashMap<>();
            boolean miss = false;

            for (Map.Entry<String, String> entry : processor.getOutputs().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                key = parseLiteral(key, vars);
                value = parseLiteral(value, vars);

                if (key == null || value == null) {
                    throw new ArtifactMalformedException("Invalid forge installation configuration");
                }

                outputs.put(key, value);

                Path artifact = Paths.get(key);
                if (Files.exists(artifact)) {
                    String code;
                    try (InputStream stream = Files.newInputStream(artifact)) {
                        code = (DigestUtils.digestToString("SHA-1", stream));
                    }

                    if (!Objects.equals(code, value)) {
                        Files.delete(artifact);
                        LOG.info("Found existing file is not valid: " + artifact);

                        miss = true;
                    }
                } else {
                    miss = true;
                }
            }

            if (!processor.getOutputs().isEmpty() && !miss) {
                return;
            }

            Path jar = gameRepository.getArtifactFile(manifest, processor.getJar());
            if (!Files.isRegularFile(jar))
                throw new FileNotFoundException("Game processor file not found, should be downloaded in preprocess");

            String mainClass;
            try (JarFile jarFile = new JarFile(jar.toFile())) {
                mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            }

            if (StringUtils.isBlank(mainClass))
                throw new Exception("Game processor jar does not have main class " + jar);

            List<String> command = new ArrayList<>();
            // Android 上优先使用启动器内置 JRE 的 java（mio.forge.java 由 MioRepository 在安装前设置），
            // 否则 JavaRuntime.getDefault() 在 app 进程（ART）里返回无效路径导致 exec 崩溃。
            String forgeJava = System.getProperty("mio.forge.java");
            if (forgeJava != null && !forgeJava.isBlank()) {
                command.add(forgeJava);
            } else {
                command.add(JavaRuntime.getDefault().getBinary().toString());
            }
            command.add("-cp");

            List<String> classpath = new ArrayList<>(processor.getClasspath().size() + 1);
            for (Artifact artifact : processor.getClasspath()) {
                Path file = gameRepository.getArtifactFile(manifest, artifact);
                if (!Files.isRegularFile(file))
                    throw new Exception("Game processor dependency missing");
                classpath.add(file.toString());
            }
            classpath.add(jar.toString());
            command.add(String.join(File.pathSeparator, classpath));

            command.add(mainClass);

            List<String> args = new ArrayList<>(processor.getArgs().size());
            for (String arg : processor.getArgs()) {
                String parsed = parseLiteral(arg, vars);
                if (parsed == null)
                    throw new ArtifactMalformedException("Invalid forge installation configuration");
                args.add(parsed);
            }

            command.addAll(args);

            // Android：SELinux 禁止 exec app 数据目录下的 java 二进制（error=13 Permission denied），
            // 改用进程内 JLI_Launch 拉起 JRE 运行处理器，规避 exec 限制。
            if ("true".equals(System.getProperty("mio.android"))) {
                LOG.info("Executing processor in-process (Android JLI_Launch): " + mainClass);
                // command = [java, -cp, <cp>, mainClass, args...]；去掉 java 本身传 JLI_Launch
                List<String> jliArgs = new ArrayList<>(command.subList(1, command.size()));
                int code = runProcessorInProcess(jliArgs);
                if (code != 0)
                    throw new IOException("Game processor exited abnormally with code " + code);
            } else {
                LOG.info("Executing external processor " + processor.getJar().toString() + ", command line: " + new CommandBuilder().addAll(command).toString());
                // Android 上运行内置 JRE 的 java 需要 LD_LIBRARY_PATH 指向 JRE 的 lib（否则 libjli.so 找不到）
                ProcessBuilder pb = new ProcessBuilder(command);
                String forgeJavaHome = System.getProperty("mio.forge.java.home");
                if (forgeJavaHome != null && !forgeJavaHome.isBlank()) {
                    String existing = pb.environment().get("LD_LIBRARY_PATH");
                    String jreLib = new java.io.File(forgeJavaHome, "lib").getAbsolutePath();
                    String jreLibServer = new java.io.File(forgeJavaHome, "lib/server").getAbsolutePath();
                    pb.environment().put("LD_LIBRARY_PATH",
                            jreLibServer + ":" + jreLib + (existing == null ? "" : ":" + existing));
                }
                int exitCode = SystemUtils.callExternalProcess(pb);
                if (exitCode != 0)
                    throw new IOException("Game processor exited abnormally with code " + exitCode);
            }

            for (Map.Entry<String, String> entry : outputs.entrySet()) {
                Path artifact = Paths.get(entry.getKey());
                if (!Files.isRegularFile(artifact))
                    throw new FileNotFoundException("File missing: " + artifact);

                String code;
                try (InputStream stream = Files.newInputStream(artifact)) {
                    code = DigestUtils.digestToString("SHA-1", stream);
                }

                if (!Objects.equals(code, entry.getValue())) {
                    if (!ZlibUtils.IS_ZLIB_COMPATIBLE && FileUtils.getExtension(artifact).equals("jar")) {
                        // Forge/NeoForge generates JARs dynamically during installation.
                        // When native compression libraries such as zlib-ng are in use,
                        // the resulting JAR may be compressed differently, causing its
                        // SHA-1 hash to differ from the expected value recorded in the
                        // install profile. In this case, fall back to verifying that the
                        // file is at least a structurally valid ZIP/JAR archive.
                        try {
                            FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER.checkIntegrity(artifact, artifact);
                            LOG.info("Ignoring SHA-1 mismatch for " + artifact + " due to non-standard zlib compression output");
                            continue;
                        } catch (Exception ignored) {
                        }
                    }


                    Files.delete(artifact);
                    throw new ChecksumMismatchException("SHA-1", entry.getValue(), code);
                }
            }
        }
    }

    /**
     * 在 Android 上进程内运行 Forge/NeoForge 处理器。
     *
     * <p>SELinux 禁止 exec app 数据目录下的 java 二进制，这里通过反射调用
     * 启动器 app 的 JRE.launchJava（进程内 JLI_Launch），用真实 JRE 运行处理器。</p>
     *
     * @param jliArgs java 命令行参数（不含 java 本身），如 {"-cp", "...", "mainClass", "args"}
     * @return JLI_Launch 返回码（0 表示成功）
     */
    private static int runProcessorInProcess(List<String> jliArgs) throws Exception {
        try {
            Class<?> appClass = Class.forName("com.miolauncher.app.MioApplication");
            java.lang.reflect.Method getContext = appClass.getMethod("getContext");
            Object context = getContext.invoke(null);

            Class<?> jreClass = Class.forName("com.miolauncher.backend.JRE");
            java.lang.reflect.Method launchJava = null;
            for (java.lang.reflect.Method m : jreClass.getMethods()) {
                if ("launchJava".equals(m.getName()) && m.getParameterTypes().length == 2) {
                    launchJava = m;
                    break;
                }
            }
            if (launchJava == null)
                throw new NoSuchMethodException("JRE.launchJava not found");
            Object code = launchJava.invoke(null, context, jliArgs);
            return code instanceof Number ? ((Number) code).intValue() : -1;
        } catch (ClassNotFoundException e) {
            // 非 MioLauncher 环境（如 HMCL 桌面版）：回退到外部进程
            LOG.warning("In-process processor launcher unavailable, falling back to external process", e);
            ProcessBuilder pb = new ProcessBuilder(jliArgs);
            return SystemUtils.callExternalProcess(pb);
        }
    }

    private final DefaultDependencyManager dependencyManager;
    private final DefaultGameRepository gameRepository;
    private final GameInstanceManifest manifest;
    private final Path installer;
    private final List<Task<?>> dependents = new ArrayList<>(1);
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    private ForgeNewInstallProfile profile;
    private List<Processor> processors;
    private GameInstanceManifest forgeVersion;
    private final String selfVersion;

    private Path tempDir;
    private AtomicInteger processorDoneCount = new AtomicInteger(0);

    public ForgeNewInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest manifest, String selfVersion, Path installer) {
        this.dependencyManager = dependencyManager;
        this.gameRepository = dependencyManager.getGameRepository();
        this.manifest = manifest;
        this.installer = installer;
        this.selfVersion = selfVersion;

        setSignificance(TaskSignificance.MAJOR);
    }

    private static String replaceTokens(Map<String, String> tokens, String value) {
        StringBuilder buf = new StringBuilder();
        for (int x = 0; x < value.length(); x++) {
            char c = value.charAt(x);
            if (c == '\\') {
                if (x == value.length() - 1)
                    throw new IllegalArgumentException("Illegal pattern (Bad escape): " + value);
                buf.append(value.charAt(++x));
            } else if (c == '{' || c == '\'') {
                StringBuilder key = new StringBuilder();
                for (int y = x + 1; y <= value.length(); y++) {
                    if (y == value.length())
                        throw new IllegalArgumentException("Illegal pattern (Unclosed " + c + "): " + value);
                    char d = value.charAt(y);
                    if (d == '\\') {
                        if (y == value.length() - 1)
                            throw new IllegalArgumentException("Illegal pattern (Bad escape): " + value);
                        key.append(value.charAt(++y));
                    } else {
                        if (c == '{' && d == '}') {
                            x = y;
                            break;
                        }
                        if (c == '\'' && d == '\'') {
                            x = y;
                            break;
                        }
                        key.append(d);
                    }
                }
                if (c == '\'') {
                    buf.append(key);
                } else {
                    if (!tokens.containsKey(key.toString()))
                        throw new IllegalArgumentException("Illegal pattern: " + value + " Missing Key: " + key);
                    buf.append(tokens.get(key.toString()));
                }
            } else {
                buf.append(c);
            }
        }
        return buf.toString();
    }

    private <E extends Exception> String parseLiteral(String literal, Map<String, String> var, ExceptionalFunction<String, String, E> plainConverter) throws E {
        if (StringUtils.isSurrounded(literal, "{", "}"))
            return var.get(StringUtils.removeSurrounding(literal, "{", "}"));
        else if (StringUtils.isSurrounded(literal, "'", "'"))
            return StringUtils.removeSurrounding(literal, "'");
        else if (StringUtils.isSurrounded(literal, "[", "]"))
            return gameRepository.getArtifactFile(manifest, Artifact.fromDescriptor(StringUtils.removeSurrounding(literal, "[", "]"))).toString();
        else
            return plainConverter.apply(replaceTokens(var, literal));
    }

    private String parseLiteral(String literal, Map<String, String> var) {
        return parseLiteral(literal, var, ExceptionalFunction.identity());
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        // Android app 进程没有 jdk.zipfs，用标准 ZipFile 读取 installer 内容
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(installer.toFile())) {
            profile = JsonUtils.fromNonNullJson(
                    readEntryText(zf, "install_profile.json"),
                    ForgeNewInstallProfile.class);
            processors = profile.getProcessors();
            forgeVersion = JsonUtils.fromNonNullJson(
                    readEntryText(zf, profile.getJson()),
                    GameInstanceManifest.class);

            for (Library library : profile.getLibraries()) {
                java.util.zip.ZipEntry entry = zf.getEntry("maven/" + library.getPath());
                if (entry != null) {
                    Path dest = gameRepository.getLibraryFile(manifest, library);
                    copyEntry(zf, entry, dest);
                }
            }

            if (profile.getPath().isPresent()) {
                java.util.zip.ZipEntry mainEntry = zf.getEntry("maven/" + profile.getPath().get().getPath());
                if (mainEntry != null) {
                    Path dest = gameRepository.getArtifactFile(manifest, profile.getPath().get());
                    copyEntry(zf, mainEntry, dest);
                }
            }
        } catch (java.util.zip.ZipException ex) {
            throw new ArtifactMalformedException("Malformed forge installer file", ex);
        }

        dependents.add(new GameLibrariesTask(dependencyManager, manifest, true, profile.getLibraries()));
    }

    private static String readEntryText(java.util.zip.ZipFile zf, String name) throws IOException {
        // zip entry 名无前导斜杠；zipfs 的 Path 会规范化，ZipFile 需要手动去掉
        String entryName = name != null && name.startsWith("/") ? name.substring(1) : name;
        java.util.zip.ZipEntry entry = zf.getEntry(entryName);
        if (entry == null) throw new IOException("Entry not found in Forge installer: " + name);
        try (java.io.InputStream is = zf.getInputStream(entry)) {
            return new String(org.jackhuang.hmcl.util.io.IOUtils.readFully(is), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static void copyEntry(java.util.zip.ZipFile zf, java.util.zip.ZipEntry entry, Path dest) throws IOException {
        if (entry == null) throw new IOException("Entry not found in Forge installer");
        Files.createDirectories(dest.getParent());
        try (java.io.InputStream is = zf.getInputStream(entry)) {
            Files.copy(is, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static java.util.zip.ZipEntry findEntry(java.util.zip.ZipFile zf, String name) {
        if (name == null) return null;
        String n = name.startsWith("/") ? name.substring(1) : name;
        return zf.getEntry(n);
    }

    private Map<String, String> parseOptions(List<String> args, Map<String, String> vars) {
        Map<String, String> options = new LinkedHashMap<>();
        String optionName = null;
        for (String arg : args) {
            if (arg.startsWith("--")) {
                if (optionName != null) {
                    options.put(optionName, "");
                }
                optionName = arg.substring(2);
            } else {
                if (optionName == null) {
                    // ignore
                } else {
                    options.put(optionName, parseLiteral(arg, vars));
                    optionName = null;
                }
            }
        }
        if (optionName != null) {
            options.put(optionName, "");
        }
        return options;
    }

    private Task<?> patchDownloadMojangMappingsTask(Processor processor, Map<String, String> vars) {
        Map<String, String> options = parseOptions(processor.getArgs(), vars);
        if (!"DOWNLOAD_MOJMAPS".equals(options.get("task")) || !"client".equals(options.get("side")))
            return null;
        String version = options.get("version");
        String output = options.get("output");
        if (version == null || output == null)
            return null;

        LOG.info("Patching DOWNLOAD_MOJMAPS task");
        return new GameInstanceJsonDownloadTask(version, dependencyManager)
                .thenComposeAsync(json -> {
                    DownloadInfo mappings = fromNonNullJson(json, GameInstanceManifest.class)
                            .getDownloads().get(DownloadType.CLIENT_MAPPINGS);
                    if (mappings == null) {
                        throw new Exception("client_mappings download info not found");
                    }

                    List<URI> mappingsUrl = dependencyManager.getDownloadProvider()
                            .injectURLWithCandidates(mappings.getUrl());
                    var mappingsTask = new FileDownloadTask(
                            mappingsUrl,
                            Paths.get(output),
                            FileDownloadTask.IntegrityCheck.of("SHA-1", mappings.getSha1()));
                    mappingsTask.setCaching(true);
                    mappingsTask.setCacheRepository(dependencyManager.getCacheRepository());
                    return mappingsTask;
                });
    }

    private Task<?> createProcessorTask(Processor processor, Map<String, String> vars) {
        Task<?> task = patchDownloadMojangMappingsTask(processor, vars);
        if (task == null) {
            task = new ProcessorTask(processor, vars);
        }
        task.onDone().register(
                () -> updateProgress(processorDoneCount.incrementAndGet(), processors.size()));
        return task;
    }

    @Override
    public void execute() throws Exception {
        tempDir = Files.createTempDirectory("forge_installer");

        Map<String, String> vars = new HashMap<>();

        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(installer.toFile())) {
            for (Map.Entry<String, String> entry : profile.getData().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                vars.put(key, parseLiteral(value,
                        Collections.emptyMap(),
                        str -> {
                            Path dest = Files.createTempFile(tempDir, null, null);
                            copyEntry(zf, findEntry(zf, str), dest);
                            return dest.toString();
                        }));
            }
        } catch (java.util.zip.ZipException ex) {
            throw new ArtifactMalformedException("Malformed forge installer file", ex);
        }

        vars.put("SIDE", "client");
        vars.put("MINECRAFT_JAR", FileUtils.getAbsolutePath(gameRepository.getInstanceJar(manifest)));
        vars.put("MINECRAFT_VERSION", FileUtils.getAbsolutePath(gameRepository.getInstanceJar(manifest)));
        vars.put("ROOT", FileUtils.getAbsolutePath(gameRepository.getBaseDirectory()));
        vars.put("INSTALLER", installer.toAbsolutePath().toString());
        vars.put("LIBRARY_DIR", FileUtils.getAbsolutePath(gameRepository.getLibrariesDirectory(manifest)));

        updateProgress(0, processors.size());

        Task<?> processorsTask = Task.runSequentially(
                processors.stream()
                        .map(processor -> createProcessorTask(processor, vars))
                        .toArray(Task<?>[]::new));

        dependencies.add(
                processorsTask.thenComposeAsync(
                        dependencyManager.checkLibraryCompletionAsync(forgeVersion, true)));

        setResult(GameInstancePatch.fromManifest(
                forgeVersion,
                LibraryAnalyzer.LibraryType.FORGE.getPatchId(),
                selfVersion,
                GameInstancePatch.PRIORITY_LOADER));
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        FileUtils.deleteDirectory(tempDir);
    }
}
