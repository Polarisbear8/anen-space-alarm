package com.anen.spacealarm.permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 围栏就绪规则：
 *  - 必须精确定位（Geofencing API 要求 ACCESS_FINE_LOCATION）；
 *  - Android 10 (API 29) 起还必须有后台定位。
 * 读取当前位置只需要前台精确定位，与后台定位解耦。
 */
class PermissionManagerTest {

    private fun state(
        fine: Boolean,
        coarse: Boolean = fine,
        background: Boolean = true,
        sdkInt: Int
    ) = PermissionManager.State(
        fineLocation = fine,
        coarseLocation = coarse,
        backgroundLocation = background,
        notifications = true,
        fullScreenIntent = true,
        sdkInt = sdkInt
    )

    @Test
    fun `android 9 and below only need fine location`() {
        assertTrue(state(fine = true, background = false, sdkInt = 28).geofenceReady)
    }

    @Test
    fun `android 10 and above require background location`() {
        assertFalse(state(fine = true, background = false, sdkInt = 29).geofenceReady)
        assertFalse(state(fine = true, background = false, sdkInt = 34).geofenceReady)
        assertTrue(state(fine = true, background = true, sdkInt = 34).geofenceReady)
    }

    @Test
    fun `coarse only is not enough for geofence`() {
        val coarseOnly = state(fine = false, coarse = true, background = true, sdkInt = 34)
        assertFalse("只有大致位置时围栏不可用", coarseOnly.geofenceReady)
    }

    @Test
    fun `no location permission is never ready`() {
        assertFalse(state(fine = false, coarse = false, background = true, sdkInt = 34).geofenceReady)
        assertFalse(state(fine = false, coarse = false, background = false, sdkInt = 28).geofenceReady)
    }
}
