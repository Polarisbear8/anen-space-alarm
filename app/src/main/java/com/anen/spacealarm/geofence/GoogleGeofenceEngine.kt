package com.anen.spacealarm.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anen.spacealarm.model.Reminder
import com.anen.spacealarm.permission.PermissionManager
import com.anen.spacealarm.preferences.AppPreferences
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

/**
 * 基于系统 / Google Play services 的地理围栏实现。
 * App 不做任何主动定位，只负责注册围栏并等待系统回调。
 */
class GoogleGeofenceEngine(private val context: Context) : GeofenceEngine {

    private val client: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    override fun isAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    override suspend fun add(reminder: Reminder) {
        // Geofencing API 要求精确位置；Android 10+ 还必须有后台定位，否则 App 不在前台时收不到事件。
        if (!PermissionManager.hasFineLocation(context)) {
            recordRegistration(reminder.id, "ERROR:permission:fine")
            throw SecurityException("需要精确位置权限，无法注册地理围栏")
        }
        if (!PermissionManager.hasBackgroundLocation(context)) {
            recordRegistration(reminder.id, "ERROR:permission:background")
            throw SecurityException("需要后台位置权限，无法注册地理围栏")
        }
        // 定位总开关关闭时，注册可能成功但永远不会触发；记录以便区分环境问题。
        if (!PermissionManager.isLocationEnabled(context)) {
            Log.w(TAG, "location services disabled at add: id=${reminder.id} at=${System.currentTimeMillis()}")
        }
        val request = buildRequest(reminder)
        try {
            client.addGeofences(request, geofencePendingIntent()).await()
        } catch (e: ApiException) {
            val name = statusName(e.statusCode)
            recordRegistration(reminder.id, "ERROR:${e.statusCode}:$name")
            Log.w(TAG, "addGeofences failed: id=${reminder.id} statusCode=${e.statusCode} $name at=${System.currentTimeMillis()}", e)
            throw e
        } catch (e: SecurityException) {
            recordRegistration(reminder.id, "ERROR:security")
            Log.w(TAG, "addGeofences denied by system at=${System.currentTimeMillis()}", e)
            throw e
        }
        recordRegistration(reminder.id, "OK")
        Log.i(TAG, "geofence added: id=${reminder.id} r=${reminder.radiusMeters} requestId=${reminder.id} at=${System.currentTimeMillis()}")
    }

    private fun recordRegistration(reminderId: Long, result: String) {
        AppPreferences.setGeofenceRegistration(context, reminderId, result, System.currentTimeMillis())
    }

    private fun statusName(statusCode: Int): String = when (statusCode) {
        GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> "GEOFENCE_NOT_AVAILABLE"
        GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> "GEOFENCE_TOO_MANY_GEOFENCES"
        GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> "GEOFENCE_TOO_MANY_PENDING_INTENTS"
        else -> "STATUS($statusCode)"
    }

    override suspend fun update(reminder: Reminder) {
        // 相同 requestId 重新注册即为更新。
        add(reminder)
    }

    override suspend fun remove(reminderId: Long) {
        try {
            client.removeGeofences(listOf(reminderId.toString())).await()
            Log.d(TAG, "geofence removed: id=$reminderId")
        } catch (e: Exception) {
            Log.w(TAG, "geofence remove failed: id=$reminderId", e)
        }
    }

    override suspend fun removeAll() {
        try {
            client.removeGeofences(geofencePendingIntent()).await()
        } catch (e: Exception) {
            Log.w(TAG, "geofence removeAll failed", e)
        }
    }

    private fun buildRequest(reminder: Reminder): GeofencingRequest {
        val geofence = Geofence.Builder()
            .setRequestId(reminder.id.toString())
            .setCircularRegion(reminder.latitude, reminder.longitude, reminder.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setNotificationResponsiveness(RESPONSIVENESS_MILLIS)
            .build()
        return GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()
    }

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            .setAction(GeofenceEngine.ACTION_GEOFENCE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    companion object {
        private const val TAG = "AnenGeofence"
        private const val REQUEST_CODE = 100
        // 0 = 让系统尽快派发；实际触发仍受 Android / OEM 后台调度影响，不保证时延。
        // ponytail: 若实测耗电明显，再按需调回 60_000。
        private const val RESPONSIVENESS_MILLIS = 0
    }
}
