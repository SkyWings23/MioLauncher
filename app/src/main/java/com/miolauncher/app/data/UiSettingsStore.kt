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
    private const val KEY_PAGE_ANIM = "ui_page_animation"
    private const val KEY_PAGE_ANIM_MS = "ui_page_animation_ms"

    fun showSplash(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_SHOW_SPLASH, true)

    fun setShowSplash(context: Context, value: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_SPLASH, value).apply()
    }

    /** 页面切换动画是否开启（默认开）。内存缓存供 Compose 即时响应。 */
    @Volatile
    var pageAnimEnabledCache: Boolean = true
        private set

    @Volatile
    private var pageAnimInit: Boolean = false

    fun pageAnimationEnabled(context: Context): Boolean {
        if (!pageAnimInit) {
            pageAnimEnabledCache = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getBoolean(KEY_PAGE_ANIM, true)
            pageAnimMsCache = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getInt(KEY_PAGE_ANIM_MS, 350)
            pageAnimInit = true
        }
        return pageAnimEnabledCache
    }

    fun setPageAnimationEnabled(context: Context, value: Boolean) {
        pageAnimEnabledCache = value
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PAGE_ANIM, value).apply()
    }

    /** 页面切换动画时长（毫秒，默认 350） */
    @Volatile
    var pageAnimMsCache: Int = 350
        private set

    fun pageAnimationMs(context: Context): Int {
        pageAnimationEnabled(context) // 触发初始化
        return pageAnimMsCache
    }

    fun setPageAnimationMs(context: Context, value: Int) {
        pageAnimMsCache = value.coerceIn(50, 1000)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PAGE_ANIM_MS, value.coerceIn(50, 1000)).apply()
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
