package com.anen.spacealarm.location

/**
 * 距离自适应的定位状态机。
 *
 * FAR      ：距离目标很远，低频低功耗
 * APPROACH ：接近中，提高频率
 * CRITICAL ：即将到达，高频高精度
 * TRIGGERED：已触发（服务即将停止）
 * IDLE     ：服务未运行
 *
 * 所有阈值都集中在 [AdaptiveTuning]，便于实测后调整。
 */
enum class AdaptiveMode { IDLE, FAR, APPROACH, CRITICAL, TRIGGERED }

/** 与 Google `Priority.*` 相同的数值，避免纯逻辑层依赖 GMS。 */
object LocationPriority {
    const val HIGH_ACCURACY = 100
    const val BALANCED_POWER_ACCURACY = 102
    const val LOW_POWER = 104
}

data class AdaptiveTuning(
    val farIntervalMillis: Long = 60_000L,
    val approachIntervalMillis: Long = 15_000L,
    val criticalIntervalMillis: Long = 4_000L,
    // 全部使用 HIGH_ACCURACY：省电靠“远距离拉长间隔”，而不是靠降低精度。
    // 实测 BALANCED 在部分环境（模拟器 / 无网络定位）不产生回调，会漏掉位置变化。
    val farPriority: Int = LocationPriority.HIGH_ACCURACY,
    val approachPriority: Int = LocationPriority.HIGH_ACCURACY,
    val criticalPriority: Int = LocationPriority.HIGH_ACCURACY,
    /** 运动触发：未到 interval 但位移超过阈值时也投递（静止时不耗电）。 */
    val farMinUpdateDistanceMeters: Float = 300f,
    val approachMinUpdateDistanceMeters: Float = 100f,
    val criticalMinUpdateDistanceMeters: Float = 0f,
    /** 进入 APPROACH / 退出回 FAR 的距离阈值（滞回）。 */
    val approachEnterMeters: Double = 2_000.0,
    val approachExitMeters: Double = 2_500.0,
    /** 进入 CRITICAL / 退出回 APPROACH 的距离阈值（滞回）。 */
    val criticalEnterMeters: Double = 300.0,
    val criticalExitMeters: Double = 400.0,
    /** 基于 ETA 的提前升频门槛。 */
    val approachEnterEtaSeconds: Double = 240.0,
    val approachExitEtaSeconds: Double = 300.0,
    val criticalEnterEtaSeconds: Double = 30.0,
    val criticalExitEtaSeconds: Double = 60.0,
    val criticalEtaMaxMeters: Double = 1_000.0,
    /** 重复提醒：离开半径多少米后才认为“已离开”，避免半径内反复触发。 */
    val insideExitMarginMeters: Double = 50.0,
    val insideExitRadiusFraction: Double = 0.2,
    val speedSmoothing: Double = 0.4,
    val minSampleSeconds: Double = 3.0,
    /** ETA 安全系数：目标是到达半径前至少再采样这么多次，避免高速跨圈。 */
    val etaSafetyFactor: Double = 3.0,
    val minIntervalMillis: Long = 3_000L,
    val maxIntervalMillis: Long = 60_000L
)

object AdaptiveStateMachine {

