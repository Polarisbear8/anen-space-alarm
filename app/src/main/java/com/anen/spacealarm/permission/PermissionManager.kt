package com.anen.spacealarm.permission

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * 权限状态与申请入口。每一项权限都要能向用户解释用途，不一次性全部申请。
 *
 * 注意精确定位与粗略定位是分开的：Android 12+ 允许用户只授予“大致位置”，
 * 而 Geofencing API 要求 ACCESS_FINE_LOCATION，因此不能把“有任意定位权限”
 * 当作“围栏可用”。
 */
object PermissionManager {

    data class State(
        val fineLocation: Boolean,
        val coarseLocation: Boolean,
        val backgroundLocation: Boolean,
        val notifications: Boolean,
        val fullScreenIntent: Boolean,
        val sdkInt: Int = Build.VERSION.SDK_INT
    ) {
        /**
         * 围栏是否真正可用：必须精确定位（Geofencing API 要求 FINE），
         * 且 Android 10 (API 29) 起必须有后台定位，否则 App 不在前台时收不到事件。
         */
        val geofenceReady: Boolean
            get() = fineLocation && (sdkInt < Build.VERSION_CODES.Q || backgroundLocation)
    }

    fun state(context: Context): State = State(
        fineLocation = hasFineLocation(context),
        coarseLocation = hasCoarseLocation(context),
        backgroundLocation = hasBackgroundLocation(context),
        notifications = hasNotifications(context),
        fullScreenIntent = hasFullScreenIntent(context)
    )

    fun hasFineLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun hasCoarseLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /** 地图显示等普通用途：粗略定位也够用。 */
    fun hasAnyLocation(context: Context): Boolean =
        hasFineLocation(context) || hasCoarseLocation(context)

    fun hasBackgroundLocation(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return false
        }
        return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun hasFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canUseFullScreenIntent()
    }

    /** 同时申请 FINE + COARSE，让系统给出“精确/大致”选择。 */
    fun locationPermissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /** 后台定位必须在精确定位授予之后再单独申请。 */
    fun backgroundLocationPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            emptyArray()
        }

    fun notificationPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    /**
     * 后台定位权限在部分系统上无法通过弹窗直接授予，需要跳到应用详情页手动选择“始终允许”。
     */
    fun appDetailsSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))

    fun fullScreenIntentSettingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            .setData(Uri.fromParts("package", context.packageName, null))
    }

    /** 系统定位总开关是否打开（定位服务关闭时 Geofence 一定不工作）。 */
    fun isLocationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    /**
     * 是否属于 Honor / Huawei 家族。只用于决定展示哪一套“手动设置”指引，
     * 绝不据此推断 GMS 是否可用（GMS 一律走 GoogleApiAvailability 运行时检测）。
     */
    fun isHonorFamily(): Boolean {
        val name = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return name.contains("honor") || name.contains("huawei")
    }

    /** 电池优化设置列表页（无需权限，不强制加白，仅作为手动入口）。 */
    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /**
     * 打开厂商“应用启动管理”页面。
     *
     * 候选组件逐个用 [PackageManager.resolveActivity] 做运行时校验，只有真正可解析的才使用；
     * 任何一个都不可用时回退到应用详情页。绝不主动抛出 ActivityNotFoundException。
     */
    fun backgroundReliabilityIntent(context: Context): Intent =
        firstResolvable(context, OEM_LAUNCH_INTENTS) ?: appDetailsSettingsIntent(context)

    private fun firstResolvable(context: Context, candidates: List<Intent>): Intent? {
        val pm = context.packageManager
        for (candidate in candidates) {
            val intent = Intent(candidate).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) return intent
        }
        return null
    }

    /**
     * Honor / Huawei 家族的应用启动管理页候选。MagicOS 与 EMUI 的组件名属于未公开实现，
     * 因此这里只在运行时解析成功后才会真正跳转。
     */
    private val OEM_LAUNCH_INTENTS: List<Intent> = listOf(
        Intent().setComponent(
            ComponentName(
                "com.hihonor.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.hihonor.systemmanager",
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
        ),
        Intent().setComponent(
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
            )
        )
    )
}
