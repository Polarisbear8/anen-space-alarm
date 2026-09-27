package com.anen.spacealarm.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anen.spacealarm.AppContainer
import com.anen.spacealarm.alert.ReminderTrigger
import com.anen.spacealarm.preferences.AppPreferences
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 系统地理围栏事件入口。
 *
 * 只做短工作：原子抢占触发权 → AlertManager → 按“一次性 / 重复”处理。
 * 不做持续定位、不做网络长任务、不做循环。
 *
 * 触发后的状态机：
 *   提醒送达 + 一次性 → 删除提醒并移除围栏
 *   提醒送达 + 重复   → 重置为可再次触发（保留围栏）
 *   提醒失败         → 恢复 ARMED，等待下次围栏事件重试
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GeofenceEngine.ACTION_GEOFENCE) return
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val receivedAt = System.currentTimeMillis()
        val transition = event.geofenceTransition
        val rawIds = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        // 事件到达时间戳：用于与 ALERT START / DELIVERED 对比，拆分系统围栏延迟。
        Log.d(TAG, "GEOFENCE EVENT receivedAt=$receivedAt transition=$transition ids=$rawIds")
        AppPreferences.setGeofenceLastEvent(
            context.applicationContext,
            transitionName(transition),
            receivedAt,
            null
        )
        if (event.hasError()) {
            Log.w(TAG, "geofencing error: ${event.errorCode}")
            return
        }
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val ids = event.triggeringGeofences?.mapNotNull { it.requestId.toLongOrNull() }.orEmpty()
        if (ids.isEmpty()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        // 所有工作都必须在 goAsync() 生命周期内完成：
        // 用 withContext(Dispatchers.IO) 做 IO，不在广播里创建独立的长生命周期作用域。
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) { handleEnter(appContext, ids, transition, receivedAt) }
            } catch (e: Exception) {
                Log.e(TAG, "geofence handling failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleEnter(
        appContext: Context,
        ids: List<Long>,
        transition: Int,
        receivedAt: Long
    ) {
        val repository = AppContainer.reminderRepository(appContext)
        for (id in ids) {
            val reminder = repository.getById(id) ?: continue
            AppPreferences.setGeofenceLastEvent(appContext, transitionName(transition), receivedAt, id)
            Log.d(TAG, "GEOFENCE HANDLE reminderId=$id triggerReceivedAt=$receivedAt at=${System.currentTimeMillis()}")
            ReminderTrigger.claimAndDeliver(appContext, reminder, source = "geofence")
        }
    }

    companion object {
        private const val TAG = "AnenGeofenceRx"

        private fun transitionName(transition: Int): String = when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
            Geofence.GEOFENCE_TRANSITION_DWELL -> "DWELL"
            else -> "UNKNOWN($transition)"
        }
    }
}
