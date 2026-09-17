package com.anen.spacealarm.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anen.spacealarm.AppContainer
import com.anen.spacealarm.permission.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手机重启后恢复围栏。只恢复 enabled = true 且尚未触发的提醒，
 * 不会无条件恢复所有旧数据。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!PermissionManager.state(appContext).geofenceReady) {
                    Log.w(TAG, "skipped restore: geofence permissions unavailable")
                    return@launch
                }
                val repository = AppContainer.reminderRepository(appContext)
                val engine = AppContainer.geofenceEngine(appContext)
                repository.getActive().forEach { reminder ->
                    try {
                        engine.add(reminder)
                    } catch (e: Exception) {
                        Log.w(TAG, "restore geofence failed: id=${reminder.id}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "boot restore failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AnenBoot"
    }
}
