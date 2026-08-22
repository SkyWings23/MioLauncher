package com.miolauncher.app.data

import android.content.Context

/**
 * 外观 / 界面类设置：开机动画、界面语言等。
 */
object UiSettingsStore {
    private const val PREF = "mio_ui"
    private const val KEY_SHOW_SPLASH = "ui_show_splash"
    private const val KEY_APP_LANG = "ui_app_lang"
    private const val KEY_HIDE_NON_STANDARD = "ui_hide_non_standard_versions"

    fun showSplash(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_SPLASH, true)

    fun setShowSplash(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_SPLASH, value).apply()
    }

    fun hideNonStandardVersions(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_HIDE_NON_STANDARD, false)

    fun setHideNonStandardVersions(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HIDE_NON_STANDARD, value).apply()
    }

    fun appLang(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_APP_LANG, "zh_cn") ?: "zh_cn"

    fun setAppLang(context: Context, lang: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_APP_LANG, lang).apply()
    }
}
