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
    private const val KEY_GEOFENCE_LAST_TRANSITION = "geofence_last_transition"
    private const val KEY_GEOFENCE_LAST_TIME = "geofence_last_time"
    private const val KEY_GEOFENCE_LAST_REMINDER = "geofence_last_reminder"
    private const val KEY_GEOFENCE_REG_RESULT = "geofence_reg_result"
    private const val KEY_GEOFENCE_REG_TIME = "geofence_reg_time"
    private const val KEY_GEOFENCE_REG_ID = "geofence_reg_id"
    private const val KEY_LAST_BOOT_ELAPSED = "last_boot_elapsed_realtime"
    private const val KEY_BOOT_MISSING = "boot_broadcast_missing"
    private const val KEY_LAST_BOOT_RESULT = "last_boot_result"

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

    /** 最近一次 Geofence 事件类型，如 ENTER / EXIT。 */
    fun geofenceLastTransition(context: Context): String? =
        prefs(context).getString(KEY_GEOFENCE_LAST_TRANSITION, null)

    /** 最近一次 Geofence 事件的接收时间（epoch ms），0 = 无记录。 */
    fun geofenceLastTime(context: Context): Long =
        prefs(context).getLong(KEY_GEOFENCE_LAST_TIME, 0L)

    /** 最近一次 Geofence 事件对应的提醒 id，0 = 无记录。 */
    fun geofenceLastReminder(context: Context): Long =
        prefs(context).getLong(KEY_GEOFENCE_LAST_REMINDER, 0L)

    fun setGeofenceLastEvent(context: Context, transition: String, atMillis: Long, reminderId: Long?) {
        prefs(context).edit()
            .putString(KEY_GEOFENCE_LAST_TRANSITION, transition)
            .putLong(KEY_GEOFENCE_LAST_TIME, atMillis)
            .putLong(KEY_GEOFENCE_LAST_REMINDER, reminderId ?: 0L)
            .apply()
    }

    /** 最近一次 Geofence 注册结果："OK" 或 "ERROR:<code>:<name>"。 */
    fun geofenceRegistrationResult(context: Context): String? =
        prefs(context).getString(KEY_GEOFENCE_REG_RESULT, null)

    fun geofenceRegistrationTime(context: Context): Long =
        prefs(context).getLong(KEY_GEOFENCE_REG_TIME, 0L)

    fun geofenceRegistrationReminder(context: Context): Long =
        prefs(context).getLong(KEY_GEOFENCE_REG_ID, 0L)

    fun setGeofenceRegistration(context: Context, reminderId: Long, result: String, atMillis: Long) {
        prefs(context).edit()
            .putString(KEY_GEOFENCE_REG_RESULT, result)
            .putLong(KEY_GEOFENCE_REG_TIME, atMillis)
            .putLong(KEY_GEOFENCE_REG_ID, reminderId)
            .apply()
    }

    /**
     * 上次成功处理重启时记下的开机时长（elapsedRealtime）。elapsedRealtime 每次开机归零，
     * 因此当“当前开机时长 < 上次记录值”时，说明设备重启过而 App 没有收到 BOOT_COMPLETED。
     */
    fun lastHandledBootElapsedRealtime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_BOOT_ELAPSED, 0L)

    fun setLastHandledBootElapsedRealtime(context: Context, elapsedRealtime: Long) {
        prefs(context).edit().putLong(KEY_LAST_BOOT_ELAPSED, elapsedRealtime).apply()
    }

    /** 重启广播疑似被厂商后台策略拦截（仅用于诊断，不做任何自动补偿）。 */
    fun bootBroadcastMissing(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BOOT_MISSING, false)

    fun setBootBroadcastMissing(context: Context, missing: Boolean) {
        prefs(context).edit().putBoolean(KEY_BOOT_MISSING, missing).apply()
    }

    /** 最近一次重启恢复的机器可读结果。 */
    fun lastBootResult(context: Context): String? =
        prefs(context).getString(KEY_LAST_BOOT_RESULT, null)

    fun setLastBootResult(context: Context, result: String) {
        prefs(context).edit().putString(KEY_LAST_BOOT_RESULT, result).apply()
    }
}
