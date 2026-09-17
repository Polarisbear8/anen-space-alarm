package com.anen.spacealarm.amap

import com.anen.spacealarm.model.CoordinateSystem
import com.anen.spacealarm.model.PlaceSource

/**
 * 分享内容里直接带坐标的情况（例如 "113.268932,23.081058"）。
 * 仅在明确的高德上下文里使用，避免把普通文本中的数字对误判为坐标。
 */
internal object DirectCoordinate {

    private val strictPair = Regex("""(-?\d{1,3}\.\d{3,})\s*[,，]\s*(-?\d{1,3}\.\d{3,})""")

    fun extract(text: String?): AMapLocation? {
        if (text.isNullOrBlank()) return null
        val match = strictPair.find(text) ?: return null
        val a = match.groupValues[1].toDoubleOrNull() ?: return null
        val b = match.groupValues[2].toDoubleOrNull() ?: return null
        val lngLat = UrlQuery.normalizeLngLat(a, b) ?: return null
        return AMapLocation(
            name = null,
            address = null,
            latitude = lngLat.latitude,
            longitude = lngLat.longitude,
            coordinateSystem = CoordinateSystem.GCJ02,
            source = PlaceSource.AMAP_SHARE_TEXT,
            poiId = null,
            via = "direct-coordinate"
        )
    }
}
