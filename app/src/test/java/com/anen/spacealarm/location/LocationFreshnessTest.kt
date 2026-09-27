package com.anen.spacealarm.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 持续更新与一次性定位并行时，旧结果不能覆盖新结果。
 */
class LocationFreshnessTest {

    @Test
    fun `first location is always accepted`() {
        assertTrue(isFresher(candidateElapsedRealtimeNanos = 1_000L, currentElapsedRealtimeNanos = null))
    }

    @Test
    fun `newer timestamp is accepted`() {
        assertTrue(isFresher(candidateElapsedRealtimeNanos = 2_000L, currentElapsedRealtimeNanos = 1_000L))
    }

    @Test
    fun `older timestamp is rejected`() {
        assertFalse(isFresher(candidateElapsedRealtimeNanos = 1_000L, currentElapsedRealtimeNanos = 2_000L))
    }

    @Test
    fun `equal timestamp is rejected`() {
        assertFalse(isFresher(candidateElapsedRealtimeNanos = 2_000L, currentElapsedRealtimeNanos = 2_000L))
    }
}
