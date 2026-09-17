package com.anen.spacealarm.model

/**
 * 提醒方式。
 *
 *  - NOTIFICATION 仅通知
 *  - VIBRATION    通知 + 震动（震动相当于闹铃）
 *  - ALARM        通知 + 震动 + 闹铃
 */
enum class AlertMode {
    NOTIFICATION,
    VIBRATION,
    ALARM
}
