package com.anen.spacealarm.model

/**
 * 空间提醒。坐标恒为 WGS84，Geofence 与地图都直接使用，不做二次转换。
 *
 * @param repeat true = 重复（每次进入范围都提醒）；false = 一次性（提醒后自动删除）
 */
data class Reminder(
    val id: Long = 0L,
    val placeName: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val message: String,
    val alertMode: AlertMode,
    val repeat: Boolean = false,
    val enabled: Boolean = true,
    val triggered: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val status: ReminderStatus
        get() = when {
            !enabled -> ReminderStatus.DISABLED
            triggered -> ReminderStatus.TRIGGERED
            else -> ReminderStatus.ARMED
        }
}

enum class ReminderStatus {
    DISABLED,
    ARMED,
    TRIGGERED
}
