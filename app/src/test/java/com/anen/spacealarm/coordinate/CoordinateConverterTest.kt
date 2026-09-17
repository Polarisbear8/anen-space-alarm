package com.anen.spacealarm.coordinate

import com.anen.spacealarm.location.DistanceCalculator
import com.anen.spacealarm.model.CoordinateSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateConverterTest {

    private val guangzhouWgs84 = doubleArrayOf(23.081058, 113.268932)

    @Test
    fun `outside china coordinates are not shifted`() {
        val paris = CoordinateConverter.wgs84ToGcj02(48.8566, 2.3522)
        assertEquals(48.8566, paris[0], 1e-9)
        assertEquals(2.3522, paris[1], 1e-9)

        val parisBack = CoordinateConverter.gcj02ToWgs84(48.8566, 2.3522)
        assertEquals(48.8566, parisBack[0], 1e-9)
        assertEquals(2.3522, parisBack[1], 1e-9)
    }

    @Test
    fun `wgs84 to gcj02 shifts by a few hundred meters in china`() {
        val gcj = CoordinateConverter.wgs84ToGcj02(guangzhouWgs84[0], guangzhouWgs84[1])
        val shift = DistanceCalculator.calculateDistanceMeters(
            guangzhouWgs84[0], guangzhouWgs84[1], gcj[0], gcj[1]
        )
        assertTrue("unexpected shift: $shift", shift in 300.0..900.0)
    }

    @Test
    fun `round trip is accurate to sub meter`() {
        val gcj = CoordinateConverter.wgs84ToGcj02(guangzhouWgs84[0], guangzhouWgs84[1])
        val back = CoordinateConverter.gcj02ToWgs84(gcj[0], gcj[1])
        val error = DistanceCalculator.calculateDistanceMeters(
            guangzhouWgs84[0], guangzhouWgs84[1], back[0], back[1]
        )
        assertTrue("round trip error: $error", error < 1.0)
    }

    @Test
    fun `declared coordinate system decides conversion`() {
        val fromGcj = CoordinateConverter.toWgs84(23.081058, 113.268932, CoordinateSystem.GCJ02)
        val fromWgs = CoordinateConverter.toWgs84(23.081058, 113.268932, CoordinateSystem.WGS84)
        assertEquals(23.081058, fromWgs[0], 1e-12)
        assertEquals(113.268932, fromWgs[1], 1e-12)
        val shift = DistanceCalculator.calculateDistanceMeters(23.081058, 113.268932, fromGcj[0], fromGcj[1])
        assertTrue("gcj02 input must be converted, shift=$shift", shift > 100.0)
    }

    @Test
    fun `double conversion would add another visible offset`() {
        val once = CoordinateConverter.toWgs84(23.081058, 113.268932, CoordinateSystem.GCJ02)
        val twice = CoordinateConverter.toWgs84(once[0], once[1], CoordinateSystem.GCJ02)
        val extra = DistanceCalculator.calculateDistanceMeters(once[0], once[1], twice[0], twice[1])
        assertTrue("double conversion must be detectable, extra=$extra", extra > 100.0)
    }

    @Test
    fun `bd09 input is converted back to wgs84`() {
        // 固定向量：广州 WGS84 (23.081058, 113.268932) 对应的 BD09 坐标
        val bdLat = 23.084693
        val bdLon = 113.280713
        val wgs = CoordinateConverter.toWgs84(bdLat, bdLon, CoordinateSystem.BD09)
        val error = DistanceCalculator.calculateDistanceMeters(
            guangzhouWgs84[0], guangzhouWgs84[1], wgs[0], wgs[1]
        )
        assertTrue("bd09 转换误差应小于 5 米，实际 $error", error < 5.0)
    }
}
