package com.miolauncher.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miolauncher.app.MioApplication
import com.miolauncher.app.data.DownloadManager
import com.miolauncher.app.data.GameVersion
import com.miolauncher.app.data.InstallProgress
import com.miolauncher.app.data.McLoader
import com.miolauncher.app.data.MioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VersionListViewModel : ViewModel() {

    private val repository = MioRepository(requireNotNull(MioApplication.appContext))

    private val _versions = MutableStateFlow<List<GameVersion>>(emptyList())
    val versions: StateFlow<List<GameVersion>> = _versions.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 当前安装进度（非 null 表示正在安装）。 */
    private val _installProgress = MutableStateFlow<InstallProgress?>(null)
    val installProgress: StateFlow<InstallProgress?> = _installProgress.asStateFlow()

    /** 安装完成提示。 */
    private val _installMessage = MutableStateFlow<String?>(null)
    val installMessage: StateFlow<String?> = _installMessage.asStateFlow()

    private var loaded = false

    private var installJob: kotlinx.coroutines.Job? = null

    fun loadIfNeeded() {
        if (loaded) return
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // 阶段1：立即显示缓存（含过期缓存，保证有列表可看）
            val cached = withContext(Dispatchers.IO) { repository.loadVersionsCached() }
            if (cached != null) {
                _versions.value = cached
                loaded = true
            }
            _loading.value = true
            _error.value = null
            try {
                // 阶段2：网络刷新（成功更新缓存，失败静默保留旧列表）
                _versions.value = repository.loadVersions()
                loaded = true
                _error.value = null
            } catch (e: Throwable) {
                // 有缓存时静默（保留旧列表）；无缓存时显示错误，避免误显示"暂无版本"
                if (cached == null || cached.isEmpty()) {
                    _error.value = e.message ?: "版本列表加载失败"
                } else {
                    _error.value = null
                }
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * 安装指定版本（可选加载器）。
     */
    fun installVersion(versionId: String, loader: McLoader) {
        if (_installProgress.value != null) return
        val taskId = "version-$versionId-${loader.id}"
        DownloadManager.start(taskId, "版本 $versionId · ${loader.label}", filesTotal = 1)
        // 版本清单 size 作为估算总大小（显示"已下载 X GB / 共 Y GB"）
        val estSize = _versions.value.firstOrNull { it.id == versionId }?.size
        if (estSize != null && estSize > 0) DownloadManager.setEstimatedTotal(taskId, estSize)
        installJob = viewModelScope.launch {
            _installProgress.value = InstallProgress(versionId = versionId, loader = loader)
            _installMessage.value = null
            try {
                repository.installVersion(
                    versionId = versionId,
                    loader = loader,
                    onStage = { stage, progress ->
                        // 整体进度 = (已完成任务 + 当前任务进度) / 任务总数。
                        // 旧公式分母取 max(taskCount, done+1)：全部任务完成后 done==taskCount 时
                        // 分母仍为 taskCount+1，进度永远停在 ~99% 直到安装函数返回（"下载卡 99%"）。
                        // 改为：全部任务完成 → 直接 100%；否则 (done + 当前进度) / 任务总数。
                        val taskCount = DownloadManager.task(taskId)?.filesTotal ?: 1
                        val done = DownloadManager.task(taskId)?.filesDone ?: 0
                        val overall = if (done >= taskCount.coerceAtLeast(1)) 1f
                        else ((done + progress.coerceIn(0f, 1f)) / taskCount.coerceAtLeast(1))
                            .coerceIn(0f, 1f)
                        _installProgress.update { it?.copy(currentStage = stage, overallProgress = overall) }
                        DownloadManager.setStage(taskId, stage)
                        DownloadManager.setProgress(taskId, overall)
                        // 版本安装按估算总大小显示字节进度（用户可见"已下载 X GB / 共 Y GB"）
                        DownloadManager.setByteProgress(taskId, overall)
                    },
                    onTaskCount = { total ->
                        // 按 Task 身份去重的下载任务总数（单调增长，不再倒退）
                        DownloadManager.setFilesTotal(taskId, total)
                    },
                    onTaskDone = {
                        // 完成一个下载任务（对应 onFinished）
                        DownloadManager.fileDone(taskId)
                        // 任务完成时立即刷新整体进度：全部完成 → 100%（避免"卡 99%"）
                        val taskCount = DownloadManager.task(taskId)?.filesTotal ?: 1
                        val done = DownloadManager.task(taskId)?.filesDone ?: 0
                        if (done >= taskCount.coerceAtLeast(1)) {
                            _installProgress.update { it?.copy(overallProgress = 1f) }
                            DownloadManager.setProgress(taskId, 1f)
                        }
                    },
                    onItem = { name, fileProgress, done ->
                        // 同步当前文件到全局下载任务（顶部"正在下载"实时显示，避免玩家误以为卡住）
                        com.miolauncher.app.data.DownloadManager.setStage(taskId, name)
                        _installProgress.update { p ->
                            p?.let {
                                val items = it.items.toMutableList()
                                val idx = items.indexOfFirst { i -> i.name == name }
                                if (idx >= 0) {
                                    items[idx] = items[idx].copy(progress = fileProgress)
                                } else {
                                    items.add(
                                        com.miolauncher.app.data.DownloadItem(
                                            name = name,
                                            progress = fileProgress,
                                            state = if (done) com.miolauncher.app.data.DownloadItemState.DONE
                                            else com.miolauncher.app.data.DownloadItemState.DOWNLOADING,
                                        )
                                    )
                                }
                                it.copy(items = items)
                            }
                        }
                    },
                )
                _installProgress.update { it?.copy(isDone = true, currentStage = "安装完成", overallProgress = 1f) }
                DownloadManager.finish(taskId)
                // 安装后检测 Java 兼容性：不兼容的版本给出明确提示，避免用户点启动后困惑
                val compatMsg = runCatching { repository.javaCompatibilityMessage(versionId) }.getOrNull()
                _installMessage.value = if (compatMsg != null) {
                    "版本 $versionId 已安装，但${compatMsg}"
                } else {
                    "版本 $versionId 安装完成"
                }
                _versions.value = _versions.value.map {
                    if (it.id == versionId) it.copy(isDownloaded = true) else it
                }
            } catch (e: Throwable) {
                _installProgress.update {
                    it?.copy(isDone = true, error = friendlyError(versionId, loader, e), currentStage = "安装失败")
                }
                DownloadManager.finish(taskId, error = e.message)
                _installMessage.value = "安装失败：${friendlyError(versionId, loader, e)}"
            }
        }
    }

    fun dismissInstall() {
        _installProgress.value = null
        _installMessage.value = null
    }

    /** 把底层错误转为玩家可理解的提示 */
    private fun friendlyError(versionId: String, loader: McLoader, e: Throwable): String {
        val msg = e.message ?: "未知错误"
        // Forge/NeoForge 需要运行外部 Java 进程，Android SELinux 禁止 exec app 目录二进制
        if ((loader == McLoader.FORGE || loader == McLoader.NEO_FORGE)
            && (msg.contains("Permission denied") || msg.contains("execute_no_trans") || msg.contains("Cannot run program"))) {
            return "Forge 安装器需要运行外部 Java 进程，但 Android 系统安全限制（SELinux）禁止了此操作，导致无法安装。\n" +
                "请改用原版或 Fabric/Quilt 加载器（Forge 请在电脑端启动器安装）。"
        }
        return msg
    }

    /**
     * 真正取消当前安装任务。
     */
    fun cancelInstall() {
        installJob?.cancel()
        installJob = null
        _installProgress.value = null
        _installMessage.value = null
        DownloadManager.removeAll()
    }
}
