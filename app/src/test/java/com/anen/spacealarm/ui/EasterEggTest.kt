package com.anen.spacealarm.ui

import com.anen.spacealarm.coordinate.CoordinateConverter
import com.anen.spacealarm.location.DistanceCalculator
import com.anen.spacealarm.model.AlertMode
import com.anen.spacealarm.model.CoordinateSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EasterEggTest {

    /** 高德分享短链返回的原始 GCJ02 坐标，用于校验只换算了一次。 */
    private val gcj02Latitude = 25.252691928063495
    private val gcj02Longitude = 110.20602464675902

    @Test
    fun `easter egg reminder is a disabled one shot alarm with 100m radius`() {
        val reminder = EasterEgg.reminder("hello")

        assertEquals(EasterEgg.PLACE_NAME, reminder.placeName)
        assertEquals(EasterEgg.ADDRESS, reminder.address)
        assertEquals(100f, reminder.radiusMeters, 0f)
        assertEquals("hello", reminder.message)
        assertEquals(AlertMode.ALARM, reminder.alertMode)
        assertFalse("must be one-shot", reminder.repeat)
        assertFalse("must be saved as disabled", reminder.enabled)
        assertFalse(reminder.triggered)
    }

    @Test
    fun `gcj02 poi is converted to wgs84 exactly once`() {
        val reminder = EasterEgg.reminder("hello")

        val shift = DistanceCalculator.calculateDistanceMeters(
            gcj02Latitude, gcj02Longitude, reminder.latitude, reminder.longitude
        )
        assertTrue("gcj02 -> wgs84 shift looks wrong: $shift", shift in 100.0..900.0)

        // 再换算一次（重复转换）会继续朝同一方向漂移，距离必然变大
        val twice = CoordinateConverter.toWgs84(reminder.latitude, reminder.longitude, CoordinateSystem.GCJ02)
        val shiftTwice = DistanceCalculator.calculateDistanceMeters(
            gcj02Latitude, gcj02Longitude, twice[0], twice[1]
        )
        assertTrue("double conversion detected: $shift -> $shiftTwice", shiftTwice > shift)
    }
}
