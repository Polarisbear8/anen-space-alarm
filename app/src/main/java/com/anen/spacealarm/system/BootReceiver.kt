package com.anen.spacealarm.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.anen.spacealarm.AppContainer
import com.anen.spacealarm.permission.PermissionManager
import com.anen.spacealarm.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手机重启 / App 更新后恢复围栏。只恢复 enabled = true 且尚未触发的提醒，
 * 不会无条件恢复所有旧数据。
 *
 * 每条围栏单独 try/catch，统计成功 / 失败数量并写入诊断，便于在
 * OEM 拦截 BOOT_COMPLETED 或个别围栏注册失败时定位问题。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val startedAt = System.currentTimeMillis()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var restored = 0
            var failed = 0
            var total = 0
            try {
                // 记录本次开机时长：用于之后判断是否漏收了重启广播。
                AppPreferences.setLastHandledBootElapsedRealtime(appContext, SystemClock.elapsedRealtime())
                AppPreferences.setBootBroadcastMissing(appContext, false)

                if (!PermissionManager.state(appContext).geofenceReady) {
                    val result = "action=$action skipped=permissions at=$startedAt"
                    AppPreferences.setLastBootResult(appContext, result)
                    Log.w(TAG, "boot restore skipped: permissions unavailable $result")
                    return@launch
                }
                val repository = AppContainer.reminderRepository(appContext)
                val engine = AppContainer.geofenceEngine(appContext)
                val active = repository.getActive()
                total = active.size
                active.forEach { reminder ->
                    try {
                        engine.add(reminder)
                        restored++
                    } catch (e: Exception) {
                        failed++
                        Log.w(TAG, "restore failed id=${reminder.id} at=${System.currentTimeMillis()}", e)
                    }
                }
                val result = "action=$action restored=$restored failed=$failed total=$total at=$startedAt"
                AppPreferences.setLastBootResult(appContext, result)
                Log.i(TAG, "boot restore $result")
            } catch (e: Exception) {
                failed++
                val result = "action=$action restored=$restored failed=$failed total=$total error=${e.message} at=$startedAt"
                AppPreferences.setLastBootResult(appContext, result)
                Log.e(TAG, "boot restore failed $result", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AnenBoot"

        /**
         * App 每次进入前台时调用：elapsedRealtime 每次开机归零，若当前值小于上次处理的
         * 开机时长，说明设备重启过但 App 没收到 BOOT_COMPLETED（部分 OEM 会拦截）。
         * 只记录诊断，不自动补偿。
         */
        fun noteAppStarted(context: Context) {
            val stored = AppPreferences.lastHandledBootElapsedRealtime(context)
            val now = SystemClock.elapsedRealtime()
            if (stored > 0L && now < stored) {
                AppPreferences.setBootBroadcastMissing(context, true)
                Log.w(TAG, "boot broadcast missing: storedElapsed=$stored nowElapsed=$now at=${System.currentTimeMillis()}")
            }
        }
    }
}
