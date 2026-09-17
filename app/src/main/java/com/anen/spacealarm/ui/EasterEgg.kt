package com.anen.spacealarm.ui

import com.anen.spacealarm.coordinate.CoordinateConverter
import com.anen.spacealarm.model.AlertMode
import com.anen.spacealarm.model.CoordinateSystem
import com.anen.spacealarm.model.Reminder
import kotlin.math.abs

/**
 * 彩蛋：在「关于」页连续点击版本号 3 次解锁。
 *
 * 地点来自高德分享短链 https://surl.amap.com/j6VvKEeV26A（POI B0H25OO5O6），
 * 分享数据是 GCJ02，入库前必须经 [CoordinateConverter] 换算为 WGS84，且只换算一次。
 *
 * 地点名、地址、提醒内容都由调用方传入本地化文案（见 `easter_egg_*` 字符串），
 * 本对象只负责固定的坐标与提醒参数。
 *
 * 彩蛋以「停用」状态保存：不注册围栏、不触发提醒，由用户自行在闹钟列表里启用。
 */
object EasterEgg {

    const val TAPS_REQUIRED = 3
    const val RADIUS_METERS = 100f

    private const val GCJ02_LATITUDE = 25.252691928063495
    private const val GCJ02_LONGITUDE = 110.20602464675902

    /** 约 1 米的经纬度容差。 */
    private const val COORDINATE_EPSILON = 1e-5

    fun reminder(placeName: String, address: String, message: String): Reminder {
        val wgs84 = wgs84Coordinates()
        return Reminder(
            placeName = placeName,
            address = address,
            latitude = wgs84[0],
            longitude = wgs84[1],
            radiusMeters = RADIUS_METERS,
            message = message,
            alertMode = AlertMode.ALARM,
            repeat = false,
            enabled = false
        )
    }

    /** 彩蛋条目是否已存在。按坐标判断，切换语言后重复点击不会建出第二条。 */
    fun alreadyCreated(reminders: List<Reminder>): Boolean {
        val wgs84 = wgs84Coordinates()
        return reminders.any {
            abs(it.latitude - wgs84[0]) < COORDINATE_EPSILON &&
                abs(it.longitude - wgs84[1]) < COORDINATE_EPSILON
        }
    }

    private fun wgs84Coordinates(): DoubleArray =
        CoordinateConverter.toWgs84(GCJ02_LATITUDE, GCJ02_LONGITUDE, CoordinateSystem.GCJ02)
}
