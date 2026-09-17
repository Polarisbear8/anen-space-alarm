package com.anen.spacealarm.model

/**
 * 坐标系统。内部统一使用 WGS84；转换只发生在 CoordinateConverter 中。
 */
enum class CoordinateSystem {
    WGS84,
    GCJ02,
    BD09
}
