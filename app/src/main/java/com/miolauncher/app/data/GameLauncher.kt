package com.miolauncher.app.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.miolauncher.app.GameActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 启动器侧的账户与游戏启动统一入口。
 */
object GameLauncher {
    private const val PREF_NAME = "mio_account"
    private const val KEY_USERNAME = "offline_username"

    /** 读取已保存的离线用户名（未创建返回 null） */
    fun offlineUsername(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_USERNAME, "") ?: ""
        return raw.trim().ifEmpty { null }
    }

    /** 保存离线用户名 */
    fun saveOfflineUsername(context: Context, name: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_USERNAME, name.trim()).apply()
    }

    /** 是否已创建离线账户 */
    fun hasAccount(context: Context): Boolean = offlineUsername(context) != null

    /**
     * 启动游戏：补齐库文件后拉起 GameActivity。
     * @param serverAddress 要加入的服务器（host 或 host:port），null 则单人游戏
     * @return 是否成功进入启动流程
     */
    suspend fun launch(context: Context, versionId: String, serverAddress: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val repo = MioRepository(context)
                // Java 兼容性预检：新版本需要更高 Java 时给出明确提示，避免启动后黑屏/崩溃
                val compatMsg = repo.javaCompatibilityMessage(versionId)
                if (compatMsg != null) {
                    Log.w("MioGame", "版本 $versionId Java 不兼容: $compatMsg")
                    throw java.lang.IllegalStateException(compatMsg)
                }
                // 26.x 及以上需要 LWJGL 3.4 + SDL3 原生库（当前启动器未提供），直接拒绝并提示，
                // 避免启动时 LibFFI/SDL UnsatisfiedLinkError 崩溃。
                val major = versionId.substringBefore('-').split('.').firstOrNull()?.toIntOrNull()
                if (major != null && major >= 26) {
                    throw java.lang.IllegalStateException("$versionId 需要 SDL3/LWJGL 3.4 原生支持，当前版本暂不支持，请使用 1.x ~ 25.x 版本")
                }
                repo.ensureLibraries(versionId)
                val user = offlineUsername(context) ?: "Player"
                withContext(Dispatchers.Main) {
                    val intent = Intent(context, GameActivity::class.java)
                        .putExtra("version_id", versionId)
                        .putExtra("username", user)
                    if (!serverAddress.isNullOrBlank()) {
                        intent.putExtra("server_address", serverAddress)
                    }
                    if (context !is android.app.Activity) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
                true
            } catch (e: Exception) {
                Log.e("MioGame", "启动游戏失败", e)
                false
            }
        }
}
