package com.anen.spacealarm.alert

import android.content.Context
import android.util.Log
import com.anen.spacealarm.AppContainer
import com.anen.spacealarm.model.Reminder

/**
 * 统一的提醒触发入口，供 Geofence 广播与前台定位服务共用。
 *
 * 通过 Room 的原子 claimTrigger 保证同一条提醒不会被触发两次
 * （Geofence 兜底与前台本地距离判断可能同时命中）。
 */
object ReminderTrigger {

    private const val TAG = "AnenTrigger"

    /** @return 是否真正抢占并送达（false 表示已被其它路径触发或投递失败）。 */
    suspend fun claimAndDeliver(context: Context, reminder: Reminder, source: String): Boolean {
        val repository = AppContainer.reminderRepository(context)
        if (!repository.claimTrigger(reminder.id)) return false
        Log.d(TAG, "TRIGGER source=$source reminderId=${reminder.id} at=${System.currentTimeMillis()}")

        val delivered = try {
            AlertManager.trigger(context, reminder)
        } catch (e: Exception) {
            Log.e(TAG, "alert failed source=$source reminderId=${reminder.id}", e)
            false
        }

        when {
            !delivered -> {
                Log.w(TAG, "alert not delivered, restoring armed state: source=$source id=${reminder.id}")
                repository.setEnabled(reminder.id, true)
            }
            reminder.repeat -> {
                Log.d(TAG, "repeat reminder delivered, re-arming: source=$source id=${reminder.id}")
                repository.setEnabled(reminder.id, true)
            }
            else -> {
                Log.d(TAG, "one-shot reminder delivered, removing: source=$source id=${reminder.id}")
                AppContainer.geofenceEngine(context).remove(reminder.id)
                repository.delete(reminder)
            }
        }
        return delivered
    }
}
