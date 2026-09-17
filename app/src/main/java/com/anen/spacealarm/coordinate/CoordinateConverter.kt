package com.anen.spacealarm.coordinate

import com.anen.spacealarm.model.CoordinateSystem
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 中国大陆坐标偏移换算（WGS84 / GCJ02 / BD09）。
 *
 * 只有在坐标确实属于对应坐标系时才允许调用；禁止对已经正确的坐标做重复转换。
 * 中国大陆以外不偏移，GCJ02/BD09 视为与 WGS84 相同。
 */
object CoordinateConverter {

    private const val PI = Math.PI
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private const val X_PI = PI * 3000.0 / 180.0

    fun isOutOfChina(latitude: Double, longitude: Double): Boolean =
        longitude < 72.004 || longitude > 137.8347 || latitude < 0.8293 || latitude > 55.8271

    /** 按声明的坐标系换算到内部统一使用的 WGS84。 */
    fun toWgs84(latitude: Double, longitude: Double, system: CoordinateSystem): DoubleArray =
        when (system) {
            CoordinateSystem.WGS84 -> doubleArrayOf(latitude, longitude)
            CoordinateSystem.GCJ02 -> gcj02ToWgs84(latitude, longitude)
            CoordinateSystem.BD09 -> {
                val gcj = bd09ToGcj02(latitude, longitude)
                gcj02ToWgs84(gcj[0], gcj[1])
            }
        }

    fun wgs84ToGcj02(latitude: Double, longitude: Double): DoubleArray {
        if (isOutOfChina(latitude, longitude)) return doubleArrayOf(latitude, longitude)
        var dLat = transformLatitude(longitude - 105.0, latitude - 35.0)
        var dLon = transformLongitude(longitude - 105.0, latitude - 35.0)
        val radLat = latitude / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return doubleArrayOf(latitude + dLat, longitude + dLon)
    }

    /**
     * 迭代逼近求逆，避免一次性近似带来的米级误差。
     */
    fun gcj02ToWgs84(latitude: Double, longitude: Double): DoubleArray {
        if (isOutOfChina(latitude, longitude)) return doubleArrayOf(latitude, longitude)
        var wgsLat = latitude
        var wgsLon = longitude
        repeat(4) {
            val gcj = wgs84ToGcj02(wgsLat, wgsLon)
            wgsLat += latitude - gcj[0]
            wgsLon += longitude - gcj[1]
        }
        return doubleArrayOf(wgsLat, wgsLon)
    }

    fun bd09ToGcj02(latitude: Double, longitude: Double): DoubleArray {
        val x = longitude - 0.0065
        val y = latitude - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = Math.atan2(y, x) - 0.000003 * cos(x * X_PI)
        return doubleArrayOf(z * sin(theta), z * cos(theta))
    }

    private fun transformLatitude(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLongitude(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
