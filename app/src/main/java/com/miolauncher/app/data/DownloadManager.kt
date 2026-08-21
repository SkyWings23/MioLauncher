package com.miolauncher.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * 全局下载管理器：模组 / 版本 / 光影等所有下载统一走这里，
 * 供悬浮窗展示「已下载 / 总数 / 当前速度」。
 *
 * 两种模式：
 * - 字节模式（total > 0）：显示 X MB / Y MB · 速度 MB/s（模组等自控下载）
 * - 百分比模式（total == 0）：显示 N% · 速度 %/s（HMCL 版本安装等）
 */
object DownloadManager {

    data class Task(
        val id: String,
        val label: String,
        val currentFile: String = "",
        val downloaded: Long = 0,
        val total: Long = 0,
        val percent: Float = 0f,
        val speed: Float = 0f,   // total>0 为 bytes/s，否则为 %/s
        val filesDone: Int = 0,
        val filesTotal: Int = 1,
        val isDone: Boolean = false,
        val error: String? = null,
    ) {
        val isActive: Boolean get() = !isDone

        /** 剩余待下载字节（字节模式才有意义，否则 0） */
        val remaining: Long get() = if (total > 0) (total - downloaded).coerceAtLeast(0) else 0

        /** 进度 0..1（字节/百分比模式统一） */
        val progress: Float
            get() = when {
                isDone -> 1f
                total > 0 && downloaded > 0 -> (downloaded.toFloat() / total).coerceIn(0f, 1f)
                else -> percent.coerceIn(0f, 1f)
            }

        /** 预计剩余时间（秒）。速度<=0 或剩余为 0 返回 null。 */
        val etaSeconds: Long?
            get() {
                if (isDone) return null
                if (speed <= 0f) return null
                return if (total > 0) {
                    if (remaining <= 0) null
                    else (remaining.toFloat() / speed).toLong()
                } else {
                    // 百分比模式：剩余进度 / 每秒进度增幅
                    val pctPerSec = speed / 1000f   // speed 存的是 %/s * 1000
                    if (pctPerSec <= 0f) null else ((1f - progress.coerceIn(0f, 1f)) / pctPerSec).toLong()
                }
            }

        /** 预计剩余时间人类可读文本 */
        val etaText: String
            get() {
                val eta = etaSeconds ?: return "—"
                return when {
                    eta >= 3600 -> "%d小时%02d分".format(eta / 3600, (eta % 3600) / 60)
                    eta >= 60 -> "%d分%02d秒".format(eta / 60, eta % 60)
                    else -> "%d秒".format(eta)
                }
            }
    }

    private val _tasks = MutableStateFlow<Map<String, Task>>(emptyMap())
    val tasks: StateFlow<List<Task>> = _tasks
        .map { it.values.toList() }
        .stateIn(CoroutineScope(Dispatchers.Main.immediate), SharingStarted.Eagerly, emptyList())

    // 速度追踪：id -> long[0]=lastBytes, long[1]=lastTimeMs
    private val speedState = HashMap<String, LongArray>()

    fun task(id: String): Task? = _tasks.value[id]

    fun start(id: String, label: String, filesTotal: Int = 1) {
        _tasks.update {
            it + (id to Task(id = id, label = label, filesTotal = filesTotal.coerceAtLeast(1)))
        }
        synchronized(speedState) {
            speedState[id] = longArrayOf(0L, System.currentTimeMillis())
        }
    }

    fun setFilesTotal(id: String, n: Int) {
        _tasks.update {
            it[id]?.let { t -> it + (id to t.copy(filesTotal = n.coerceAtLeast(1))) } ?: it
        }
    }

    /** 开始下载某个文件（字节模式：累加 total） */
    fun beginFile(id: String, name: String, size: Long) {
        _tasks.update {
            it[id]?.let { t ->
                it + (id to t.copy(
                    currentFile = name,
                    total = t.total + size.coerceAtLeast(0),
                ))
            } ?: it
        }
    }

    /** 字节模式：追加已下载字节数（自动更新速度） */
    fun addBytes(id: String, bytes: Long) {
        val now = System.currentTimeMillis()
        var speed = 0f
        synchronized(speedState) {
            val st = speedState[id]
            if (st != null) {
                val dt = now - st[1]
                if (dt > 400) {
                    val bytesDelta = bytes - st[0]
                    speed = bytesDelta.toFloat() * 1000f / dt
                    st[0] = bytes
                    st[1] = now
                }
            } else {
                speedState[id] = longArrayOf(bytes, now)
            }
        }
        _tasks.update {
            it[id]?.let { t ->
                val downloaded = t.downloaded + bytes
                val percent = if (t.total > 0) (downloaded.toFloat() / t.total) else t.percent
                it + (id to t.copy(
                    downloaded = downloaded,
                    percent = percent.coerceIn(0f, 1f),
                    speed = speed,
                ))
            } ?: it
        }
    }

    /** 百分比模式：直接设进度 */
    fun setProgress(id: String, percent: Float) {
        val now = System.currentTimeMillis()
        var speed = 0f
        synchronized(speedState) {
            val st = speedState[id]
            if (st != null) {
                val dt = now - st[1]
                if (dt > 400) {
                    val pctDelta = percent - st[0] / 1000f
                    speed = pctDelta * 1000f / dt
                    st[0] = (percent * 1000f).toLong()
                    st[1] = now
                }
            } else {
                speedState[id] = longArrayOf((percent * 1000f).toLong(), now)
            }
        }
        _tasks.update {
            it[id]?.let { t ->
                it + (id to t.copy(percent = percent.coerceIn(0f, 1f), speed = speed))
            } ?: it
        }
    }

    /** 更新当前文件与阶段文案 */
    fun setStage(id: String, stage: String) {
        _tasks.update {
            it[id]?.let { t -> it + (id to t.copy(currentFile = stage)) } ?: it
        }
    }

    /** 完成一个文件 */
    fun fileDone(id: String) {
        _tasks.update {
            it[id]?.let { t ->
                it + (id to t.copy(filesDone = t.filesDone + 1))
            } ?: it
        }
    }

    fun finish(id: String, error: String? = null) {
        _tasks.update {
            it[id]?.let { t ->
                it + (id to t.copy(isDone = true, error = error, percent = if (error == null) 1f else t.percent))
            } ?: it
        }
        synchronized(speedState) { speedState.remove(id) }
    }

    fun remove(id: String) {
        _tasks.update { it - id }
        synchronized(speedState) { speedState.remove(id) }
    }

    fun removeAll() {
        _tasks.update { emptyMap() }
        synchronized(speedState) { speedState.clear() }
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / 1024f / 1024f / 1024f)
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024f / 1024f)
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}
