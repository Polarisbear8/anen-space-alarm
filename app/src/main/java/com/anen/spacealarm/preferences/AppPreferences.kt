package com.anen.spacealarm.preferences

import android.content.Context

/**
 * App 偏好（SharedPreferences）。
 * 语言与开发者模式都是 App preference，不是业务数据，因此不进数据库。
 */
object AppPreferences {

    private const val FILE = "anen_settings"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_DEVELOPER_MODE = "developer_mode"
    private const val KEY_LAST_SHARE_DEBUG = "last_share_debug"
    private const val KEY_LAST_RESOLVE_DEBUG = "last_resolve_debug"
    private const val KEY_TUTORIAL_SEEN = "tutorial_seen"
    private const val KEY_LOCATION_INTERVAL = "location_interval_seconds"

    /** 地图前台定位刷新档位（秒），默认 5 秒。 */
    val LOCATION_INTERVAL_OPTIONS = listOf(3, 5, 10, 30)
    const val DEFAULT_LOCATION_INTERVAL_SECONDS = 5

    /**
     * 注意：这里不能走 applicationContext。
     * Application.attachBaseContext 阶段（语言包装时）applicationContext 还是 null。
     */
    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** null = 跟随系统 */
    fun language(context: Context): String? =
        prefs(context).getString(KEY_LANGUAGE, null)?.takeIf { it.isNotBlank() }

    fun setLanguage(context: Context, languageTag: String?) {
        val editor = prefs(context).edit()
        if (languageTag.isNullOrBlank()) editor.remove(KEY_LANGUAGE) else editor.putString(KEY_LANGUAGE, languageTag)
        editor.apply()
    }

    fun developerMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEVELOPER_MODE, false)

    fun setDeveloperMode(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply()
    }

    /** 最近一次分享载荷（供开发者模式查看/复制） */
    fun lastShareDebug(context: Context): String? =
        prefs(context).getString(KEY_LAST_SHARE_DEBUG, null)

    fun setLastShareDebug(context: Context, report: String) {
        prefs(context).edit().putString(KEY_LAST_SHARE_DEBUG, report).apply()
    }

    /** 最近一次高德解析报告（供开发者模式查看/复制） */
    fun lastResolveDebug(context: Context): String? =
        prefs(context).getString(KEY_LAST_RESOLVE_DEBUG, null)

    fun setLastResolveDebug(context: Context, report: String) {
        prefs(context).edit().putString(KEY_LAST_RESOLVE_DEBUG, report).apply()
    }

    /** 是否已经看过首次使用教程 */
    fun tutorialSeen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TUTORIAL_SEEN, false)

    fun setTutorialSeen(context: Context, seen: Boolean) {
        prefs(context).edit().putBoolean(KEY_TUTORIAL_SEEN, seen).apply()
    }

    fun locationIntervalSeconds(context: Context): Int =
        prefs(context).getInt(KEY_LOCATION_INTERVAL, DEFAULT_LOCATION_INTERVAL_SECONDS)
            .takeIf { it in LOCATION_INTERVAL_OPTIONS }
            ?: DEFAULT_LOCATION_INTERVAL_SECONDS

    fun setLocationIntervalSeconds(context: Context, seconds: Int) {
        if (seconds !in LOCATION_INTERVAL_OPTIONS) return
        prefs(context).edit().putInt(KEY_LOCATION_INTERVAL, seconds).apply()
    }
}
