/*
 * MioLauncher multi-mirror download provider.
 *
 * 聚合多个 BMCLAPI 镜像 + Mojang 官方源，为每个文件同时提供多个候选地址，
 * 任一镜像不可用时自动回退到其他镜像，提高国内下载成功率。
 */
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/// 多镜像下载源：为同一个文件提供多个镜像候选，提升下载成功率。
///
/// @author MioLauncher
public final class MultiMirrorDownloadProvider implements DownloadProvider {

    private final List<DownloadProvider> mirrors = new ArrayList<>();
    private final DownloadProvider mojang;

    public MultiMirrorDownloadProvider(String... apiRoots) {
        this.mojang = new MojangDownloadProvider();
        for (String root : apiRoots) {
            if (root != null && !root.isBlank()) {
                mirrors.add(new BMCLAPIDownloadProvider(root));
            }
        }
        if (mirrors.isEmpty()) {
            mirrors.add(new BMCLAPIDownloadProvider("https://bmclapi2.bangbang93.com"));
        }
    }

    @Override
    public List<URI> getVersionListURLs() {
        LinkedHashSet<URI> urls = new LinkedHashSet<>();
        urls.addAll(mojang.getVersionListURLs());
        for (DownloadProvider mirror : mirrors) {
            urls.addAll(mirror.getVersionListURLs());
        }
        return List.copyOf(urls);
    }

    @Override
    public List<URI> getAssetObjectCandidates(String assetObjectLocation) {
        LinkedHashSet<URI> urls = new LinkedHashSet<>();
        for (DownloadProvider mirror : mirrors) {
            urls.addAll(mirror.getAssetObjectCandidates(assetObjectLocation));
        }
        return List.copyOf(urls);
    }

    @Override
    public String injectURL(String baseURL) {
        // 主镜像为首选
        return mirrors.get(0).injectURL(baseURL);
    }

    @Override
    public List<URI> injectURLWithCandidates(String baseURL) {
        LinkedHashSet<URI> candidates = new LinkedHashSet<>();
        // 原始 Mojang URL 作为最后兜底
        candidates.addAll(mojang.injectURLWithCandidates(baseURL));
        for (DownloadProvider mirror : mirrors) {
            candidates.addAll(mirror.injectURLWithCandidates(baseURL));
        }
        return List.copyOf(candidates);
    }

    @Override
    public List<URI> injectURLsWithCandidates(List<String> urls) {
        LinkedHashSet<URI> result = new LinkedHashSet<>();
        for (String url : urls) {
            result.addAll(injectURLWithCandidates(url));
        }
        return List.copyOf(result);
    }

    @Override
    public VersionList<?> getVersionListById(String id) {
        // 依次尝试各镜像，首个能提供该列表的为准
        VersionList<?> mojangList = mojang.getVersionListById(id);
        List<VersionList<?>> lists = new ArrayList<>();
        lists.add(mojangList);
        for (DownloadProvider mirror : mirrors) {
            lists.add(mirror.getVersionListById(id));
        }
        return new MultipleSourceVersionList(lists.toArray(new VersionList<?>[0]));
    }

    @Override
    public int getConcurrency() {
        return Math.max(Runtime.getRuntime().availableProcessors() * 2, 6);
    }

    @Override
    public String toString() {
        return String.format("MultiMirrorDownloadProvider[%s]", mirrors);
    }
}
