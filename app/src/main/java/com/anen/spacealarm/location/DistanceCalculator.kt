package com.anen.spacealarm.location

import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 纯函数直线距离计算（Haversine）。不使用任何地图 API。
 */
object DistanceCalculator {

    private const val EARTH_RADIUS_METERS = 6371008.8

    /** 两点直线距离（米）。 */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(lon2 - lon1)
        val sinDPhi = sin(dPhi / 2.0)
        val sinDLambda = sin(dLambda / 2.0)
        val a = sinDPhi * sinDPhi + cos(phi1) * cos(phi2) * sinDLambda * sinDLambda
        return 2.0 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    /** 以给定方位角前进指定距离后的坐标，用于绘制触发范围圆。 */
    fun offsetPoint(latitude: Double, longitude: Double, bearingDegrees: Double, distanceMeters: Double): DoubleArray {
        val delta = distanceMeters / EARTH_RADIUS_METERS
        val theta = Math.toRadians(bearingDegrees)
        val phi1 = Math.toRadians(latitude)
        val lambda1 = Math.toRadians(longitude)
        val sinPhi2 = sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta)
        val phi2 = asin(min(1.0, sinPhi2))
        val lambda2 = lambda1 + atan2(sin(theta) * sin(delta) * cos(phi1), cos(delta) - sin(phi1) * sinPhi2)
        return doubleArrayOf(Math.toDegrees(phi2), Math.toDegrees(lambda2))
    }

    /** 触发范围圆的边界点（首尾不重复）。 */
    fun circlePolygon(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        segments: Int = 64
    ): List<DoubleArray> = (0 until segments).map { i ->
        offsetPoint(latitude, longitude, i * 360.0 / segments, radiusMeters)
    }

    /** UI 显示：1.24 KM / 500 M。 */
    fun formatDistance(meters: Double): String = when {
        meters.isNaN() || meters < 0 -> "--"
        meters >= 1000.0 -> String.format(Locale.US, "%.2f KM", meters / 1000.0)
        else -> String.format(Locale.US, "%.0f M", meters)
    }

    /** UI 显示：500 M / 1.00 KM / 2.00 KM。 */
    fun formatRadius(meters: Float): String = formatDistance(meters.toDouble())
}
