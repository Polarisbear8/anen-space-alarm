package com.anen.spacealarm.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Google Play services FusedLocationProvider 实现。需要设备具备 GMS。
 */
class FusedLocationProvider(context: Context) : LocationProvider {

    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    private val _availability = MutableStateFlow<Boolean?>(null)
    override val availability: StateFlow<Boolean?> = _availability.asStateFlow()

    override val name: String = "GMS Fused"

    override fun hasPermission(): Boolean = hasLocationPermission(appContext)

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): Location? {
        if (!hasPermission()) return null
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            // fresh-only：不接受历史缓存位置。
            .setMaxUpdateAgeMillis(0L)
            .setDurationMillis(FRESH_LOCATION_TIMEOUT_MILLIS)
            .build()
        val cts = CancellationTokenSource()
        return try {
            val location = client.getCurrentLocation(request, cts.token).await()
            Log.d(
                TAG,
                "currentLocation result=" + (
                    location?.let {
                        "lat=${it.latitude} lon=${it.longitude} elapsedRealtimeNanos=${it.elapsedRealtimeNanos}"
                    } ?: "null"
                    ) + " at=${System.currentTimeMillis()}"
            )
            location
        } catch (e: Exception) {
            Log.w(TAG, "fresh current location failed at=${System.currentTimeMillis()}", e)
            null
        } finally {
            cts.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnownLocation(): Location? {
        if (!hasPermission()) return null
        return try {
            client.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(spec: LocationRequestSpec): Flow<Location> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(spec.priority, spec.intervalMillis)
            .setMinUpdateIntervalMillis(spec.fastestIntervalMillis)
            .setMinUpdateDistanceMeters(spec.minUpdateDistanceMeters)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setMaxUpdateAgeMillis(spec.maxUpdateAgeMillis)
            .setWaitForAccurateLocation(spec.waitForAccurateLocation)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val latest = result.locations.maxByOrNull { it.elapsedRealtimeNanos }
                latest?.let { trySend(it) }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                _availability.value = availability.isLocationAvailable
                Log.d(
                    TAG,
                    "location availability=${availability.isLocationAvailable} at=${System.currentTimeMillis()}"
                )
            }
        }
        Log.d(
            TAG,
            "request start interval=${spec.intervalMillis} priority=${spec.priority} at=${System.currentTimeMillis()}"
        )
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper()).await()
        } catch (e: Exception) {
            close(e)
        }
        awaitClose {
            client.removeLocationUpdates(callback)
            Log.d(TAG, "request stop at=${System.currentTimeMillis()}")
        }
    }

    companion object {
        private const val TAG = "AnenLocation"
        private const val FRESH_LOCATION_TIMEOUT_MILLIS = 15_000L

        fun hasLocationPermission(context: Context): Boolean =
            com.anen.spacealarm.permission.PermissionManager.hasAnyLocation(context)
    }
}
