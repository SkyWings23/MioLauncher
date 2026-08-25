package com.miolauncher.app.data

import android.content.Context

/**
 * UI 主题偏好持久化（深色/浅色）。PatchDownloadActivity 等独立 Activity 也据此渲染，
 * 保证与启动器主界面主题一致。
 */
object ThemeStore {
    private const val PREF = "mio_ui"
    private const val KEY_DARK = "dark_theme"

    fun isDark(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_DARK, true)

    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, dark).apply()
    }
}
