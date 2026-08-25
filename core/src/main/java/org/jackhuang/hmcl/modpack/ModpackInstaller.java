/*
 * MioLauncher 整合包安装器。
 * 封装 Modrinth 整合包（.mrpack）的下载与安装：读清单 → 创建实例 → 装依赖。
 */
package org.jackhuang.hmcl.modpack;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.modpack.modrinth.ModrinthModpackProvider;
import org.jackhuang.hmcl.task.Task;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/// 从 URL 下载 Modrinth 整合包并安装到指定实例。
public final class ModpackInstaller {

    private ModpackInstaller() {
    }

    /// 下载 mrpack 到本地临时文件。
    public static Path downloadModpack(String url, Path target) throws IOException {
        var lastErr = (IOException) null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "MioLauncher/0.1.0");
                try {
                    int code = conn.getResponseCode();
                    if (code < 200 || code > 299) {
                        throw new IOException("HTTP " + code);
                    }
                    try (InputStream in = conn.getInputStream()) {
                        Files.copy(in, target);
                    }
                    return target;
                } finally {
                    conn.disconnect();
                }
            } catch (IOException e) {
                lastErr = e;
                try { Files.deleteIfExists(target); } catch (IOException ignored) {
                }
            }
        }
        throw lastErr != null ? lastErr : new IOException("下载失败");
    }

    /// 读取 Modrinth 整合包清单并返回其安装 Task（未执行）。
    public static Modpack readModpackManifest(Path zipFile) throws IOException {
        kala.compress.archivers.zip.ZipArchiveReader reader =
                new kala.compress.archivers.zip.ZipArchiveReader(zipFile, StandardCharsets.UTF_8);
        return ModrinthModpackProvider.INSTANCE.readManifest(reader, zipFile, StandardCharsets.UTF_8);
    }

    /// 创建并返回整合包安装 Task（执行后创建实例 + 下载依赖模组 + 版本）。
    public static Task<?> createInstallTask(DefaultDependencyManager dependencyManager,
                                            Path zipFile, Modpack modpack,
                                            GameInstanceID instanceId) {
        return modpack.getInstallTask(dependencyManager, zipFile, instanceId, null);
    }
}
