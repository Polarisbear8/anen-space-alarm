package com.anen.spacealarm.ui

import com.anen.spacealarm.coordinate.CoordinateConverter
import com.anen.spacealarm.model.AlertMode
import com.anen.spacealarm.model.CoordinateSystem
import com.anen.spacealarm.model.Reminder

/**
 * 彩蛋：在「关于」页连续点击版本号 3 次解锁。
 *
 * 地点来自高德分享短链 https://surl.amap.com/j6VvKEeV26A
 * （POI B0H25OO5O6「老水街米粉(天下桂林步行街店)」），分享数据是 GCJ02，
 * 入库前必须经 [CoordinateConverter] 换算为 WGS84，且只换算一次。
 *
 * 彩蛋以「停用」状态保存：不注册围栏、不触发提醒，由用户自行在闹钟列表里启用。
 */
object EasterEgg {

    const val TAPS_REQUIRED = 3
    const val PLACE_NAME = "老水街米粉(天下桂林步行街店)"
    const val ADDRESS = "天下桂林步行街"
    const val RADIUS_METERS = 100f

    private const val GCJ02_LATITUDE = 25.252691928063495
    private const val GCJ02_LONGITUDE = 110.20602464675902

    fun reminder(message: String): Reminder {
        val wgs84 = CoordinateConverter.toWgs84(GCJ02_LATITUDE, GCJ02_LONGITUDE, CoordinateSystem.GCJ02)
        return Reminder(
            placeName = PLACE_NAME,
            address = ADDRESS,
            latitude = wgs84[0],
            longitude = wgs84[1],
            radiusMeters = RADIUS_METERS,
            message = message,
            alertMode = AlertMode.ALARM,
            repeat = false,
            enabled = false
        )
    }
}
