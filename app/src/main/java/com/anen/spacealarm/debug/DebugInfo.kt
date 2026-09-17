package com.anen.spacealarm.debug

import android.content.Context
import android.location.Location
import android.os.Build
import com.anen.spacealarm.AppContainer
import com.anen.spacealarm.BuildConfig
import com.anen.spacealarm.R
import com.anen.spacealarm.geofence.GoogleGeofenceEngine
import com.anen.spacealarm.location.DistanceCalculator
import com.anen.spacealarm.map.MapStyleManager
import com.anen.spacealarm.model.Reminder
import com.anen.spacealarm.permission.PermissionManager
import com.anen.spacealarm.preferences.AppPreferences

/**
 * 开发者模式下的调试信息。
 *
 * 只输出当前工程确实存在的信息，不虚构不存在的引擎或字段；
 * 所有文本走字符串资源，保证与界面语言一致。
 */
object DebugInfo {

    fun appSection(context: Context): String = buildString {
        appendLine(line(context.getString(R.string.dev_label_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
        appendLine(line(context.getString(R.string.dev_label_package), BuildConfig.APPLICATION_ID))
        appendLine(line(context.getString(R.string.dev_label_android), Build.VERSION.RELEASE))
        appendLine(line(context.getString(R.string.dev_label_sdk), Build.VERSION.SDK_INT.toString()))
        appendLine(line(context.getString(R.string.dev_label_device), "${Build.MANUFACTURER} ${Build.MODEL}"))
    }.trim()

    fun permissionSection(context: Context): String {
        val state = PermissionManager.state(context)
        return buildString {
            appendLine(line(context.getString(R.string.permission_location), granted(context, state.fineLocation || state.coarseLocation)))
            appendLine(line(context.getString(R.string.permission_precise), granted(context, state.fineLocation)))
            appendLine(line(context.getString(R.string.permission_background), granted(context, state.backgroundLocation)))
            appendLine(line(context.getString(R.string.permission_notifications), granted(context, state.notifications)))
            appendLine(line(context.getString(R.string.permission_fullscreen), granted(context, state.fullScreenIntent)))
            appendLine(line(context.getString(R.string.permission_geofence_ready), granted(context, state.geofenceReady)))
        }.trim()
    }

    fun locationSection(context: Context, location: Location?): String = buildString {
        if (location == null) {
            appendLine(line(context.getString(R.string.dev_label_latitude), context.getString(R.string.dev_label_no_data)))
        } else {
            appendLine(line(context.getString(R.string.dev_label_latitude), "%.6f".format(location.latitude)))
            appendLine(line(context.getString(R.string.dev_label_longitude), "%.6f".format(location.longitude)))
            appendLine(
                line(
                    context.getString(R.string.dev_label_accuracy),
                    "${"%.0f".format(location.accuracy)} m"
                )
            )
            appendLine(line(context.getString(R.string.dev_label_provider), location.provider ?: "-"))
            appendLine(line(context.getString(R.string.dev_label_timestamp), location.time.toString()))
        }
        appendLine(line(context.getString(R.string.dev_label_coordinate_system), "WGS84"))
    }.trim()

    fun mapSection(context: Context): String = buildString {
        appendLine(line(context.getString(R.string.dev_label_map_engine), context.getString(R.string.dev_map_engine_value)))
        appendLine(line(context.getString(R.string.dev_label_map_source), MapStyleManager.tileSourceHost()))
    }.trim()

    fun geofenceSection(context: Context, activeCount: Int, totalCount: Int): String = buildString {
        val engine = AppContainer.geofenceEngine(context)
        appendLine(
            line(
                context.getString(R.string.dev_label_geofence_engine),
                if (engine.isAvailable()) context.getString(R.string.dev_geofence_engine_value)
                else context.getString(R.string.dev_value_not_available)
            )
        )
        appendLine(line(context.getString(R.string.dev_label_active_geofences), "$activeCount / ${AppContainer.MAX_GEOFENCES}"))
        appendLine(line(context.getString(R.string.dev_label_reminders), totalCount.toString()))
    }.trim()

    fun alarmSection(context: Context): String {
        val state = PermissionManager.state(context)
        return buildString {
            appendLine(line(context.getString(R.string.dev_label_notification_channel), granted(context, state.notifications)))
            appendLine(line(context.getString(R.string.dev_label_alarm_channel), granted(context, state.notifications)))
            appendLine(line(context.getString(R.string.dev_label_fullscreen), granted(context, state.fullScreenIntent)))
        }.trim()
    }

    fun locationUpdatesSection(context: Context): String =
        line(
            context.getString(R.string.dev_label_location_updates),
            context.getString(
                R.string.location_refresh_seconds,
                AppPreferences.locationIntervalSeconds(context)
            )
        )

    fun reminderSection(context: Context, reminders: List<Reminder>): String = buildString {
        appendLine(line(context.getString(R.string.dev_label_reminders), reminders.size.toString()))
        appendLine(line(context.getString(R.string.dev_label_active_reminders), reminders.count { it.enabled && !it.triggered }.toString()))
        reminders.take(5).forEach { reminder ->
            appendLine("")
            appendLine(line(context.getString(R.string.dev_reminder_id), reminder.id.toString()))
            appendLine(line(context.getString(R.string.dev_reminder_target), reminder.placeName))
            appendLine(
                line(
                    context.getString(R.string.dev_label_latitude) + "/" + context.getString(R.string.dev_label_longitude),
                    "%.6f / %.6f".format(reminder.latitude, reminder.longitude)
                )
            )
            appendLine(line(context.getString(R.string.dev_reminder_radius), DistanceCalculator.formatRadius(reminder.radiusMeters)))
            appendLine(line(context.getString(R.string.dev_reminder_state), reminder.status.name))
            appendLine(line(context.getString(R.string.dev_reminder_alert_mode), reminder.alertMode.name))
        }
    }.trim()

    fun shareSection(context: Context): String =
        AppPreferences.lastShareDebug(context) ?: context.getString(R.string.dev_label_no_data)

    fun resolverSection(context: Context): String =
        AppPreferences.lastResolveDebug(context) ?: context.getString(R.string.dev_label_no_data)

    /** 一键复制用的完整调试报告。 */
    fun buildReport(
        context: Context,
        reminders: List<Reminder>,
        userLocation: Location?
    ): String {
        val activeCount = reminders.count { it.enabled && !it.triggered }
        return buildString {
            appendLine("=== ANEN SPACE ALARM DEBUG ===")
            appendLine()
            appendLine("[App]")
            appendLine(appSection(context))
            appendLine()
            appendLine("[Permissions]")
            appendLine(permissionSection(context))
            appendLine()
            appendLine("[Location]")
            appendLine(locationSection(context, userLocation))
            appendLine()
            appendLine("[Map]")
            appendLine(mapSection(context))
            appendLine()
            appendLine("[Geofence]")
            appendLine(geofenceSection(context, activeCount, reminders.size))
            appendLine()
            appendLine("[Alarm]")
            appendLine(alarmSection(context))
            appendLine()
            appendLine("[Reminders]")
            appendLine(reminderSection(context, reminders))
            appendLine()
            appendLine("[Last share]")
            appendLine(shareSection(context))
            appendLine()
            appendLine("[Last resolve]")
            appendLine(resolverSection(context))
        }.trim()
    }

    private fun line(label: String, value: String): String = "$label: $value"

    private fun granted(context: Context, ok: Boolean): String = context.getString(
        if (ok) R.string.dev_value_yes else R.string.dev_value_no
    )
}
