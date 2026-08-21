package com.miolauncher.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miolauncher.app.MioApplication
import com.miolauncher.app.data.DownloadManager
import com.miolauncher.app.data.GameVersion
import com.miolauncher.app.data.InstallProgress
import com.miolauncher.app.data.McLoader
import com.miolauncher.app.data.MioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
            _loading.value = true
            _error.value = null
            try {
                _versions.value = repository.loadVersions()
                loaded = true
            } catch (e: Exception) {
                _error.value = e.message ?: "加载版本失败"
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
        val fileNames = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val doneNames = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        DownloadManager.start(taskId, "版本 $versionId · ${loader.label}", filesTotal = 1)
        installJob = viewModelScope.launch {
            _installProgress.value = InstallProgress(versionId = versionId, loader = loader)
            _installMessage.value = null
            try {
                repository.installVersion(
                    versionId = versionId,
                    loader = loader,
                    onStage = { stage, progress ->
                        _installProgress.update { it?.copy(currentStage = stage, overallProgress = progress) }
                        DownloadManager.setStage(taskId, stage)
                        DownloadManager.setProgress(taskId, progress)
                    },
                    onItem = { name, fileProgress, done ->
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
                        // 用去重文件名统计总文件数；已完成文件数用去重集合精确统计
                        if (fileNames.add(name)) {
                            DownloadManager.setFilesTotal(taskId, fileNames.size)
                        }
                        if (done && doneNames.add(name)) {
                            DownloadManager.fileDone(taskId)
                        }
                        // 以「已完成文件 / 总文件」作为整体进度（比 HMCL 各阶段 progress 更稳定真实）
                        val overall = if (fileNames.isEmpty()) fileProgress
                        else doneNames.size.toFloat() / fileNames.size.coerceAtLeast(1)
                        _installProgress.update { it?.copy(overallProgress = overall.coerceIn(0f, 1f)) }
                        DownloadManager.setProgress(taskId, overall.coerceIn(0f, 1f))
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
            } catch (e: Exception) {
                _installProgress.update {
                    it?.copy(isDone = true, error = e.message, currentStage = "安装失败")
                }
                DownloadManager.finish(taskId, error = e.message)
                _installMessage.value = "安装失败：${e.message}"
            }
        }
    }

    fun dismissInstall() {
        _installProgress.value = null
        _installMessage.value = null
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
