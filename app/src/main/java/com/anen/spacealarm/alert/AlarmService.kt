package com.anen.spacealarm.alert

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 闹钟前台服务。只在真正触发 ALARM 后短时间运行：播放声音 + 振动 + 全屏通知。
 * 不是常驻服务，触发结束后立即停止。
 *
 * 启动来源是 Geofence 事件（系统豁免后台启动前台服务的场景），
 * 不经过 AlarmManager，也不依赖精确闹钟权限。
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var autoStopScheduled = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopAlarm() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }
        val id = intent?.getLongExtra(AlarmActivity.EXTRA_REMINDER_ID, -1L) ?: -1L
        if (id <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        val placeName = intent?.getStringExtra(AlarmActivity.EXTRA_PLACE_NAME).orEmpty()
        val message = intent?.getStringExtra(AlarmActivity.EXTRA_MESSAGE).orEmpty()
        val radius = intent?.getFloatExtra(AlarmActivity.EXTRA_RADIUS, 0f) ?: 0f

        startForeground(
            NotificationHelper.ALARM_NOTIFICATION_ID,
            NotificationHelper.buildAlarmNotification(this, id, placeName, message, radius)
        )
        startSound()
        startVibration()
        if (!autoStopScheduled) {
            autoStopScheduled = true
            mainHandler.postDelayed(autoStop, AUTO_STOP_MILLIS)
        }
        // 全屏呈现完全交给通知的 full-screen intent：锁屏/后台由系统拉起 AlarmActivity，
        // 前台退化为 heads-up。系统不允许全屏通知（Android 14+ 未授权）时，
        // 就只保留高优先级闹钟通知 + 声音 + 振动，不再从后台强行 startActivity。
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(autoStop)
        releaseSound()
        releaseVibration()
        super.onDestroy()
    }

    private fun startSound() {
        if (player != null) return
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "alarm sound failed", e)
            null
        }
    }

    private fun startVibration() {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        vibrator = device
        try {
            device.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
        } catch (e: Exception) {
            Log.w(TAG, "vibration failed", e)
        }
    }

    private fun stopAlarm() {
        releaseSound()
        releaseVibration()
        NotificationHelper.cancel(this, NotificationHelper.ALARM_NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseSound() {
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (e: Exception) {
                // 忽略停止异常
            }
            it.release()
        }
        player = null
    }

    private fun releaseVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    companion object {
        private const val TAG = "AnenAlarmService"
        private const val AUTO_STOP_MILLIS = 5 * 60 * 1000L
        private val VIBRATION_PATTERN = longArrayOf(0, 800, 500)
        const val ACTION_STOP = "com.anen.spacealarm.action.STOP_ALARM"

        fun intent(
            context: Context,
            reminderId: Long,
            placeName: String,
            message: String,
            radiusMeters: Float
        ): Intent = Intent(context, AlarmService::class.java)
            .putExtra(AlarmActivity.EXTRA_REMINDER_ID, reminderId)
            .putExtra(AlarmActivity.EXTRA_PLACE_NAME, placeName)
            .putExtra(AlarmActivity.EXTRA_MESSAGE, message)
            .putExtra(AlarmActivity.EXTRA_RADIUS, radiusMeters)

        fun stopIntent(context: Context): Intent =
            Intent(context, AlarmService::class.java).setAction(ACTION_STOP)

        fun start(
            context: Context,
            reminderId: Long,
            placeName: String,
            message: String,
            radiusMeters: Float
        ): Boolean = try {
            ContextCompat.startForegroundService(
                context, intent(context, reminderId, placeName, message, radiusMeters)
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "cannot start alarm service", e)
            false
        }

        fun stop(context: Context) {
            try {
                context.startService(stopIntent(context))
            } catch (e: Exception) {
                context.stopService(Intent(context, AlarmService::class.java))
            }
        }
    }
}
