package com.anen.spacealarm.alert

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anen.spacealarm.MainActivity
import com.anen.spacealarm.R
import com.anen.spacealarm.location.DistanceCalculator
import com.anen.spacealarm.model.Reminder

/**
 * 只负责通知渠道与通知内容。不负责 Geofence、数据库、定位。
 */
object NotificationHelper {

    const val CHANNEL_ALERT_SILENT = "space_alert_silent"
    const val CHANNEL_ALERT_VIBRATE = "space_alert_vibrate"
    const val CHANNEL_ALARM = "space_alarm"
    const val ALARM_NOTIFICATION_ID = 9001

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT_SILENT,
                context.getString(R.string.channel_alert_silent_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_alert_silent_desc)
                enableVibration(false)
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT_VIBRATE,
                context.getString(R.string.channel_alert_vibrate_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_alert_vibrate_desc)
                enableVibration(true)
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM,
                context.getString(R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_alarm_desc)
                enableVibration(true)
                setSound(null, null)
            }
        )
    }

    /** 通知 + 震动模式使用的一次性震动（不常驻、不循环）。 */
    fun vibrateOnce(context: Context) {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.os.Vibrator::class.java)
        } ?: return
        try {
            device.vibrate(android.os.VibrationEffect.createWaveform(VIBRATE_PATTERN, -1))
        } catch (e: Exception) {
            // 忽略：部分设备在静音/勿扰下会拒绝震动
        }
    }

    private val VIBRATE_PATTERN = longArrayOf(0, 600, 300, 600)

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * 提醒通知的稳定 32 位 ID：直接使用 Long 的 hashCode（高低 32 位异或），
     * 不再用 % 1000，避免 id 相差 1000 的提醒互相覆盖。
     */
    fun alertIdFor(reminderId: Long): Int = reminderId.hashCode()

    /**
     * 通知模式：进入范围后的一条普通通知。
     *
     * 文案使用“已进入 X 范围”而不是推算距离：Geofence 事件本身不携带触发时刻的
     * 实际位置，用系统缓存位置推算的“还有约 X”不可靠（见 AlertManager 说明）。
     *
     * @return 是否真正送达
     */
    fun showSpaceAlert(context: Context, reminder: Reminder, vibrate: Boolean): Boolean {
        if (!canNotify(context)) return false
        val rangeLine = context.getString(
            R.string.notif_range_entered,
            DistanceCalculator.formatRadius(reminder.radiusMeters)
        )
        val body = buildString {
            appendLine(reminder.placeName)
            appendLine(rangeLine)
            if (reminder.message.isNotBlank()) append(reminder.message)
        }.trim()
        val channel = if (vibrate) CHANNEL_ALERT_VIBRATE else CHANNEL_ALERT_SILENT
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText("${reminder.placeName} · $rangeLine")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(MainActivity.contentIntent(context, reminder.id))
            .build()
        return notifySafely(context, alertIdFor(reminder.id), notification)
    }

    /** 闹钟模式但无法响铃时的降级通知（例如系统拒绝后台启动服务）。 */
    fun showAlarmFallback(context: Context, placeName: String, radiusMeters: Float): Boolean {
        if (!canNotify(context)) return false
        val rangeLine = context.getString(
            R.string.notif_range_entered,
            DistanceCalculator.formatRadius(radiusMeters)
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(context.getString(R.string.notif_title_alarm))
            .setContentText("$placeName · $rangeLine")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$placeName\n$rangeLine"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        return notifySafely(context, ALARM_NOTIFICATION_ID, notification)
    }

    /** 闹钟前台服务使用的全屏通知。 */
    fun buildAlarmNotification(
        context: Context,
        reminderId: Long,
        placeName: String,
        message: String,
        radiusMeters: Float
    ): Notification {
        val rangeLine = if (radiusMeters > 0f) {
            context.getString(R.string.notif_range_entered, DistanceCalculator.formatRadius(radiusMeters))
        } else {
            context.getString(R.string.notif_range_entered_generic)
        }
        val body = listOf(placeName, rangeLine, message)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        val contentIntent = AlarmActivity.pendingIntent(context, reminderId, placeName, message, radiusMeters)
        val stopIntent = PendingIntent.getService(
            context,
            2,
            AlarmService.stopIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(context.getString(R.string.notif_title_alarm))
            .setContentText("$placeName · $rangeLine")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(contentIntent, true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.notif_dismiss), stopIntent)
            .build()
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    /** @return 是否成功送达（SecurityException 等视为失败，调用方据此决定是否回滚状态）。 */
    private fun notifySafely(context: Context, id: Int, notification: Notification): Boolean = try {
        NotificationManagerCompat.from(context).notify(id, notification)
        true
    } catch (e: SecurityException) {
        false
    }
}
