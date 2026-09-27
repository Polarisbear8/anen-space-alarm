package com.anen.spacealarm.location

import android.content.Context
import com.anen.spacealarm.preferences.AppPreferences
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * 运行时选择定位实现：GMS 可用用 Fused，否则用 framework LocationManager。
 * 开发者模式可强制选择 Native（测试辅助）。
 */
object LocationProviders {

    fun create(context: Context): LocationProvider {
        val appContext = context.applicationContext
        if (AppPreferences.forceNativeProvider(appContext)) return NativeLocationProvider(appContext)
        return if (isGmsAvailable(appContext)) FusedLocationProvider(appContext) else NativeLocationProvider(appContext)
    }

    fun isGmsAvailable(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}
