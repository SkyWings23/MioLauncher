package com.miolauncher.app.data.terracotta

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import net.burningtnt.terracotta.TerracottaAndroidAPI

/**
 * 陶瓦联机（Terracotta）本地 VPN 服务。
 *
 * libterracotta.so 会回调 onVpnServiceStateChanged，请求建立本地 VPN 隧道
 * （EasyTier 虚拟局域网）。本服务在 onStartCommand 里取回 pending request，
 * 用系统 VpnService.Builder 建立 VPN 连接并持有 fd，直到 Terracotta 会话结束。
 */
class TerracottaVpnService : VpnService() {
    private var connection: ParcelFileDescriptor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (connection == null) {
            try {
                val request = TerracottaAndroidAPI.getPendingVpnServiceRequest()
                val builder = Builder()
                connection = request.startVpnService(builder)
                Log.i(TAG, "Terracotta VPN established")
            } catch (e: Exception) {
                Log.w(TAG, "Cannot establish Terracotta VPN: ${e.message}")
                try {
                    TerracottaAndroidAPI.getPendingVpnServiceRequest().reject()
                } catch (_: Exception) {
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try {
            connection?.close()
        } catch (_: Exception) {
        }
        connection = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TerracottaVPN"

        /** VpnService 是否已授权 */
        fun isPrepared(context: android.content.Context): Boolean =
            VpnService.prepare(context) == null

        /** 触发系统授权对话框；返回 null 表示已授权 */
        fun prepareIntent(context: android.content.Context): Intent? =
            VpnService.prepare(context)
    }
}
