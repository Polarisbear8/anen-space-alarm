package com.anen.spacealarm.location

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TriggerState { IDLE, ARMED, TRIGGERED }

/**
 * 前台定位服务对外发布的状态。UI 只观察这里，不直接控制服务的生命周期。
 * 进程内单例（服务与 UI 同进程）。
 */
data class LocationServiceStatus(
    val running: Boolean = false,
    val providerName: String = "",
    val mode: AdaptiveMode = AdaptiveMode.IDLE,
    val requestedIntervalMillis: Long = 0L,
    val lastCallbackElapsedRealtimeNanos: Long = 0L,
    val lastFreshElapsedRealtimeNanos: Long = 0L,
    val accuracyMeters: Float = 0f,
    val distanceMeters: Double? = null,
    val closestReminderId: Long? = null,
    val radiusMeters: Float = 0f,
    val closingSpeedMps: Double = 0.0,
    val etaSeconds: Double? = null,
    val triggerState: TriggerState = TriggerState.IDLE,
    val availability: Boolean? = null,
    val latestLocation: Location? = null
)

object AlarmLocationRuntime {

    private val _status = MutableStateFlow(LocationServiceStatus())
    val status: StateFlow<LocationServiceStatus> = _status.asStateFlow()

    fun update(transform: (LocationServiceStatus) -> LocationServiceStatus) {
        _status.update(transform)
    }

    fun reset() {
        _status.value = LocationServiceStatus()
    }
}
