package com.anen.spacealarm.amap

import com.anen.spacealarm.model.CoordinateSystem
import com.anen.spacealarm.model.Place
import com.anen.spacealarm.model.PlaceSource

/** 经度、纬度（始终按 Amap 约定：先经度后纬度）。 */
internal data class LngLat(val longitude: Double, val latitude: Double)

/** Resolver 内部的中间结果：坐标 + 原始坐标系 + 来源。 */
internal data class AMapLocation(
    val name: String?,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val coordinateSystem: CoordinateSystem,
    val source: PlaceSource,
    val poiId: String?,
    val via: String
) {
    fun toPlace(fallbackName: String): Place = Place.of(
        name = name?.takeIf { it.isNotBlank() } ?: fallbackName,
        address = address?.takeIf { it.isNotBlank() },
        latitude = latitude,
        longitude = longitude,
        coordinateSystem = coordinateSystem,
        source = source
    )
}

/** 解析失败原因（界面负责映射成用户可读文案）。 */
enum class FailureReason {
    EMPTY_PAYLOAD,
    NO_AMAP_LINK,
    POI_ID_ONLY,
    FETCH_FAILED
}

/** 解析最终结果。 */
sealed interface ResolveResult {

    data class Success(
        val place: Place,
        val via: String,
        /** 分级 fallback 的调试报告，供开发者模式查看 / 复制 */
        val report: String
    ) : ResolveResult

    data class Failure(
        val reason: FailureReason,
        /** 分级 fallback 的调试报告 */
        val report: String
    ) : ResolveResult
}
