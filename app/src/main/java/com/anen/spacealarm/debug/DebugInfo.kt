package com.anen.spacealarm.debug

import android.content.Context
import android.location.Location
import android.os.Build
import android.os.SystemClock
import com.anen.spacealarm.AppContainer
import com.anen.spacealarm.BuildConfig
import com.anen.spacealarm.R
import com.anen.spacealarm.geofence.GoogleGeofenceEngine
import com.anen.spacealarm.location.AlarmLocationRuntime
import com.anen.spacealarm.location.DistanceCalculator
import com.anen.spacealarm.map.MapStyleManager
import com.anen.spacealarm.model.Reminder
import com.anen.spacealarm.permission.PermissionManager
import com.anen.spacealarm.preferences.AppPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    fun locationSection(context: Context, location: Location?, availability: Boolean?): String = buildString {
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
            appendLine(line(context.getString(R.string.dev_label_last_update), lastUpdate(location)))
            appendLine(line(context.getString(R.string.dev_label_age), ageSeconds(location)))
        }
        appendLine(
            line(
                context.getString(R.string.dev_label_availability),
                when (availability) {
                    true -> context.getString(R.string.dev_value_available)
                    false -> context.getString(R.string.dev_value_unavailable)
                    null -> context.getString(R.string.dev_value_unknown)
                }
            )
        )
        appendLine(line(context.getString(R.string.dev_label_coordinate_system), "WGS84"))
    }.trim()

    private fun lastUpdate(location: Location): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(location.time))

    private fun ageSeconds(location: Location): String {
        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        return "%.1f s".format(if (ageNanos < 0L) 0.0 else ageNanos / 1_000_000_000.0)
    }

    /** 运行时环境与兼容性。全部为检测结果，不根据厂商名推断能力。 */
    fun compatSection(context: Context): String {
        val compat = DeviceCompat.snapshot(context)
        val permissions = PermissionManager.state(context)
        val noData = context.getString(R.string.dev_label_no_data)
        return buildString {
            appendLine(line(context.getString(R.string.dev_label_manufacturer), compat.manufacturer))
            appendLine(line(context.getString(R.string.dev_label_model), compat.model))
            appendLine(
                line(
                    context.getString(R.string.dev_label_android),
                    "${compat.androidRelease} (API ${compat.sdkInt})"
                )
            )
            appendLine(line(context.getString(R.string.dev_label_gms), granted(context, compat.gmsAvailable)))
            appendLine(line(context.getString(R.string.dev_label_gms_version), compat.gmsVersion ?: noData))
            appendLine(
                line(
                    context.getString(R.string.dev_label_location_services),
                    granted(context, compat.locationServicesEnabled)
                )
            )
            appendLine(
                line(
                    context.getString(R.string.permission_location),
                    granted(context, permissions.fineLocation || permissions.coarseLocation)
                )
            )
            appendLine(line(context.getString(R.string.permission_precise), granted(context, permissions.fineLocation)))
            appendLine(line(context.getString(R.string.permission_background), granted(context, permissions.backgroundLocation)))
            appendLine(line(context.getString(R.string.permission_notifications), granted(context, permissions.notifications)))
            appendLine(
                line(
                    context.getString(R.string.dev_label_battery_optimization),
                    context.getString(
                        if (compat.batteryOptimizationExempt) R.string.dev_value_exempt
                        else R.string.dev_value_optimized
                    )
                )
            )
            appendLine(
                line(
                    context.getString(R.string.dev_label_standby_bucket),
                    "${compat.standbyBucket ?: "-"} (${DeviceCompat.bucketName(compat.standbyBucket)})"
                )
            )
            appendLine(
                line(
                    context.getString(R.string.dev_label_process_state),
                    DeviceCompat.importanceName(compat.processImportance)
                )
            )
            appendLine(line(context.getString(R.string.dev_label_geofence_registration), registrationSummary(context)))
            appendLine(
                line(
                    context.getString(R.string.dev_label_boot_restore),
                    AppPreferences.lastBootResult(context) ?: noData
                )
            )
            appendLine(
                line(
                    context.getString(R.string.dev_label_boot_broadcast),
                    context.getString(
                        if (AppPreferences.bootBroadcastMissing(context)) R.string.dev_value_missing
                        else R.string.dev_value_handled
                    )
                )
            )
        }.trim()
    }

    private fun registrationSummary(context: Context): String {
        val result = AppPreferences.geofenceRegistrationResult(context) ?: return context.getString(R.string.dev_label_no_data)
        val id = AppPreferences.geofenceRegistrationReminder(context)
        val at = AppPreferences.geofenceRegistrationTime(context)
        val time = if (at > 0L) SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(at)) else "-"
        return "$result · #$id · $time"
    }

    /** 前台定位服务 / 自适应状态机的实时状态（全部来自真实运行状态）。 */
    fun locationServiceSection(context: Context): String {
        val status = AlarmLocationRuntime.status.value
        val noData = context.getString(R.string.dev_label_no_data)
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        fun age(nanos: Long): String =
            if (nanos <= 0L) noData else "%.1f s".format((nowNanos - nanos) / 1_000_000_000.0)
        val availability = when (status.availability) {
            true -> context.getString(R.string.dev_value_available)
            false -> context.getString(R.string.dev_value_unavailable)
            null -> context.getString(R.string.dev_value_unknown)
        }
        return buildString {
            appendLine(line(context.getString(R.string.dev_label_provider), status.providerName.ifBlank { noData }))
            appendLine(
                line(
                    context.getString(R.string.dev_label_service),
                    context.getString(if (status.running) R.string.dev_value_running else R.string.dev_value_stopped)
                )
            )
            appendLine(line(context.getString(R.string.dev_label_mode), status.mode.name))
            appendLine(line(context.getString(R.string.dev_label_requested_interval), "${status.requestedIntervalMillis} ms"))
            appendLine(line(context.getString(R.string.dev_label_last_callback), age(status.lastCallbackElapsedRealtimeNanos)))
            appendLine(line(context.getString(R.string.dev_label_accuracy), "${"%.0f".format(status.accuracyMeters)} m"))
            appendLine(line(context.getString(R.string.dev_label_distance), status.distanceMeters?.let { "%.0f m".format(it) } ?: noData))
            appendLine(line(context.getString(R.string.dev_label_closing_speed), "%.2f m/s".format(status.closingSpeedMps)))
            appendLine(line(context.getString(R.string.dev_label_eta), status.etaSeconds?.let { "%.0f s".format(it) } ?: noData))
            appendLine(line(context.getString(R.string.dev_label_radius), "${"%.0f".format(status.radiusMeters)} m"))
            appendLine(line(context.getString(R.string.dev_label_trigger), status.triggerState.name))
            appendLine(line(context.getString(R.string.dev_label_availability), availability))
        }.trim()
    }

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
        val noData = context.getString(R.string.dev_label_no_data)
        val lastTime = AppPreferences.geofenceLastTime(context)
        val lastReminder = AppPreferences.geofenceLastReminder(context)
        appendLine(
            line(
                context.getString(R.string.dev_label_last_event),
                AppPreferences.geofenceLastTransition(context) ?: noData
            )
        )
        appendLine(
            line(
                context.getString(R.string.dev_label_last_event_time),
                if (lastTime > 0L) {
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastTime))
                } else {
                    noData
                }
            )
        )
        appendLine(
            line(
                context.getString(R.string.dev_label_last_reminder),
                if (lastReminder > 0L) "#$lastReminder" else noData
            )
        )
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
        userLocation: Location?,
        locationAvailable: Boolean? = null
    ): String {
        val activeCount = reminders.count { it.enabled && !it.triggered }
        return buildString {
            appendLine("=== ANENG SPACE ALARM DEBUG ===")
            appendLine()
            appendLine("[App]")
            appendLine(appSection(context))
            appendLine()
            appendLine("[Permissions]")
            appendLine(permissionSection(context))
            appendLine()
            appendLine("[Runtime compat]")
            appendLine(compatSection(context))
            appendLine()
            appendLine("[Location]")
            appendLine(locationSection(context, userLocation, locationAvailable))
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
