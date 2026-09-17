package com.anen.spacealarm

import com.anen.spacealarm.location.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 创建提醒前的前置检查：
 *  - 已在目标范围内 → 拒绝（不会再有"进入"事件）
 *  - 取不到定位     → 拒绝（绝不能当成"不在范围内"）
 */
class ArmPreflightTest {

    private val targetLat = 23.081058
    private val targetLon = 113.268932

    /** 纬度 1 度约 111194.9 米（与本项目 Haversine 半径一致）。 */
    private fun metersNorth(meters: Double) = targetLat + meters / 111_194.9

    @Test
    fun `user already inside the radius is blocked`() {
        val inside = LocationFix(latitude = metersNorth(180.0), longitude = targetLon)
        val result = AppContainer.preflightArm(inside, targetLat, targetLon, 500f)

        assertEquals(AppContainer.ArmResult.INSIDE_RANGE, result.blockedBy)
        assertTrue("应带上实际距离用于提示", result.distanceMeters!! < 500.0)
    }

    @Test
    fun `user outside the radius can proceed`() {
        val outside = LocationFix(latitude = metersNorth(1_200.0), longitude = targetLon)
        val result = AppContainer.preflightArm(outside, targetLat, targetLon, 500f)

        assertNull(result.blockedBy)
    }

    @Test
    fun `using current location as target is blocked`() {
        val here = LocationFix(latitude = targetLat, longitude = targetLon)
        val result = AppContainer.preflightArm(here, targetLat, targetLon, 100f)

        assertEquals(AppContainer.ArmResult.INSIDE_RANGE, result.blockedBy)
    }

    @Test
    fun `just inside the boundary is blocked and just outside is allowed`() {
        val radius = 200f
        val insideEdge = LocationFix(latitude = metersNorth(radius - 1.0), longitude = targetLon)
        val outsideEdge = LocationFix(latitude = metersNorth(radius + 5.0), longitude = targetLon)

        assertEquals(
            AppContainer.ArmResult.INSIDE_RANGE,
            AppContainer.preflightArm(insideEdge, targetLat, targetLon, radius).blockedBy
        )
        assertNull(AppContainer.preflightArm(outsideEdge, targetLat, targetLon, radius).blockedBy)
    }

    @Test
    fun `missing location is not treated as outside`() {
        val result = AppContainer.preflightArm(null, targetLat, targetLon, 500f)

        assertEquals(AppContainer.ArmResult.LOCATION_UNAVAILABLE, result.blockedBy)
        assertNull(result.distanceMeters)
    }
}
