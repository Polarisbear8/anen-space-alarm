package com.anen.spacealarm.alert

import android.content.Context
import android.util.Log
import com.anen.spacealarm.model.AlertMode
import com.anen.spacealarm.model.Reminder

/**
 * 统一提醒出口：Geofence 只调用这里，不关心提醒方式。
 *
 * 三种模式：
 *   NOTIFICATION 仅通知（静默渠道，不震动）
 *   VIBRATION    通知 + 震动（震动渠道 + 一次性震动）
 *   ALARM        通知 + 震动 + 闹铃（短时前台服务，全屏通知）
 *
 * ALARM 直接启动短时闹钟服务，不经过 AlarmManager：
 * 系统文档明确把“地理围栏事件”列为后台启动前台服务的豁免场景。
 */
object AlertManager {

    private const val TAG = "AnenAlert"

    /**
     * 执行提醒。
     *
     * @return 用户是否真正收到了提醒。返回 false 表示什么都没送达
     *         （例如通知权限关闭、且闹钟服务也无法启动），
     *         调用方应回滚触发状态，避免“系统以为提醒了，用户什么都没收到”。
     *
     * 日志时间戳用于拆分 Geofence 事件 → Alert 送达之间的延迟：
     * ALERT START = 收到事件开始处理，ALERT DELIVERED = 通知/闹钟已投递。
     */
    fun trigger(context: Context, reminder: Reminder): Boolean {
        Log.d(TAG, "ALERT START startedAt=${System.currentTimeMillis()} reminderId=${reminder.id}")
        val delivered = when (reminder.alertMode) {
            AlertMode.NOTIFICATION -> NotificationHelper.showSpaceAlert(context, reminder, vibrate = false)

            AlertMode.VIBRATION -> {
                val notified = NotificationHelper.showSpaceAlert(context, reminder, vibrate = true)
                NotificationHelper.vibrateOnce(context)
                notified
            }

            AlertMode.ALARM -> {
                val started = AlarmService.start(
                    context,
                    reminder.id,
                    reminder.placeName,
                    reminder.message,
                    reminder.radiusMeters
                )
                started || NotificationHelper.showAlarmFallback(context, reminder.placeName, reminder.radiusMeters)
            }
        }
        Log.d(TAG, "ALERT DELIVERED deliveredAt=${System.currentTimeMillis()} reminderId=${reminder.id} delivered=$delivered")
        return delivered
    }
}
