package com.anen.spacealarm.location

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.anen.spacealarm.AppContainer
import com.anen.spacealarm.alert.NotificationHelper
import com.anen.spacealarm.alert.ReminderTrigger
import com.anen.spacealarm.model.Reminder
import com.anen.spacealarm.permission.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * 空间闹钟的前台定位服务。
 *
 * 用户点击“启动闹钟”（仍在可见界面）时启动：
 *   - 以 location 前台服务类型持续获得位置，锁屏 / 退到后台仍继续
 *   - 本地计算到最近目标的距离，按 [AdaptiveStateMachine] 自适应调整频率
 *   - distance <= reminderRadius 时立刻触发提醒，然后停止服务
 *
 * 不再依赖 repeatOnLifecycle(RESUMED) 维持闹钟运行；Geofence 只作为低功耗兜底。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var locationJob: Job? = null
    private var reminderJob: Job? = null
    private val tuning = AdaptiveTuning()

    /** 模式 + 由 ETA 收紧后的 interval；任一变化才重建定位 request。 */
    private data class AdaptiveRequest(
        val mode: AdaptiveMode,
        val intervalMillis: Long,
        val priority: Int,
        val minUpdateDistanceMeters: Float
    )

    private val requestFlow = MutableStateFlow(buildRequest(AdaptiveMode.FAR, null))

    @Volatile
    private var reminders: List<Reminder> = emptyList()

    private var latestLocation: Location? = null
    private var previousDistance: Double? = null
    private var previousSpeed = 0.0
    private var previousSampleNanos = 0L
    private val inside = mutableMapOf<Long, Boolean>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            requestStop()
            return START_NOT_STICKY
        }
        startAlarm()
        return START_STICKY
    }

    private fun startAlarm() {
        if (!PermissionManager.state(this).fineLocation) {
            Log.w(TAG, "cannot start: fine location missing")
            requestStop()
            return
        }
        startForegroundCompat(NotificationHelper.buildLocationServiceNotification(this, "STARTING"))
        requestFlow.value = buildRequest(AdaptiveMode.FAR, null)
        AlarmLocationRuntime.update {
            it.copy(running = true, mode = requestFlow.value.mode, triggerState = TriggerState.ARMED)
        }
        if (locationJob?.isActive == true) {
            Log.d(TAG, "already running")
            return
        }
        val provider = AppContainer.locationProvider(this)
        val repository = AppContainer.reminderRepository(this)
        Log.d(TAG, "service START provider=${provider.name} at=${System.currentTimeMillis()}")

        reminderJob = scope.launch {
            repository.observeAll().collect { list ->
                reminders = list.filter { it.enabled && !it.triggered }
                if (reminders.isEmpty()) {
                    Log.d(TAG, "no active reminders, stopping at=${System.currentTimeMillis()}")
                    requestStop()
                }
            }
        }

        locationJob = scope.launch {
            AlarmLocationRuntime.update { it.copy(providerName = provider.name) }
            launch {
                provider.availability.collect { available ->
                    AlarmLocationRuntime.update { it.copy(availability = available) }
                }
            }
            // 立刻取一次 fresh fix，避免等服务周期
            provider.currentLocation()?.let { onLocation(it) }
            // 自适应频率：模式或 ETA 收紧后的 interval 变化时，切换到新的 request
            requestFlow.flatMapLatest { request ->
                AlarmLocationRuntime.update {
                    it.copy(mode = request.mode, requestedIntervalMillis = request.intervalMillis)
                }
                Log.d(
                    TAG,
                    "LOCATION MODE ${request.mode.name} interval=${request.intervalMillis} priority=${request.priority}"
                )
                updateNotification(request.mode)
                provider.locationUpdates(
                    LocationRequestSpec(
                        intervalMillis = request.intervalMillis,
                        priority = request.priority,
                        minUpdateDistanceMeters = request.minUpdateDistanceMeters
                    )
                )
            }.collect { onLocation(it) }
        }
    }

    private fun onLocation(location: Location) {
        val previous = latestLocation
        // freshness gate：旧位置绝不覆盖新位置
        if (previous != null && location.elapsedRealtimeNanos <= previous.elapsedRealtimeNanos) {
            return
        }
        latestLocation = location

        val active = reminders
        if (active.isEmpty()) {
            requestStop()
            return
        }
        val nearest = active.minByOrNull { distanceTo(location, it) } ?: return
        val distance = distanceTo(location, nearest)

        val dtSeconds = (location.elapsedRealtimeNanos - previousSampleNanos) / 1_000_000_000.0
        val previousDist = previousDistance
        if (previousDist != null && dtSeconds >= tuning.minSampleSeconds) {
            previousSpeed = AdaptiveStateMachine.smoothedClosingSpeed(
                previousSpeed, previousDist, distance, dtSeconds, tuning.speedSmoothing
            )
        }
        previousDistance = distance
        previousSampleNanos = location.elapsedRealtimeNanos
        val eta = AdaptiveStateMachine.etaSeconds(distance, nearest.radiusMeters.toDouble(), previousSpeed)

        val current = requestFlow.value
        val nextMode = AdaptiveStateMachine.next(current.mode, distance, eta, tuning)
        val desired = buildRequest(nextMode, eta)
        // interval 有实质变化才重建 request，避免频繁重排。
        if (desired.mode != current.mode ||
            kotlin.math.abs(desired.intervalMillis - current.intervalMillis) >= REQUEST_UPDATE_THRESHOLD_MS
        ) {
            requestFlow.value = desired
        }

        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
        Log.d(
            TAG,
            "LOCATION CALLBACK lat=${location.latitude} lon=${location.longitude} " +
                "accuracy=${location.accuracy} provider=${location.provider} " +
                "elapsedRealtimeNanos=${location.elapsedRealtimeNanos} ageMs=$ageMs " +
                "interval=${AlarmLocationRuntime.status.value.requestedIntervalMillis} " +
                "distance=${"%.1f".format(distance)} " +
                "closing=${"%.2f".format(previousSpeed)} eta=${eta?.let { "%.0f".format(it) } ?: "-"} mode=${requestFlow.value.mode}"
        )

        AlarmLocationRuntime.update {
            it.copy(
                lastCallbackElapsedRealtimeNanos = location.elapsedRealtimeNanos,
                accuracyMeters = location.accuracy,
                distanceMeters = distance,
                closestReminderId = nearest.id,
                radiusMeters = nearest.radiusMeters,
                closingSpeedMps = previousSpeed,
                etaSeconds = eta,
                triggerState = TriggerState.ARMED,
                latestLocation = location
            )
        }

        // 阈值触发：只在“外→内”跃迁时触发，避免半径内重复提醒
        for (reminder in active) {
            val d = distanceTo(location, reminder)
            val exitThreshold = reminder.radiusMeters + maxOf(
                tuning.insideExitMarginMeters,
                reminder.radiusMeters * tuning.insideExitRadiusFraction
            )
            val wasInside = inside[reminder.id] == true
            when {
                d <= reminder.radiusMeters -> if (!wasInside) {
                    inside[reminder.id] = true
                    Log.d(TAG, "ALARM TRIGGER reminderId=${reminder.id} distance=${"%.1f".format(d)} radius=${reminder.radiusMeters}")
                    scope.launch {
                        if (ReminderTrigger.claimAndDeliver(this@LocationForegroundService, reminder, source = "fgs")) {
                            AlarmLocationRuntime.update { it.copy(triggerState = TriggerState.TRIGGERED) }
                        }
                    }
                }
                d >= exitThreshold -> inside[reminder.id] = false
            }
        }
    }

    private fun buildRequest(mode: AdaptiveMode, etaSeconds: Double?): AdaptiveRequest = AdaptiveRequest(
        mode = mode,
        intervalMillis = AdaptiveStateMachine.intervalMillis(mode, etaSeconds, tuning),
        priority = AdaptiveStateMachine.priority(mode, tuning),
        minUpdateDistanceMeters = AdaptiveStateMachine.minUpdateDistanceMeters(mode, tuning)
    )

    private fun distanceTo(location: Location, reminder: Reminder): Double =
        DistanceCalculator.calculateDistanceMeters(
            location.latitude,
            location.longitude,
            reminder.latitude,
            reminder.longitude
        )

    private fun updateNotification(mode: AdaptiveMode) {
        val status = AlarmLocationRuntime.status.value
        val text = buildString {
            append(mode.name)
            status.distanceMeters?.let { append(" · %.0f m".format(it)) }
        }
        NotificationHelper.updateLocationServiceNotification(this, text)
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.LOCATION_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NotificationHelper.LOCATION_NOTIFICATION_ID, notification)
        }
    }

    private fun requestStop() {
        Log.d(TAG, "service STOP at=${System.currentTimeMillis()}")
        NotificationHelper.cancel(this, NotificationHelper.LOCATION_NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationJob?.cancel()
        reminderJob?.cancel()
        locationJob = null
        reminderJob = null
        inside.clear()
        latestLocation = null
        previousDistance = null
        previousSpeed = 0.0
        previousSampleNanos = 0L
        requestFlow.value = buildRequest(AdaptiveMode.FAR, null)
        AlarmLocationRuntime.reset()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AnenLocationService"
        /** interval 变化超过该值才重建 request。 */
        private const val REQUEST_UPDATE_THRESHOLD_MS = 1_500L
        const val ACTION_START = "com.anen.spacealarm.action.START_LOCATION"
        const val ACTION_STOP = "com.anen.spacealarm.action.STOP_LOCATION"

        /** 必须在 App 可见时调用（Android 14+ 后台启动 location FGS 受限）。 */
        fun start(context: Context) {
            if (!PermissionManager.state(context).fineLocation) {
                Log.w(TAG, "start requested without fine location")
                return
            }
            val intent = Intent(context, LocationForegroundService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "cannot start location foreground service", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, LocationForegroundService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "cannot stop location foreground service", e)
            }
        }
    }
}