    /**
     * 计算下一个状态。distance/eta 越大或越不接近时降频，反之升频。
     * 使用显式滞回，避免在阈值附近 HIGH/LOW 抖动。
     */
    fun next(
        current: AdaptiveMode,
        distanceMeters: Double,
        etaSeconds: Double?,
        tuning: AdaptiveTuning = AdaptiveTuning()
    ): AdaptiveMode {
        if (current == AdaptiveMode.IDLE || current == AdaptiveMode.TRIGGERED) return current

        val criticalEnter = distanceMeters <= tuning.criticalEnterMeters ||
            (etaSeconds != null && etaSeconds <= tuning.criticalEnterEtaSeconds &&
                distanceMeters <= tuning.criticalEtaMaxMeters)
        val criticalStay = distanceMeters < tuning.criticalExitMeters ||
            (etaSeconds != null && etaSeconds < tuning.criticalExitEtaSeconds)
        val approachEnter = distanceMeters <= tuning.approachEnterMeters ||
            (etaSeconds != null && etaSeconds <= tuning.approachEnterEtaSeconds)
        val approachStay = distanceMeters < tuning.approachExitMeters ||
            (etaSeconds != null && etaSeconds < tuning.approachExitEtaSeconds)

        return when (current) {
            AdaptiveMode.FAR -> when {
                criticalEnter -> AdaptiveMode.CRITICAL
                approachEnter -> AdaptiveMode.APPROACH
                else -> AdaptiveMode.FAR
            }
            AdaptiveMode.APPROACH -> when {
                criticalEnter -> AdaptiveMode.CRITICAL
                approachStay -> AdaptiveMode.APPROACH
                else -> AdaptiveMode.FAR
            }
            AdaptiveMode.CRITICAL -> when {
                criticalStay -> AdaptiveMode.CRITICAL
                approachStay -> AdaptiveMode.APPROACH
                else -> AdaptiveMode.FAR
            }
            else -> current
        }
    }

    /**
     * 自适应 interval：以模式的基础间隔为上限，再用 ETA（预计到达提醒半径的秒数）收紧，
     * 保证到达半径前至少还有 [AdaptiveTuning.etaSafetyFactor] 次采样。
     * ETA 未知或未接近时返回基础间隔（保持低功耗）。
     */
    fun intervalMillis(
        mode: AdaptiveMode,
        etaSeconds: Double? = null,
        tuning: AdaptiveTuning = AdaptiveTuning()
    ): Long {
        val base = when (mode) {
            AdaptiveMode.CRITICAL -> tuning.criticalIntervalMillis
            AdaptiveMode.APPROACH -> tuning.approachIntervalMillis
            else -> tuning.farIntervalMillis
        }
        val capped = if (etaSeconds != null && etaSeconds > 0.0) {
            (etaSeconds * 1000.0 / tuning.etaSafetyFactor).toLong()
        } else {
            base
        }
        return capped.coerceIn(tuning.minIntervalMillis, base)
    }

    fun priority(mode: AdaptiveMode, tuning: AdaptiveTuning = AdaptiveTuning()): Int = when (mode) {
        AdaptiveMode.CRITICAL -> tuning.criticalPriority
        AdaptiveMode.APPROACH -> tuning.approachPriority
        else -> tuning.farPriority
    }

    fun minUpdateDistanceMeters(mode: AdaptiveMode, tuning: AdaptiveTuning = AdaptiveTuning()): Float = when (mode) {
        AdaptiveMode.CRITICAL -> tuning.criticalMinUpdateDistanceMeters
        AdaptiveMode.APPROACH -> tuning.approachMinUpdateDistanceMeters
        else -> tuning.farMinUpdateDistanceMeters
    }

    /** 指数平滑的接近速度（m/s，正数表示靠近）。 */
    fun smoothedClosingSpeed(
        previousSpeedMps: Double,
        previousDistanceMeters: Double,
        distanceMeters: Double,
        dtSeconds: Double,
        smoothing: Double
    ): Double {
        if (dtSeconds <= 0.0) return previousSpeedMps
        val instant = (previousDistanceMeters - distanceMeters) / dtSeconds
        return previousSpeedMps + smoothing * (instant - previousSpeedMps)
    }

    /** 预计到达提醒半径的秒数；未在靠近时返回 null。 */
    fun etaSeconds(distanceMeters: Double, radiusMeters: Double, closingSpeedMps: Double): Double? {
        if (closingSpeedMps <= 0.1) return null
        val remaining = distanceMeters - radiusMeters
        if (remaining <= 0.0) return 0.0
        return remaining / closingSpeedMps
    }
}
