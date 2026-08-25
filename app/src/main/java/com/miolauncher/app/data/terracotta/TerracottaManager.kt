package com.miolauncher.app.data.terracotta

import android.content.Context
import net.burningtnt.terracotta.TerracottaAndroidAPI
import org.jackhuang.hmcl.util.gson.JsonUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android 版 Terracotta（陶瓦联机）管理器。
 *
 * 通过 JNI 调用 libterracotta.so 内置的 EasyTier 虚拟局域网：
 *  - 创建房间（host）：setScanning 触发扫描，最终状态为 host-ok，得到房间号
 *  - 加入房间（guest）：setGuesting(room) 触发连接，最终状态为 guest-ok，得到局域网地址
 *  - 需要 VpnService 授权（本地 VPN，转发联机流量）
 */
object TerracottaManager {
    private val _state = MutableStateFlow<TerracottaState>(TerracottaState.Bootstrap.INSTANCE)
    val state: StateFlow<TerracottaState> = _state

    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized

    /** 扫描超时后置为 true，UI 显示"未找到服务器"提示（读取后需调用 clearScanTimeout） */
    private val _scanTimeout = MutableStateFlow(false)
    val scanTimeout: StateFlow<Boolean> = _scanTimeout

    /** 扫描开始时间戳（用于超时检测） */
    @Volatile
    private var scanStartTime: Long = -1

    /** 扫描超时毫秒数 */
    private const val SCAN_TIMEOUT_MS = 20_000L

    private var gson = JsonUtils.GSON

    @Volatile
    private var polling = false

    private var vpnCallback: (() -> Unit)? = null

    /** 轮询线程是否在运行（用于 UI 显示） */
    val pollingThreadActive: Boolean get() = polling

    fun initialize(context: Context, vpnServiceCallback: () -> Unit) {
        if (_initialized.value) return
        vpnCallback = vpnServiceCallback
        val callback = TerracottaAndroidAPI.VpnServiceCallback {
            vpnCallback?.invoke()
        }
        val metadata = TerracottaAndroidAPI.initialize(context.applicationContext, callback)
        _initialized.value = true

        startPolling()
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        Thread {
            while (polling) {
                try {
                    val json = TerracottaAndroidAPI.getState()
                    val parsed = gson.fromJson(json, TerracottaState.Ready::class.java)
                    if (parsed != null) {
                        // 进入扫描状态：记录开始时间
                        if (parsed is TerracottaState.HostScanning && scanStartTime < 0) {
                            scanStartTime = System.currentTimeMillis()
                        }
                        // 离开扫描状态：清除计时（scanTimeout 标记由用户操作/新扫描清除，保证 UI 能显示超时结果）
                        if (parsed !is TerracottaState.HostScanning) {
                            scanStartTime = -1
                        }
                        // 进入新的扫描时清除上一次的超时提示
                        if (parsed is TerracottaState.HostScanning) {
                            _scanTimeout.value = false
                        }
                        _state.value = parsed
                    }

                    // 扫描超时：自动取消，提示用户先对局域网开放
                    if (_state.value is TerracottaState.HostScanning && scanStartTime > 0) {
                        val elapsed = System.currentTimeMillis() - scanStartTime
                        if (elapsed > SCAN_TIMEOUT_MS) {
                            scanStartTime = -1
                            _scanTimeout.value = true
                            try {
                                TerracottaAndroidAPI.setWaiting()
                            } catch (_: Exception) {
                            }
                            _state.value = TerracottaState.Waiting(-1, -1, null)
                        }
                    }
                } catch (_: Exception) {
                    // native not ready yet / transient
                }
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply { isDaemon = true }.start()
    }

    fun setWaiting() {
        try {
            TerracottaAndroidAPI.setWaiting()
        } catch (_: Exception) {
        }
        scanStartTime = -1
        _scanTimeout.value = false
        _state.value = TerracottaState.Waiting(-1, -1, null)
    }

    fun setScanning(playerName: String?) {
        try {
            TerracottaAndroidAPI.setScanning(null, playerName)
        } catch (_: Exception) {
        }
        scanStartTime = System.currentTimeMillis()
        _scanTimeout.value = false
        _state.value = TerracottaState.HostScanning(-1, -1, null)
    }

    /** 读取并清除扫描超时标记（UI 消费后调用，避免重复提示） */
    fun consumeScanTimeout(): Boolean {
        val v = _scanTimeout.value
        if (v) _scanTimeout.value = false
        return v
    }

    /** 加入房间。返回房间号是否合法。 */
    fun setGuesting(room: String, playerName: String?): Boolean {
        val ok = TerracottaAndroidAPI.setGuesting(room, playerName)
        if (ok) {
            scanStartTime = -1
            _scanTimeout.value = false
            _state.value = TerracottaState.GuestConnecting(-1, -1, null)
        }
        return ok
    }

    fun parseRoomCode(room: String): TerracottaAndroidAPI.RoomType? =
        TerracottaAndroidAPI.parseRoomCode(room)

    fun collectLogs(): String = try {
        TerracottaAndroidAPI.collectLogs().use { it.readText() }
    } catch (e: Exception) {
        "无法读取日志: ${e.message}"
    }

    fun shutdown() {
        polling = false
    }
}
