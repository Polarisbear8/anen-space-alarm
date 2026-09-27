package com.anen.spacealarm.debug

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * 运行时环境快照，供开发者模式展示。
 *
 * 全部字段都是运行时检测，不根据厂商名推断能力：
 * GMS 是否可用一律走 [GoogleApiAvailability]，不因设备品牌下结论。
 */
data class RuntimeCompat(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val gmsAvailable: Boolean,
    val gmsVersion: String?,
    val locationServicesEnabled: Boolean,
    /** true = 已加入电池优化白名单（不受省电限制）。 */
    val batteryOptimizationExempt: Boolean,
    /** 应用待机分组（API 28+）：10=active 20=working 30=frequent 40=rare 45=restricted。 */
    val standbyBucket: Int?,
    /** ActivityManager 进程重要性；数值越小越接近前台。 */
    val processImportance: Int
)

object DeviceCompat {

    const val GMS_PACKAGE = "com.google.android.gms"

    fun snapshot(context: Context): RuntimeCompat = RuntimeCompat(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidRelease = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        gmsAvailable = isGmsAvailable(context),
        gmsVersion = gmsVersion(context),
        locationServicesEnabled = com.anen.spacealarm.permission.PermissionManager.isLocationEnabled(context),
        batteryOptimizationExempt = isBatteryOptimizationExempt(context),
        standbyBucket = standbyBucket(context),
        processImportance = processImportance()
    )

    fun isGmsAvailable(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    private fun gmsVersion(context: Context): String? = try {
        context.packageManager.getPackageInfo(GMS_PACKAGE, 0).versionName
    } catch (e: Exception) {
        null
    }

    fun isBatteryOptimizationExempt(context: Context): Boolean {
        val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return manager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun standbyBucket(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        return manager.appStandbyBucket
    }

    fun processImportance(): Int {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance
    }

    /** 进程重要性 -> 可读名称，用于诊断日志与开发者模式。 */
    fun importanceName(importance: Int): String = when (importance) {
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "FOREGROUND_SERVICE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "SERVICE"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "CACHED"
        ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY -> "EMPTY"
        else -> "OTHER($importance)"
    }

    fun bucketName(bucket: Int?): String = when (bucket) {
        null -> "n/a"
        10 -> "active"
        20 -> "working"
        30 -> "frequent"
        40 -> "rare"
        45 -> "restricted"
        else -> "bucket($bucket)"
    }
}
