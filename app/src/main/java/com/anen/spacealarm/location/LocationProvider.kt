package com.anen.spacealarm.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * 定位入口。只提供两种使用方式：
 *  - 一次性当前位置（用户主动点击，使用高精度）；
 *  - 页面可见期间的更新流（地图打开时，默认低功耗，随生命周期停止）。
 * 不存在后台轮询。
 */
class LocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    fun hasPermission(): Boolean = hasLocationPermission(appContext)

    /** 用户主动触发的一次性定位，使用高精度。 */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        if (!hasPermission()) return null
        return try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                ?: client.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }

    /** 最近一次已知位置，仅用于计算触发时的参考距离，不会主动定位。 */
    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): Location? {
        if (!hasPermission()) return null
        return try {
            client.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 页面可见期间的更新流。
     * 地图需要秒级刷新时用高精度短间隔；页面离开后 Flow 取消，不会在后台轮询。
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(
        intervalMillis: Long = 15_000L,
        highAccuracy: Boolean = false
    ): Flow<Location> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val priority = if (highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = LocationRequest.Builder(priority, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper()).await()
        } catch (e: Exception) {
            close(e)
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    companion object {
        /** 地图显示等普通用途：粗略或精确定位都可。围栏注册要求精确位置，见 PermissionManager。 */
        fun hasLocationPermission(context: Context): Boolean =
            com.anen.spacealarm.permission.PermissionManager.hasAnyLocation(context)
    }
}
