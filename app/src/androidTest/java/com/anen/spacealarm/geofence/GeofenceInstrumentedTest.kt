package com.anen.spacealarm.geofence

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.anen.spacealarm.model.AlertMode
import com.anen.spacealarm.model.Reminder
import com.anen.spacealarm.permission.PermissionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机集成测试：在真实设备上注册/移除地理围栏。
 *
 * 需要在已授予“始终允许”定位权限的真机上运行；缺少权限时测试会跳过而不是失败。
 * 真正的“进入范围是否触发”仍需人工移动测试（见验收清单）。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class GeofenceInstrumentedTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Test
    fun addAndRemoveGeofenceSucceedsOnDevice() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val engine = GoogleGeofenceEngine(context)

            Log.i(TAG, "geofence engine available = ${engine.isAvailable()}")
            Log.i(TAG, "geofence ready = ${PermissionManager.state(context).geofenceReady}")
            assumeTrue("Google Play services 不可用，跳过", engine.isAvailable())
            assumeTrue("需要后台定位权限（始终允许），跳过", PermissionManager.state(context).geofenceReady)

            val reminder = Reminder(
                id = TEST_REMINDER_ID,
                placeName = "instrumented-geofence-test",
                address = null,
                latitude = 23.079347,
                longitude = 113.267724,
                radiusMeters = 200f,
                message = "test",
                alertMode = AlertMode.NOTIFICATION
            )

            engine.add(reminder)
            engine.remove(reminder.id)
            Log.i(TAG, "geofence add/remove completed")
        }
    }

    private companion object {
        const val TAG = "AnenGeofenceTest"
        const val TEST_REMINDER_ID = 990001L
    }
}
