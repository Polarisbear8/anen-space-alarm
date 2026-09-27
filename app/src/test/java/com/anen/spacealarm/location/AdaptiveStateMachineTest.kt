package com.anen.spacealarm.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveStateMachineTest {

    private val tuning = AdaptiveTuning()

    @Test
    fun `far when distance is large`() {
        assertEquals(
            AdaptiveMode.FAR,
            AdaptiveStateMachine.next(AdaptiveMode.FAR, distanceMeters = 5_000.0, etaSeconds = null, tuning)
        )
    }

    @Test
    fun `approach when within approach range`() {
        assertEquals(
            AdaptiveMode.APPROACH,
            AdaptiveStateMachine.next(AdaptiveMode.FAR, distanceMeters = 1_500.0, etaSeconds = null, tuning)
        )
    }

    @Test
    fun `critical when within critical range`() {
        assertEquals(
            AdaptiveMode.CRITICAL,
            AdaptiveStateMachine.next(AdaptiveMode.APPROACH, distanceMeters = 250.0, etaSeconds = null, tuning)
        )
    }

    @Test
    fun `critical has hysteresis and stays until past exit`() {
        // 350m 已经超过进入阈值(300)，但还没到退出阈值(400)，应保持 CRITICAL。
        assertEquals(
            AdaptiveMode.CRITICAL,
            AdaptiveStateMachine.next(AdaptiveMode.CRITICAL, distanceMeters = 350.0, etaSeconds = null, tuning)
        )
        // 450m 超过退出阈值 -> 回退到 APPROACH。
        assertEquals(
            AdaptiveMode.APPROACH,
            AdaptiveStateMachine.next(AdaptiveMode.CRITICAL, distanceMeters = 450.0, etaSeconds = null, tuning)
        )
    }

    @Test
    fun `approach has hysteresis against boundary flapping`() {
        // 在 2000 进入阈值附近抖动，不应在 FAR/APPROACH 之间来回切。
        var mode = AdaptiveStateMachine.next(AdaptiveMode.FAR, 1_900.0, null, tuning)
        assertEquals(AdaptiveMode.APPROACH, mode)
        mode = AdaptiveStateMachine.next(mode, 2_100.0, null, tuning)
        assertEquals(AdaptiveMode.APPROACH, mode)
        mode = AdaptiveStateMachine.next(mode, 1_900.0, null, tuning)
        assertEquals(AdaptiveMode.APPROACH, mode)
        // 明显远离才回 FAR。
        assertEquals(AdaptiveMode.FAR, AdaptiveStateMachine.next(mode, 2_600.0, null, tuning))
    }

    @Test
    fun `eta promotes to critical even when still far`() {
        // 800m 但 25s 后到达 -> 应提前进入 CRITICAL。
        assertEquals(
            AdaptiveMode.CRITICAL,
            AdaptiveStateMachine.next(AdaptiveMode.FAR, distanceMeters = 800.0, etaSeconds = 25.0, tuning)
        )
    }

    @Test
    fun `eta promotes to approach`() {
        // 3km 外但 200s 后到达 -> 至少进入 APPROACH。
        assertEquals(
            AdaptiveMode.APPROACH,
            AdaptiveStateMachine.next(AdaptiveMode.FAR, distanceMeters = 3_000.0, etaSeconds = 200.0, tuning)
        )
    }

    @Test
    fun `interval and priority follow mode`() {
        assertEquals(tuning.criticalIntervalMillis, AdaptiveStateMachine.intervalMillis(AdaptiveMode.CRITICAL, null, tuning))
        assertEquals(tuning.approachIntervalMillis, AdaptiveStateMachine.intervalMillis(AdaptiveMode.APPROACH, null, tuning))
        assertEquals(tuning.farIntervalMillis, AdaptiveStateMachine.intervalMillis(AdaptiveMode.FAR, null, tuning))
        assertEquals(LocationPriority.HIGH_ACCURACY, AdaptiveStateMachine.priority(AdaptiveMode.CRITICAL, tuning))
    }

    @Test
    fun `eta shrinks interval so fast approach cannot cross the window`() {
        // FAR 基础 60s，但 ETA 45s：安全系数 3 -> 15s，绝不再等 60s。
        assertEquals(15_000L, AdaptiveStateMachine.intervalMillis(AdaptiveMode.FAR, etaSeconds = 45.0, tuning))
        // ETA 更短 -> 更密，但不低于下限。
        assertEquals(
            tuning.minIntervalMillis,
            AdaptiveStateMachine.intervalMillis(AdaptiveMode.FAR, etaSeconds = 3.0, tuning)
        )
        // 未知 / 未接近 -> 保持基础间隔（省电）。
        assertEquals(tuning.farIntervalMillis, AdaptiveStateMachine.intervalMillis(AdaptiveMode.FAR, null, tuning))
        assertEquals(tuning.farIntervalMillis, AdaptiveStateMachine.intervalMillis(AdaptiveMode.FAR, 0.0, tuning))
    }

    @Test
    fun `high closing speed lowers requested interval far out`() {
        val distance = 1_200.0
        val radius = 100.0
        val speed = 25.0 // m/s
        val eta = AdaptiveStateMachine.etaSeconds(distance, radius, speed)!! // (1200-100)/25 = 44s
        val interval = AdaptiveStateMachine.intervalMillis(AdaptiveMode.FAR, eta, tuning)
        assertTrue("interval 应明显小于 FAR 基础 60s，实际=$interval", interval < tuning.farIntervalMillis)
    }

    @Test
    fun `closing speed and eta`() {
        // 20s 内从 1000m 到 800m -> 10 m/s
        val speed = AdaptiveStateMachine.smoothedClosingSpeed(
            previousSpeedMps = 0.0,
            previousDistanceMeters = 1_000.0,
            distanceMeters = 800.0,
            dtSeconds = 20.0,
            smoothing = 1.0
        )
        assertEquals(10.0, speed, 0.001)
        // 半径 100m，剩余 700m -> ETA 70s
        assertEquals(70.0, AdaptiveStateMachine.etaSeconds(800.0, 100.0, speed)!!, 0.001)
        // 静止时无 ETA
        assertNull(AdaptiveStateMachine.etaSeconds(800.0, 100.0, 0.0))
    }

    @Test
    fun `idle and triggered are terminal`() {
        assertEquals(AdaptiveMode.IDLE, AdaptiveStateMachine.next(AdaptiveMode.IDLE, 100.0, null, tuning))
        assertEquals(AdaptiveMode.TRIGGERED, AdaptiveStateMachine.next(AdaptiveMode.TRIGGERED, 100.0, null, tuning))
    }

    @Test
    fun `hysteresis prevents high low high flapping over a simulated approach`() {
        // 从 5000m 单调接近到 50m，模式应单调不降级（FAR -> APPROACH -> CRITICAL）。
        val sequence = listOf(5_000.0, 3_000.0, 2_100.0, 1_500.0, 800.0, 400.0, 200.0, 50.0)
        var mode = AdaptiveMode.FAR
        val seen = mutableListOf(mode)
        for (d in sequence) {
            mode = AdaptiveStateMachine.next(mode, d, null, tuning)
            seen.add(mode)
        }
        assertTrue(seen.contains(AdaptiveMode.APPROACH))
        assertEquals(AdaptiveMode.CRITICAL, seen.last())
    }
}
