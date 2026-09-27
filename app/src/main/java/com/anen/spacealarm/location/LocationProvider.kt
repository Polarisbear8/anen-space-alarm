package com.anen.spacealarm.location

import android.location.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 定位请求参数，屏蔽 GMS / Android framework 的差异。
 *
 * [priority] 使用 Google `Priority.*` 数值；Native 实现据此选择 provider。
 */
data class LocationRequestSpec(
    val intervalMillis: Long,
    val priority: Int,
    val fastestIntervalMillis: Long = intervalMillis / 2,
    val maxUpdateAgeMillis: Long = 0L,
    val waitForAccurateLocation: Boolean = false,
    /** 即使未到 interval，只要位移超过该距离也投递一次（运动触发，静止时省电）。 */
    val minUpdateDistanceMeters: Float = 0f
)

/**
 * 定位提供者抽象。
 *
 * 实现：
 *  - [FusedLocationProvider]：Google Play services FusedLocationProvider（需要 GMS）
 *  - [NativeLocationProvider]：Android framework LocationManager（无 GMS 兜底）
 *
 * 注意：FusedLocationProviderClient 属于 Google Play services，不是 Android Framework，
 * 因此不能声称它在无 GMS 机型上可用；由 [LocationProviders] 在运行时选择。
 */
interface LocationProvider {

    /** 人类可读的实现名，用于 Debug。 */
    val name: String

    /** 最近一次 location availability；null = 尚未收到任何回调。 */
    val availability: StateFlow<Boolean?>

    fun hasPermission(): Boolean

    /** fresh-only 一次性定位；失败返回 null，绝不回退到缓存位置。 */
    suspend fun currentLocation(): Location?

    /** 最近已知位置，仅供 UI 初始显示 / Debug，不用于距离或围栏判断。 */
    suspend fun lastKnownLocation(): Location?

    /** 持续定位流。调用方改变频率时取消旧流即可（Flow 会移除 request）。 */
    fun locationUpdates(spec: LocationRequestSpec): Flow<Location>
}

/**
 * 并行请求（持续更新 + 一次性定位）返回顺序不定：只允许时间戳更新的结果覆盖。
 * 纯函数，便于单元测试。
 */
internal fun isFresher(candidateElapsedRealtimeNanos: Long, currentElapsedRealtimeNanos: Long?): Boolean =
    currentElapsedRealtimeNanos == null || candidateElapsedRealtimeNanos > currentElapsedRealtimeNanos
