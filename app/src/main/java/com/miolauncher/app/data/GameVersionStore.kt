package com.miolauncher.app.data

import android.content.Context

/**
 * 持久化「当前选中的游戏版本」（主页选择 + 资源/下载页安装组件的目标版本）。
 * 跨页面切换、跨应用重启均保持。
 */
object GameVersionStore {
    private const val PREF = "mio_game_version"
    private const val KEY_SELECTED = "selected_version_id"

    fun get(context: Context): String? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED, null)

    fun set(context: Context, versionId: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_SELECTED, versionId).apply()
    }
}
