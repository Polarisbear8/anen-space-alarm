package com.anen.spacealarm.location

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * 运行时选择定位实现：GMS 可用用 Fused，否则用 framework LocationManager。
 */
object LocationProviders {

    fun create(context: Context): LocationProvider =
        if (isGmsAvailable(context)) FusedLocationProvider(context) else NativeLocationProvider(context)

    fun isGmsAvailable(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}
