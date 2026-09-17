package com.anen.spacealarm.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.anen.spacealarm.model.Reminder
import com.anen.spacealarm.permission.PermissionManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Geofence
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
        if (!PermissionManager.hasFineLocation(context)) {
            throw SecurityException("需要精确位置权限，无法注册地理围栏")
        }
        val request = buildRequest(reminder)
        try {
            client.addGeofences(request, geofencePendingIntent()).await()
        } catch (e: SecurityException) {
            Log.w(TAG, "addGeofences denied by system", e)
            throw e
        }
        Log.d(TAG, "geofence added: id=${reminder.id} r=${reminder.radiusMeters}")
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
        private const val RESPONSIVENESS_MILLIS = 60_000
    }
}
