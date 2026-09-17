package com.anen.spacealarm.ui

import android.content.Context
import com.anen.spacealarm.AppContainer
import com.anen.spacealarm.R
import com.anen.spacealarm.location.DistanceCalculator
import com.anen.spacealarm.permission.PermissionManager

/**
 * 权限 / 创建结果相关的用户文案。
 * 接收 Context 而不是用 stringResource，方便在协程与回调里调用。
 */

/** 围栏权限缺失时的统一用户提示；已就绪返回 null。 */
fun geofenceWarning(context: Context, state: PermissionManager.State): String? = when {
    !state.fineLocation -> context.getString(R.string.warning_need_precise)
    !state.backgroundLocation -> context.getString(R.string.warning_need_background)
    else -> null
}

/** 提醒方式 → 文案资源。 */
fun alertModeLabel(mode: com.anen.spacealarm.model.AlertMode): Int = when (mode) {
    com.anen.spacealarm.model.AlertMode.NOTIFICATION -> R.string.mode_notification
    com.anen.spacealarm.model.AlertMode.VIBRATION -> R.string.mode_vibration
    com.anen.spacealarm.model.AlertMode.ALARM -> R.string.mode_alarm
}

/** 创建提醒被拒绝时的用户提示（不写数据库、不注册围栏）。 */
fun armBlockedMessage(context: Context, outcome: AppContainer.ArmOutcome): String = when (outcome.result) {
    AppContainer.ArmResult.INSIDE_RANGE -> context.getString(
        R.string.arm_blocked_inside_range,
        outcome.distanceMeters?.let { DistanceCalculator.formatDistance(it) } ?: "--",
        DistanceCalculator.formatRadius(outcome.radiusMeters)
    )
    AppContainer.ArmResult.LOCATION_UNAVAILABLE -> context.getString(R.string.arm_blocked_no_location)
    AppContainer.ArmResult.PERMISSIONS_MISSING -> context.getString(R.string.arm_blocked_permissions)
    AppContainer.ArmResult.LIMIT_REACHED ->
        context.getString(R.string.arm_limit_reached, AppContainer.MAX_GEOFENCES)
    AppContainer.ArmResult.REGISTRATION_FAILED -> context.getString(R.string.arm_registration_failed)
    AppContainer.ArmResult.ARMED -> ""
}
