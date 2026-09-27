package com.anen.spacealarm.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Android framework LocationManager 实现，用于没有 GMS 的机型。
 *
 * 这是兜底路径：不依赖 Google Play services。GMS 可用时不会走到这里。
 */
class NativeLocationProvider(context: Context) : LocationProvider {

    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _availability = MutableStateFlow<Boolean?>(null)
    override val availability: StateFlow<Boolean?> = _availability.asStateFlow()

    override val name: String = "Native LocationManager"

    override fun hasPermission(): Boolean =
        com.anen.spacealarm.permission.PermissionManager.hasAnyLocation(appContext)

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): Location? {
        if (!hasPermission()) return null
        // API < 30 没有 framework 的一次性定位 API；不返回缓存冒充 fresh。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val provider = providerFor(PRIORITY_HIGH_ACCURACY) ?: return null
        return suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            try {
                manager.getCurrentLocation(provider, signal, appContext.mainExecutor) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            } catch (e: Exception) {
                Log.w(TAG, "native currentLocation failed", e)
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnownLocation(): Location? {
        if (!hasPermission()) return null
        return try {
            manager.getLastKnownLocation(providerFor(PRIORITY_HIGH_ACCURACY) ?: return null)
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
        val provider = providerFor(spec.priority)
        if (provider == null) {
            Log.w(TAG, "no enabled location provider")
            _availability.value = false
            close()
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                _availability.value = true
                trySend(location)
            }

            override fun onProviderEnabled(provider: String) {
                _availability.value = true
            }

            override fun onProviderDisabled(provider: String) {
                _availability.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        Log.d(TAG, "request start provider=$provider interval=${spec.intervalMillis} at=${System.currentTimeMillis()}")
        try {
            manager.requestLocationUpdates(
                provider,
                spec.intervalMillis,
                spec.minUpdateDistanceMeters,
                listener,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            close(e)
        }
        awaitClose {
            manager.removeUpdates(listener)
            Log.d(TAG, "request stop at=${System.currentTimeMillis()}")
        }
    }

    private fun providerFor(priority: Int): String? {
        val highAccuracy = priority >= PRIORITY_HIGH_ACCURACY
        val gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return when {
            highAccuracy && gpsEnabled -> LocationManager.GPS_PROVIDER
            networkEnabled -> LocationManager.NETWORK_PROVIDER
            gpsEnabled -> LocationManager.GPS_PROVIDER
            else -> null
        }
    }

    companion object {
        private const val TAG = "AnenNativeLocation"

        /** 与 Google `Priority.PRIORITY_HIGH_ACCURACY` 相同的数值，避免依赖 GMS 常量。 */
        private const val PRIORITY_HIGH_ACCURACY = 100
    }
}
