package com.anen.spacealarm.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceCalculatorTest {

    @Test
    fun `same coordinate is zero meters`() {
        val distance = DistanceCalculator.calculateDistanceMeters(23.081058, 113.268932, 23.081058, 113.268932)
        assertEquals(0.0, distance, 1e-6)
    }

    @Test
    fun `one degree of latitude is about 111 km`() {
        val distance = DistanceCalculator.calculateDistanceMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue("unexpected: $distance", distance in 111_000.0..111_300.0)
    }

    @Test
    fun `guangzhou to shenzhen is about 100 km`() {
        val distance = DistanceCalculator.calculateDistanceMeters(23.1066, 113.3245, 22.5460, 114.0546)
        assertTrue("unexpected: $distance", distance in 90_000.0..105_000.0)
    }

    @Test
    fun `offset point keeps requested distance`() {
        val moved = DistanceCalculator.offsetPoint(23.081058, 113.268932, 90.0, 1_000.0)
        val back = DistanceCalculator.calculateDistanceMeters(23.081058, 113.268932, moved[0], moved[1])
        assertEquals(1_000.0, back, 1.0)
        assertTrue("east bearing should increase longitude", moved[1] > 113.268932)
    }

    @Test
    fun `circle polygon has expected segment count and radius`() {
        val polygon = DistanceCalculator.circlePolygon(23.081058, 113.268932, 500.0, 64)
        assertEquals(64, polygon.size)
        polygon.forEach { point ->
            val distance = DistanceCalculator.calculateDistanceMeters(23.081058, 113.268932, point[0], point[1])
            assertEquals(500.0, distance, 1.0)
        }
    }

    @Test
    fun `format distance matches ui rules`() {
        assertEquals("1.24 KM", DistanceCalculator.formatDistance(1_240.0))
        assertEquals("1.00 KM", DistanceCalculator.formatDistance(1_000.0))
        assertEquals("500 M", DistanceCalculator.formatDistance(500.0))
        assertEquals("--", DistanceCalculator.formatDistance(Double.NaN))
    }
}
