package com.anen.spacealarm.location

/**
 * 一次定位结果。
 *
 * 使用独立值类型而不是 android.location.Location，便于纯 JVM 单元测试，
 * 也让定位实现与业务层解耦（以后更换定位实现不需要改业务代码）。
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f
)
